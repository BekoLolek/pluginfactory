package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.plan.PlanDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateServiceTest {

    private TemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new TemplateService();
    }

    @Test
    void renderTemplate_validPom() {
        PlanDocument plan = createTestPlan();

        Map<String, String> files = templateService.renderTemplate(plan);

        String pom = files.get("pom.xml");
        assertThat(pom).isNotNull();
        assertThat(pom).contains("<artifactId>test-plugin</artifactId>");
        assertThat(pom).contains("<version>1.0.0</version>");
        assertThat(pom).contains("1.20.4-R0.1-SNAPSHOT");
    }

    @Test
    void renderTemplate_validPluginYml() {
        PlanDocument plan = createTestPlan();

        Map<String, String> files = templateService.renderTemplate(plan);

        String pluginYml = files.get("src/main/resources/plugin.yml");
        assertThat(pluginYml).isNotNull();
        assertThat(pluginYml).contains("name: Test Plugin");
        assertThat(pluginYml).contains("main: com.bekololek.generated.TestPlugin");
        assertThat(pluginYml).contains("api-version: '1.20'");
    }

    @Test
    void renderTemplate_validMainClass() {
        PlanDocument plan = createTestPlan();

        Map<String, String> files = templateService.renderTemplate(plan);

        String mainClassKey = "src/main/java/com/bekololek/generated/TestPlugin.java";
        assertThat(files).containsKey(mainClassKey);

        String mainClass = files.get(mainClassKey);
        assertThat(mainClass).contains("class TestPlugin extends JavaPlugin");
        assertThat(mainClass).contains("package com.bekololek.generated;");
        assertThat(mainClass).contains("onEnable()");
        assertThat(mainClass).contains("onDisable()");
    }

    @Test
    void renderTemplate_withCommands() {
        PlanDocument plan = createTestPlan();
        plan.setCommands("[{\"name\":\"heal\",\"description\":\"Heal a player\",\"usage\":\"/heal [player]\"}]");

        Map<String, String> files = templateService.renderTemplate(plan);

        String pluginYml = files.get("src/main/resources/plugin.yml");
        assertThat(pluginYml).contains("heal:");
        assertThat(pluginYml).contains("description: \"Heal a player\"");
    }

    @Test
    void toClassName_normalName() {
        assertThat(templateService.toClassName("Test Plugin")).isEqualTo("TestPlugin");
    }

    @Test
    void toClassName_specialChars() {
        assertThat(templateService.toClassName("my-awesome plugin!")).isEqualTo("MyAwesomePlugin");
    }

    @Test
    void toClassName_null() {
        assertThat(templateService.toClassName(null)).isEqualTo("GeneratedPlugin");
    }

    @Test
    void toClassName_blank() {
        assertThat(templateService.toClassName("  ")).isEqualTo("GeneratedPlugin");
    }

    @Test
    void toClassName_startsWithDigit() {
        assertThat(templateService.toClassName("123Plugin")).startsWith("Plugin");
    }

    @Test
    void toArtifactId_normalName() {
        assertThat(templateService.toArtifactId("Test Plugin")).isEqualTo("test-plugin");
    }

    @Test
    void toArtifactId_null() {
        assertThat(templateService.toArtifactId(null)).isEqualTo("generated-plugin");
    }

    @Test
    void renderTemplate_threeFiles() {
        PlanDocument plan = createTestPlan();

        Map<String, String> files = templateService.renderTemplate(plan);

        assertThat(files).hasSize(3);
        assertThat(files).containsKey("pom.xml");
        assertThat(files).containsKey("src/main/resources/plugin.yml");
        assertThat(files.keySet().stream().filter(k -> k.endsWith(".java")).count()).isEqualTo(1);
    }

    @Test
    void renderTemplate_withVaultDependency_injectsVaultInPom() {
        PlanDocument plan = createTestPlan();
        plan.setDependencies("[\"Vault\"]");

        Map<String, String> files = templateService.renderTemplate(plan);

        String pom = files.get("pom.xml");
        assertThat(pom).contains("jitpack.io");
        assertThat(pom).contains("com.github.MilkBowl");
        assertThat(pom).contains("VaultAPI");
        assertThat(pom).contains("<scope>provided</scope>");
    }

    @Test
    void renderTemplate_withoutVaultDependency_omitsVaultFromPom() {
        PlanDocument plan = createTestPlan();
        plan.setDependencies("[\"PlaceholderAPI\"]");

        Map<String, String> files = templateService.renderTemplate(plan);

        String pom = files.get("pom.xml");
        assertThat(pom).doesNotContain("VaultAPI");
        assertThat(pom).doesNotContain("jitpack.io");
    }

    @Test
    void renderTemplate_withVaultDependency_noUnresolvedPlaceholders() {
        PlanDocument plan = createTestPlan();
        plan.setDependencies("[\"Vault\"]");

        Map<String, String> files = templateService.renderTemplate(plan);

        String pom = files.get("pom.xml");
        assertThat(pom).doesNotContain("{{");
        assertThat(pom).doesNotContain("}}");
    }

    @Test
    void renderTemplate_descriptionWithQuotes_producesValidYaml() {
        PlanDocument plan = createTestPlan();
        // The exact shape that broke a real build: embedded quotes + angle
        // brackets in the description made plugin.yml invalid YAML, so the
        // plugin never loaded and its listeners never registered.
        plan.setDescription("chat plugin that responds with \"Hello <player>\" when any player says hello");

        Map<String, String> files = templateService.renderTemplate(plan);
        String pluginYml = files.get("src/main/resources/plugin.yml");

        // Must parse cleanly as YAML (no ParserException) and round-trip the
        // description with its quotes intact.
        Object parsed = new org.yaml.snakeyaml.Yaml().load(pluginYml);
        assertThat(parsed).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> root = (Map<String, Object>) parsed;
        assertThat((String) root.get("description")).contains("\"Hello <player>\"");
        assertThat((String) root.get("name")).isEqualTo("Test Plugin");
    }

    @Test
    void renderTemplate_commandDescriptionWithQuotes_producesValidYaml() {
        PlanDocument plan = createTestPlan();
        plan.setCommands("[{\"name\":\"say\",\"description\":\"echo a \\\"quoted\\\" phrase: now\",\"usage\":\"/say <msg>\"}]");

        Map<String, String> files = templateService.renderTemplate(plan);
        String pluginYml = files.get("src/main/resources/plugin.yml");

        // Parsing must not throw — the command description's quotes and colon
        // are escaped, not left to break the block mapping.
        Object parsed = new org.yaml.snakeyaml.Yaml().load(pluginYml);
        assertThat(parsed).isInstanceOf(Map.class);
    }

    @Test
    void yamlDq_escapesQuotesAndBackslashes() {
        assertThat(TemplateService.yamlDq("a \"b\" c")).isEqualTo("a \\\"b\\\" c");
        assertThat(TemplateService.yamlDq("path\\to")).isEqualTo("path\\\\to");
        assertThat(TemplateService.yamlDq("line1\nline2")).isEqualTo("line1 line2");
        assertThat(TemplateService.yamlDq(null)).isEqualTo("");
    }

    private PlanDocument createTestPlan() {
        PlanDocument plan = new PlanDocument();
        plan.setPluginName("Test Plugin");
        plan.setDescription("A test plugin for unit testing");
        plan.setMinecraftVersion("1.20.4");
        plan.setServerType("PAPER");
        plan.setCommands("[]");
        plan.setEventListeners("[]");
        plan.setConfigSchema("[]");
        plan.setDependencies("[]");
        plan.setEstimatedLoc(100);
        return plan;
    }
}
