package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.plan.PlanDocument;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class TemplateService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEMPLATE_BASE = "agents/templates/plugin-template/";

    /** Strict major.minor.patch — only versions matching this can be a real
     *  paper-api Maven artifact. Anything else (wildcards like "1.21.x",
     *  major-only like "1.21", garbage from the LLM) gets swapped to
     *  {@link #DEFAULT_MINECRAFT_VERSION} below. */
    private static final java.util.regex.Pattern STRICT_MC_VERSION =
            java.util.regex.Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private static final String DEFAULT_MINECRAFT_VERSION = "1.21.4";

    public Map<String, String> renderTemplate(PlanDocument plan) {
        Map<String, String> files = new LinkedHashMap<>();

        String mainClassName = toClassName(plan.getPluginName());
        String artifactId = toArtifactId(plan.getPluginName());
        String version = "1.0.0";
        String minecraftVersion = sanitizeMinecraftVersion(plan.getMinecraftVersion());
        String apiVersion = extractApiVersion(minecraftVersion);
        String javaVersion = javaVersionFor(minecraftVersion);
        String description = plan.getDescription() != null ? plan.getDescription() : "";

        // Render pom.xml
        String pomTemplate = loadTemplateFile("pom.xml");
        String pom = pomTemplate
                .replace("{{artifactId}}", artifactId)
                .replace("{{version}}", version)
                .replace("{{minecraftVersion}}", minecraftVersion)
                .replace("{{javaVersion}}", javaVersion);
        files.put("pom.xml", pom);

        // Render plugin.yml
        String pluginYmlTemplate = loadTemplateFile("src/main/resources/plugin.yml");
        String commandsYaml = generateCommandsYaml(plan.getCommands());
        String permissionsYaml = generatePermissionsYaml(plan.getCommands());
        String pluginYml = pluginYmlTemplate
                .replace("{{pluginName}}", plan.getPluginName() != null ? plan.getPluginName() : "GeneratedPlugin")
                .replace("{{version}}", version)
                .replace("{{mainClassName}}", mainClassName)
                .replace("{{apiVersion}}", apiVersion)
                .replace("{{description}}", description)
                .replace("{{commandsYaml}}", commandsYaml)
                .replace("{{permissionsYaml}}", permissionsYaml);
        files.put("src/main/resources/plugin.yml", pluginYml);

        // Generate main class
        String mainClass = generateMainClass(mainClassName, plan);
        files.put("src/main/java/com/bekololek/generated/" + mainClassName + ".java", mainClass);

        return files;
    }

    String toClassName(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return "GeneratedPlugin";
        }
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (char c : pluginName.toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                sb.append(capitalizeNext ? Character.toUpperCase(c) : c);
                capitalizeNext = false;
            } else {
                capitalizeNext = true;
            }
        }
        String result = sb.toString();
        if (result.isEmpty()) {
            return "GeneratedPlugin";
        }
        // Ensure starts with a letter
        if (Character.isDigit(result.charAt(0))) {
            result = "Plugin" + result;
        }
        return result;
    }

    String toArtifactId(String pluginName) {
        if (pluginName == null || pluginName.isBlank()) {
            return "generated-plugin";
        }
        return pluginName.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private String extractApiVersion(String minecraftVersion) {
        // Extract major.minor from version like "1.20.4" -> "1.20"
        String[] parts = minecraftVersion.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return minecraftVersion;
    }

    /**
     * Maps a Paper minecraft version to the Java release the build must
     * use. Paper requirements:
     * <ul>
     *   <li>1.20.5 and 1.21.x ship Java 21 bytecode in paper-api and
     *       require Java 21 to load on the server</li>
     *   <li>1.18 - 1.20.4 ship Java 17 bytecode</li>
     * </ul>
     * Compiling against a newer paper-api with an older javac fails
     * with "class file has wrong version 65.0, should be 61.0", so the
     * build container must have Java 21 (it does as of the Java-21
     * Dockerfile bump) and the pom must request the right release per
     * target so the resulting jar actually loads on the user's server.
     */
    String javaVersionFor(String mcVersion) {
        try {
            String[] parts = mcVersion.split("\\.");
            int minor = Integer.parseInt(parts[1]);
            int patch = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
            if (minor >= 21) return "21";
            if (minor == 20 && patch >= 5) return "21";
            return "17";
        } catch (Exception e) {
            // sanitizeMinecraftVersion guarantees the strict pattern, so
            // this branch is unreachable in practice. Default to 21
            // because that's the safer modern choice.
            log.warn("Could not parse minecraftVersion '{}', defaulting Java to 21", mcVersion);
            return "21";
        }
    }

    /**
     * Templated verbatim into pom.xml as the paper-api Maven coordinate.
     * The plan agent occasionally produces non-resolvable versions like
     * "1.21.x", "latest", or just a major like "1.21" — which kill the
     * Maven build before any Java compiles. Replace anything that isn't
     * an exact major.minor.patch with a known-good default and warn so
     * the rate of fallbacks is visible in logs.
     */
    String sanitizeMinecraftVersion(String raw) {
        if (raw != null && STRICT_MC_VERSION.matcher(raw).matches()) {
            return raw;
        }
        log.warn("Plan stored unresolvable minecraftVersion '{}' — falling back to {}",
                raw, DEFAULT_MINECRAFT_VERSION);
        return DEFAULT_MINECRAFT_VERSION;
    }

    private String generateCommandsYaml(String commandsJson) {
        try {
            List<Map<String, Object>> commands = MAPPER.readValue(
                    commandsJson, new TypeReference<List<Map<String, Object>>>() {});
            if (commands.isEmpty()) {
                return "  {}";
            }
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> cmd : commands) {
                String name = (String) cmd.getOrDefault("name", "command");
                String desc = (String) cmd.getOrDefault("description", "A command");
                String usage = (String) cmd.getOrDefault("usage", "/" + name);
                sb.append("  ").append(name).append(":\n");
                sb.append("    description: \"").append(desc).append("\"\n");
                sb.append("    usage: \"").append(usage).append("\"\n");
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            log.warn("Failed to parse commands JSON: {}", e.getMessage());
            return "  {}";
        }
    }

    private String generatePermissionsYaml(String commandsJson) {
        try {
            List<Map<String, Object>> commands = MAPPER.readValue(
                    commandsJson, new TypeReference<List<Map<String, Object>>>() {});
            if (commands.isEmpty()) {
                return "  {}";
            }
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> cmd : commands) {
                String name = (String) cmd.getOrDefault("name", "command");
                String permission = (String) cmd.getOrDefault("permission", "plugin." + name);
                sb.append("  ").append(permission).append(":\n");
                sb.append("    description: \"Allows use of /").append(name).append("\"\n");
                sb.append("    default: op\n");
            }
            return sb.toString().stripTrailing();
        } catch (Exception e) {
            log.warn("Failed to parse commands for permissions: {}", e.getMessage());
            return "  {}";
        }
    }

    private String generateMainClass(String className, PlanDocument plan) {
        String pluginName = plan.getPluginName() != null ? plan.getPluginName() : "GeneratedPlugin";
        return """
                package com.bekololek.generated;

                import org.bukkit.plugin.java.JavaPlugin;

                public class %s extends JavaPlugin {

                    @Override
                    public void onEnable() {
                        getLogger().info("%s has been enabled!");
                    }

                    @Override
                    public void onDisable() {
                        getLogger().info("%s has been disabled!");
                    }
                }
                """.formatted(className, pluginName, pluginName);
    }

    private String loadTemplateFile(String relativePath) {
        try {
            // First try classpath
            ClassPathResource resource = new ClassPathResource(TEMPLATE_BASE + relativePath);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        } catch (IOException e) {
            log.debug("Template not found on classpath: {}", relativePath);
        }

        // Fallback: try filesystem relative to project root
        try {
            java.nio.file.Path path = java.nio.file.Path.of(TEMPLATE_BASE + relativePath);
            if (java.nio.file.Files.exists(path)) {
                return java.nio.file.Files.readString(path);
            }
            // Try going up from api/ to project root
            path = java.nio.file.Path.of("../" + TEMPLATE_BASE + relativePath);
            if (java.nio.file.Files.exists(path)) {
                return java.nio.file.Files.readString(path);
            }
        } catch (IOException e) {
            log.debug("Template not found on filesystem: {}", relativePath);
        }

        // Return a default if not found
        log.warn("Template file not found, using embedded default: {}", relativePath);
        return getDefaultTemplate(relativePath);
    }

    private String getDefaultTemplate(String relativePath) {
        if (relativePath.equals("pom.xml")) {
            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <project xmlns="http://maven.apache.org/POM/4.0.0"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                        <modelVersion>4.0.0</modelVersion>
                        <groupId>com.bekololek.generated</groupId>
                        <artifactId>{{artifactId}}</artifactId>
                        <version>{{version}}</version>
                        <packaging>jar</packaging>
                        <properties>
                            <java.version>{{javaVersion}}</java.version>
                            <maven.compiler.release>{{javaVersion}}</maven.compiler.release>
                            <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
                        </properties>
                        <repositories>
                            <repository>
                                <id>papermc</id>
                                <url>https://repo.papermc.io/repository/maven-public/</url>
                            </repository>
                        </repositories>
                        <dependencies>
                            <dependency>
                                <groupId>io.papermc.paper</groupId>
                                <artifactId>paper-api</artifactId>
                                <version>{{minecraftVersion}}-R0.1-SNAPSHOT</version>
                                <scope>provided</scope>
                            </dependency>
                        </dependencies>
                        <build>
                            <plugins>
                                <plugin>
                                    <groupId>org.apache.maven.plugins</groupId>
                                    <artifactId>maven-compiler-plugin</artifactId>
                                    <version>3.11.0</version>
                                    <configuration>
                                        <release>{{javaVersion}}</release>
                                    </configuration>
                                </plugin>
                                <plugin>
                                    <groupId>org.apache.maven.plugins</groupId>
                                    <artifactId>maven-shade-plugin</artifactId>
                                    <version>3.5.1</version>
                                    <executions>
                                        <execution>
                                            <phase>package</phase>
                                            <goals><goal>shade</goal></goals>
                                        </execution>
                                    </executions>
                                </plugin>
                            </plugins>
                        </build>
                    </project>
                    """;
        } else if (relativePath.contains("plugin.yml")) {
            return """
                    name: {{pluginName}}
                    version: {{version}}
                    main: com.bekololek.generated.{{mainClassName}}
                    api-version: '{{apiVersion}}'
                    description: "{{description}} | Generated by BekoLolek Plugin Factory"
                    commands:
                    {{commandsYaml}}
                    permissions:
                    {{permissionsYaml}}
                    """;
        }
        return "";
    }
}
