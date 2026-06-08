package com.bekololek.pluginfactory.build;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BuildLogRecorderTest {

    // The ANSI escape (ESC, 0x1B) built from its code point to avoid embedding
    // a raw control character in source.
    private static final String ESC = String.valueOf((char) 27);

    @Test
    void clean_stripsAnsiButKeepsBracketedText() {
        // Real mvn output: ESC[1;31m ... ESC[m around "[ERROR]" markers.
        String raw = ESC + "[1;31m[ERROR]" + ESC + "[m COMPILATION ERROR : [INFO] building";
        String cleaned = BuildLogRecorder.clean(raw);
        assertThat(cleaned).isEqualTo("[ERROR] COMPILATION ERROR : [INFO] building");
        assertThat(cleaned).doesNotContain(ESC);
    }

    @Test
    void clean_truncatesOverCapKeepingTail() {
        String big = "x".repeat(BuildLogRecorder.MAX_CHARS + 500) + "TAIL_MARKER";
        String cleaned = BuildLogRecorder.clean(big);
        assertThat(cleaned.length()).isLessThan(BuildLogRecorder.MAX_CHARS + 100);
        assertThat(cleaned).contains("truncated");
        assertThat(cleaned).endsWith("TAIL_MARKER");
    }

    @Test
    void clean_leavesShortPlainTextUntouched() {
        assertThat(BuildLogRecorder.clean("Done (12.3s)! [Server]")).isEqualTo("Done (12.3s)! [Server]");
    }
}
