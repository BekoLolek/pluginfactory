package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ImplementerAgentTest {

    @Mock
    private AnthropicClient anthropicClient;

    @Mock
    private ModelRouter modelRouter;

    @Mock
    private TemplateService templateService;

    @Mock
    private PlanDocumentRepository planDocumentRepository;

    @Test
    void buildUserMessage_withClassContracts_includesLockedSection() {
        // Class contracts are the whole point of plan-stage type locking:
        // when the plan declares them, the implementer prompt must hand them
        // to the model as ground truth so cross-file constructor signatures
        // and extends/implements relations stay consistent.
        ImplementerAgent agent = new ImplementerAgent(
                anthropicClient, modelRouter, templateService,
                planDocumentRepository, new ObjectMapper(), new PatternLibraryService());

        PlanDocument plan = newPlan();
        plan.setClasses("""
                [
                  {"name":"Game","constructorParams":["TicTacToe plugin","Player owner"]},
                  {"name":"GameSession","extends":"Game",
                   "constructorParams":["TicTacToe plugin","Player owner"]},
                  {"name":"GameBoardGUI","implements":["Listener"],
                   "constructorParams":["TicTacToe plugin","Game game","Player owner","char symbol"]}
                ]
                """);

        String msg = agent.buildUserMessage(plan, Map.of(
                "pom.xml", "<project/>",
                "src/main/resources/plugin.yml", "name: Test"));

        assertThat(msg)
                .contains("Class Contracts (LOCKED")
                .contains("must match")
                .contains("\"name\":\"Game\"")
                .contains("\"extends\":\"Game\"")
                .contains("char symbol");
    }

    @Test
    void buildUserMessage_withEmptyClasses_skipsContractSection() {
        // Trivial plugins (single class) shouldn't get a "LOCKED" header
        // pointing at an empty array — the section just adds noise.
        ImplementerAgent agent = new ImplementerAgent(
                anthropicClient, modelRouter, templateService,
                planDocumentRepository, new ObjectMapper(), new PatternLibraryService());

        PlanDocument plan = newPlan();
        plan.setClasses("[]");

        String msg = agent.buildUserMessage(plan, Map.of(
                "pom.xml", "<project/>",
                "src/main/resources/plugin.yml", "name: Test"));

        assertThat(msg).doesNotContain("Class Contracts");
    }

    @Test
    void buildUserMessage_withNullClasses_skipsContractSection() {
        ImplementerAgent agent = new ImplementerAgent(
                anthropicClient, modelRouter, templateService,
                planDocumentRepository, new ObjectMapper(), new PatternLibraryService());

        PlanDocument plan = newPlan();
        plan.setClasses(null);

        String msg = agent.buildUserMessage(plan, Map.of(
                "pom.xml", "<project/>",
                "src/main/resources/plugin.yml", "name: Test"));

        assertThat(msg).doesNotContain("Class Contracts");
    }

    @Test
    void buildUserMessage_withRepairContext_includesFailureAndPreviousFiles() {
        ImplementerAgent agent = new ImplementerAgent(
                anthropicClient, modelRouter, templateService,
                planDocumentRepository, new ObjectMapper(), new PatternLibraryService());

        PlanDocument plan = newPlan();
        ImplementerAgent.RepairContext repair = new ImplementerAgent.RepairContext(
                Map.of("src/main/java/com/bekololek/generated/Main.java",
                        "package com.bekololek.generated; class Main {}"),
                "COMPILATION failure:\npackage com.sk89q.worldedit does not exist");

        String msg = agent.buildUserMessage(plan, Map.of(
                "pom.xml", "<project/>",
                "src/main/resources/plugin.yml", "name: Test"), repair);

        assertThat(msg)
                .contains("PREVIOUS ATTEMPT FAILED")
                .contains("com.sk89q.worldedit does not exist")
                .contains("class Main");
    }

    @Test
    void parseFilesArray_readsPathContentPairs() throws Exception {
        ImplementerAgent agent = new ImplementerAgent(
                anthropicClient, modelRouter, templateService,
                planDocumentRepository, new ObjectMapper(), new PatternLibraryService());

        JsonNode input = new ObjectMapper().readTree("""
                {"files":[
                  {"path":"src/main/java/com/bekololek/generated/Main.java","content":"package x;"},
                  {"path":"src/main/resources/config.yml","content":"a: 1"}
                ]}""");

        Map<String, String> files = agent.parseFilesArray(input);

        assertThat(files).hasSize(2)
                .containsEntry("src/main/resources/config.yml", "a: 1");
    }

    @Test
    void parseFilesArray_emptyOrMissing_returnsEmpty() throws Exception {
        ImplementerAgent agent = new ImplementerAgent(
                anthropicClient, modelRouter, templateService,
                planDocumentRepository, new ObjectMapper(), new PatternLibraryService());
        ObjectMapper om = new ObjectMapper();

        assertThat(agent.parseFilesArray(om.readTree("{\"files\":[]}"))).isEmpty();
        assertThat(agent.parseFilesArray(om.readTree("{}"))).isEmpty();
        assertThat(agent.parseFilesArray(null)).isEmpty();
    }

    private PlanDocument newPlan() {
        PlanDocument plan = new PlanDocument();
        plan.setPluginName("TestPlugin");
        plan.setDescription("test");
        plan.setMinecraftVersion("1.21.4");
        plan.setServerType("paper");
        plan.setCommands("[]");
        plan.setEventListeners("[]");
        plan.setConfigSchema("[]");
        plan.setDependencies("[]");
        return plan;
    }
}
