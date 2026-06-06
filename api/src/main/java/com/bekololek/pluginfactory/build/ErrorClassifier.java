package com.bekololek.pluginfactory.build;

import org.springframework.stereotype.Service;

@Service
public class ErrorClassifier {

    public ErrorCategory classify(String errorMessage) {
        if (errorMessage == null) {
            return ErrorCategory.STRUCTURAL;
        }
        String lower = errorMessage.toLowerCase();

        // SECURITY - immediate block
        if (containsAny(lower, "runtime.exec", "processbuilder", "socket", "serversocket",
                "urlconnection", "sun.misc.unsafe", "net.minecraft.server",
                "java.lang.reflect", "class.forname")) {
            return ErrorCategory.SECURITY;
        }

        // RECOVERABLE - compilation errors worth retrying
        if (containsAny(lower, "cannot find symbol", "cannot resolve", "incompatible types",
                "missing return", "unreported exception", "not a statement", "';' expected",
                "does not exist", "cannot be applied", "null pointer",
                "variable might not have been initialized")) {
            return ErrorCategory.RECOVERABLE;
        }

        return ErrorCategory.STRUCTURAL;
    }

    public enum ErrorCategory {
        RECOVERABLE,
        STRUCTURAL,
        SECURITY
    }

    private boolean containsAny(String text, String... patterns) {
        for (String p : patterns) {
            if (text.contains(p)) {
                return true;
            }
        }
        return false;
    }
}
