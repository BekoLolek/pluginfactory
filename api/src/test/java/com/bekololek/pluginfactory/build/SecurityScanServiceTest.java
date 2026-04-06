package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityScanServiceTest {

    private SecurityScanService securityScanService;

    @BeforeEach
    void setUp() {
        securityScanService = new SecurityScanService();
    }

    @Test
    void scanSource_cleanCode_passes() {
        String cleanCode = """
                package com.bekololek.generated;

                import org.bukkit.plugin.java.JavaPlugin;

                public class MyPlugin extends JavaPlugin {
                    @Override
                    public void onEnable() {
                        getLogger().info("Enabled!");
                    }

                    @Override
                    public void onDisable() {
                        getLogger().info("Disabled!");
                    }
                }
                """;

        SecurityScanResult result = securityScanService.scanSource(cleanCode);

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void scanSource_runtimeExec_detected() {
        String code = "Runtime.getRuntime().exec(\"rm -rf /\");";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("Runtime.exec"));
    }

    @Test
    void scanSource_processBuilder_detected() {
        String code = "new ProcessBuilder(\"cmd\", \"/c\", \"dir\").start();";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("ProcessBuilder"));
    }

    @Test
    void scanSource_serverSocket_detected() {
        String code = "new ServerSocket(8080);";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("ServerSocket"));
    }

    @Test
    void scanSource_urlConnection_detected() {
        String code = "URL url = new URL(\"http://evil.com\"); URLConnection conn = url.openConnection();";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("URLConnection"));
    }

    @Test
    void scanSource_sunMiscUnsafe_detected() {
        String code = "sun.misc.Unsafe unsafe = getUnsafe();";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("sun.misc.Unsafe"));
    }

    @Test
    void scanSource_nms_detected() {
        String code = "import net.minecraft.server.v1_20_R1.EntityPlayer;";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("NMS"));
    }

    @Test
    void scanSource_classForName_detected() {
        String code = "Class<?> clazz = Class.forName(\"com.evil.Exploit\");";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("Class.forName"));
    }

    @Test
    void scanSource_systemExit_detected() {
        String code = "System.exit(0);";

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).anyMatch(v -> v.contains("System.exit"));
    }

    @Test
    void scanSource_multipleViolations() {
        String code = """
                Runtime.getRuntime().exec("cmd");
                System.exit(1);
                new ProcessBuilder("evil").start();
                """;

        SecurityScanResult result = securityScanService.scanSource(code);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations().size()).isGreaterThanOrEqualTo(3);
    }
}
