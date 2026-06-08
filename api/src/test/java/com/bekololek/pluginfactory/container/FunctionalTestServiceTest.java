package com.bekololek.pluginfactory.container;

import com.bekololek.pluginfactory.container.FunctionalTestService.FunctionalResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FunctionalTestServiceTest {

    // parse() is a pure function of the harness output; deps are unused here.
    private final FunctionalTestService service = new FunctionalTestService(null, null, null);

    @Test
    void parsesPassingResult() {
        String out = "noise before\n"
                + "===PF_RESULT_BEGIN===\n"
                + "{\"passed\":true,\"scenarios\":[{\"name\":\"ping\",\"passed\":true,\"error\":null}]}\n"
                + "===PF_RESULT_END===\ntrailing";
        FunctionalResult r = service.parse(out);
        assertThat(r.ran()).isTrue();
        assertThat(r.passed()).isTrue();
        assertThat(r.scenarios()).hasSize(1);
    }

    @Test
    void parsesFailingResultWithDetail() {
        String out = "===PF_RESULT_BEGIN===\n"
                + "{\"passed\":false,\"scenarios\":["
                + "{\"name\":\"give item\",\"passed\":false,\"error\":\"no matching item\"}]}\n"
                + "===PF_RESULT_END===";
        FunctionalResult r = service.parse(out);
        assertThat(r.ran()).isTrue();
        assertThat(r.passed()).isFalse();
        assertThat(r.detail()).contains("FAILED").contains("give item").contains("no matching item");
    }

    @Test
    void missingMarkersIsSkippedAndFailsOpen() {
        FunctionalResult r = service.parse("server crashed, no json at all");
        assertThat(r.ran()).isFalse();
        assertThat(r.passed()).isTrue(); // fail open — never block delivery on infra noise
    }
}
