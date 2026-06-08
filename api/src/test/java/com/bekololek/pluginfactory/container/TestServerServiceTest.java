package com.bekololek.pluginfactory.container;

import com.bekololek.pluginfactory.container.TestServerService.SmokeResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestServerServiceTest {

    // evaluate() is a pure function of the server log; deps are unused here.
    private final TestServerService service = new TestServerService(null, null, null);

    @Test
    void passesWhenServerReachesDoneWithNoPluginErrors() {
        String log = "[12:00:00] [Server thread/INFO]: Starting minecraft server\n"
                + "[12:00:10] [Server thread/INFO]: [MyPlugin] Enabling MyPlugin v1.0.0\n"
                + "[12:00:20] [Server thread/INFO]: Done (20.1s)! For help, type \"help\"";
        SmokeResult r = service.evaluate(log, "MyPlugin");
        assertThat(r.passed()).isTrue();
    }

    @Test
    void failsWhenPluginCouldNotLoad() {
        String log = "[ERROR]: Could not load 'plugins/MyPlugin.jar' in folder 'plugins'\n"
                + "org.bukkit.plugin.InvalidDescriptionException: main is not defined\n"
                + "[INFO]: Done (5.0s)!";
        SmokeResult r = service.evaluate(log, "MyPlugin");
        assertThat(r.passed()).isFalse();
        assertThat(r.detail()).contains("Could not load");
    }

    @Test
    void failsOnEnableException() {
        String log = "[INFO]: [MyPlugin] Enabling MyPlugin v1.0.0\n"
                + "[ERROR]: Error occurred while enabling MyPlugin v1.0.0 (Is it up to date?)\n"
                + "java.lang.NullPointerException\n"
                + "[INFO]: Done (6.0s)!";
        assertThat(service.evaluate(log, "MyPlugin").passed()).isFalse();
    }

    @Test
    void failsWhenServerNeverFinishesStartup() {
        String log = "[INFO]: Starting minecraft server version 1.21.4\n"
                + "[INFO]: Preparing spawn area: 40%";
        SmokeResult r = service.evaluate(log, "MyPlugin");
        assertThat(r.passed()).isFalse();
        assertThat(r.detail()).contains("did not finish startup");
    }

    @Test
    void failsOnUnsupportedApiVersion() {
        String log = "[ERROR]: Could not load 'plugins/MyPlugin.jar' in folder 'plugins'\n"
                + "Unsupported API version 1.99\n[INFO]: Done (5.0s)!";
        assertThat(service.evaluate(log, "MyPlugin").passed()).isFalse();
    }
}
