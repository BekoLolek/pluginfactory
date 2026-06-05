package com.bekololek.pluginfactory.build;

/**
 * Thrown when the compiled plugin JAR fails the runtime smoke test — i.e. it
 * compiled and passed security scanning, but did not load/enable cleanly on a
 * real Paper server (bad plugin.yml, missing registration, NoClassDefFound,
 * enable-time exception, etc.). Routed to {@code handleBuildError} as a
 * {@code RUNTIME} error.
 */
public class RuntimeTestException extends RuntimeException {
    public RuntimeTestException(String message) {
        super(message);
    }
}
