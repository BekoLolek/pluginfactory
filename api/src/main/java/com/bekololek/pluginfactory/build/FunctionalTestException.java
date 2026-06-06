package com.bekololek.pluginfactory.build;

/**
 * Thrown when the plugin compiled and enabled, but failed one or more
 * functional (in-server bot) test scenarios. Routed to {@code handleBuildError}
 * as a {@code FUNCTIONAL} error, which is retryable via the auto-fix loop.
 */
public class FunctionalTestException extends RuntimeException {
    public FunctionalTestException(String message) {
        super(message);
    }
}
