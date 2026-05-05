package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
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
                planDocumentRepository, new ObjectMapper());

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
                planDocumentRepository, new ObjectMapper());

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
                planDocumentRepository, new ObjectMapper());

        PlanDocument plan = newPlan();
        plan.setClasses(null);

        String msg = agent.buildUserMessage(plan, Map.of(
                "pom.xml", "<project/>",
                "src/main/resources/plugin.yml", "name: Test"));

        assertThat(msg).doesNotContain("Class Contracts");
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
