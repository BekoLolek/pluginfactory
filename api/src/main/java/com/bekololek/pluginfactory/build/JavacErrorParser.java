package com.bekololek.pluginfactory.build;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses Maven/javac compilation output into structured {@link JavacError} records
 * so the build-recovery prompt can hand the LLM something it can actually act on
 * instead of raw stderr.
 *
 * <p>Maven prints each error twice — once in the COMPILATION ERROR block and again
 * in the failure summary at the bottom. We dedupe on (file, line, column, summary).
 * Lines may carry a leading {@code [ERROR]} prefix or not depending on which block
 * they came from; both shapes parse to the same record.
 */
@Service
public class JavacErrorParser {

    // Strip Maven SGR color codes. Matches the ESC-prefixed real form
    // (ESC + "[" + params + "m") and the bare form ("[" + params + "m")
    // that appears when the ESC byte gets stripped somewhere in the pipe.
    // Anchoring on the trailing "m" keeps content like "[Help 1]" or
    // "[103,21]" intact — those don't end in "m" after digits/empty.
    private static final Pattern ANSI_ESCAPE = Pattern.compile(
            "\u001B?\\[(?:\\d+(?:;\\d+)*)?m"
    );

    private static final Pattern ERROR_HEADER = Pattern.compile(
            "^(?:\\[ERROR\\]\\s+)?(.+\\.java):\\[(\\d+),(\\d+)\\]\\s*(.*)$"
    );

    private static final Pattern KV_LINE = Pattern.compile(
            "^(?:\\[ERROR\\])?\\s+([a-zA-Z]+):\\s*(.*)$"
    );

    public List<JavacError> parse(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return List.of();
        }

        String stripped = ANSI_ESCAPE.matcher(rawOutput).replaceAll("");
        String[] lines = stripped.split("\\r?\\n");

        Map<String, JavacError> deduped = new LinkedHashMap<>();

        for (int i = 0; i < lines.length; i++) {
            Matcher header = ERROR_HEADER.matcher(lines[i]);
            if (!header.matches()) {
                continue;
            }

            String file = header.group(1).trim();
            int lineNum = Integer.parseInt(header.group(2));
            int col = Integer.parseInt(header.group(3));
            String summary = header.group(4).trim();

            List<String> continuation = new ArrayList<>();
            int j = i + 1;
            while (j < lines.length && isContinuation(lines[j])) {
                continuation.add(lines[j]);
                j++;
            }
            i = j - 1;

            JavacError err = build(file, lineNum, col, summary, continuation);
            String key = file + ":" + lineNum + ":" + col + ":" + summary;
            deduped.putIfAbsent(key, err);
        }

        return new ArrayList<>(deduped.values());
    }

    private boolean isContinuation(String line) {
        if (line.isBlank()) {
            return false;
        }
        // A continuation either has no [ERROR] prefix (and is therefore an
        // indented "  required: ..." line) OR has [ERROR] followed by
        // whitespace + non-path content. A new error always starts with
        // [ERROR] /path/file.java:[L,C], which matches ERROR_HEADER.
        if (ERROR_HEADER.matcher(line).matches()) {
            return false;
        }
        // Maven trailers like "[ERROR] -> [Help 1]", "[ERROR] To see ...",
        // "[ERROR] Failed to execute goal ...", "[ERROR] COMPILATION ERROR :",
        // "[ERROR] Re-run Maven ...", "[ERROR] For more information ...",
        // and bare "[ERROR]" framing lines are not part of any single error.
        String trimmed = line.replaceFirst("^\\[ERROR\\]\\s*", "").trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.startsWith("-> ")
                || trimmed.startsWith("To see")
                || trimmed.startsWith("Re-run")
                || trimmed.startsWith("For more")
                || trimmed.startsWith("[Help")
                || trimmed.startsWith("Failed to execute")
                || trimmed.startsWith("COMPILATION ERROR")
                || trimmed.startsWith("Compilation failure")) {
            return false;
        }
        return true;
    }

    private JavacError build(String file, int lineNum, int col, String summary, List<String> continuation) {
        String required = null;
        String found = null;
        String reason = null;
        String symbol = null;
        String location = null;

        for (String c : continuation) {
            Matcher m = KV_LINE.matcher(c);
            if (!m.matches()) {
                continue;
            }
            String key = m.group(1).toLowerCase();
            String value = m.group(2).trim();
            switch (key) {
                case "required" -> required = value;
                case "found" -> found = value;
                case "reason" -> reason = value;
                case "symbol" -> symbol = value;
                case "location" -> location = value;
                default -> { /* ignore unknown keys */ }
            }
        }

        JavacError.Kind kind = classifyKind(summary);

        String sourceType = null;
        String targetType = null;
        if (kind == JavacError.Kind.INCOMPATIBLE_TYPES) {
            String[] parts = parseIncompatibleTypes(summary);
            sourceType = parts[0];
            targetType = parts[1];
        }

        StringBuilder rawBuilder = new StringBuilder();
        rawBuilder.append(file).append(":[").append(lineNum).append(',').append(col).append("] ").append(summary);
        for (String c : continuation) {
            rawBuilder.append('\n').append(c);
        }

        return new JavacError(
                file, lineNum, col, kind, summary,
                required, found, reason, symbol, location,
                sourceType, targetType,
                rawBuilder.toString()
        );
    }

    private JavacError.Kind classifyKind(String summary) {
        String s = summary.toLowerCase();
        if (s.contains("cannot be applied to given types")) {
            return JavacError.Kind.SIGNATURE_MISMATCH;
        }
        if (s.contains("incompatible types")) {
            return JavacError.Kind.INCOMPATIBLE_TYPES;
        }
        if (s.contains("cannot find symbol") || s.contains("cannot resolve")) {
            return JavacError.Kind.CANNOT_FIND_SYMBOL;
        }
        if (s.contains("package") && s.contains("does not exist")) {
            return JavacError.Kind.PACKAGE_NOT_FOUND;
        }
        if (s.contains("missing return statement")) {
            return JavacError.Kind.MISSING_RETURN;
        }
        if (s.contains("';' expected") || s.contains("not a statement") || s.contains("expected")) {
            return JavacError.Kind.SYNTAX_ERROR;
        }
        return JavacError.Kind.OTHER;
    }

    /** Returns [sourceType, targetType] from "incompatible types: A cannot be converted to B". */
    private String[] parseIncompatibleTypes(String summary) {
        Pattern p = Pattern.compile(
                "incompatible types:\\s*(\\S+)\\s+cannot be converted to\\s+(\\S+)",
                Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(summary);
        if (m.find()) {
            return new String[]{m.group(1), m.group(2)};
        }
        return new String[]{null, null};
    }
}
