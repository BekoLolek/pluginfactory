package com.bekololek.pluginfactory.build;

import java.util.List;

public record SecurityScanResult(
        boolean passed,
        List<String> violations
) {
}
