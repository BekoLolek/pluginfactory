package com.bekololek.pluginfactory.build;

/**
 * Structured representation of a single javac/Maven compiler error,
 * extracted from raw build output by {@link JavacErrorParser}.
 *
 * <p>Field semantics depend on {@link Kind}: {@code required}/{@code found}
 * are populated for SIGNATURE_MISMATCH; {@code symbol}/{@code location} for
 * CANNOT_FIND_SYMBOL; {@code sourceType}/{@code targetType} for
 * INCOMPATIBLE_TYPES. Any field not relevant to the kind is null.
 */
public record JavacError(
        String filePath,
        int line,
        int column,
        Kind kind,
        String summary,
        String required,
        String found,
        String reason,
        String symbol,
        String location,
        String sourceType,
        String targetType,
        String raw
) {

    public enum Kind {
        SIGNATURE_MISMATCH,
        INCOMPATIBLE_TYPES,
        CANNOT_FIND_SYMBOL,
        PACKAGE_NOT_FOUND,
        MISSING_RETURN,
        SYNTAX_ERROR,
        OTHER
    }

    public String fileName() {
        if (filePath == null) {
            return "<unknown>";
        }
        int slash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return slash >= 0 ? filePath.substring(slash + 1) : filePath;
    }
}
