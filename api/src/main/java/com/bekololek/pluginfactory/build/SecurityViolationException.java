package com.bekololek.pluginfactory.build;

import java.util.List;

public class SecurityViolationException extends RuntimeException {

    private final List<String> violations;

    public SecurityViolationException(List<String> violations) {
        super("Security scan failed with " + violations.size() + " violation(s): "
                + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
