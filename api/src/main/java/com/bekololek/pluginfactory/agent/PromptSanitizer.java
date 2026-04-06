package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.common.exception.ValidationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Slf4j
public class PromptSanitizer {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act as", Pattern.CASE_INSENSITIVE),
            Pattern.compile("forget everything", Pattern.CASE_INSENSITIVE),
            Pattern.compile("new instructions:", Pattern.CASE_INSENSITIVE),
            Pattern.compile("override rules", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard", Pattern.CASE_INSENSITIVE),
            Pattern.compile("pretend you", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE)
    );

    private static final Pattern BASE64_PATTERN = Pattern.compile("[A-Za-z0-9+/=]{100,}");

    public record SanitizationResult(String cleanMessage, List<String> flags) {
        public boolean hasSuspiciousContent() {
            return !flags.isEmpty();
        }
    }

    public SanitizationResult sanitize(String userMessage) {
        List<String> flags = new ArrayList<>();

        if (userMessage == null || userMessage.isBlank()) {
            return new SanitizationResult("", flags);
        }

        String cleaned = userMessage;

        // Check injection patterns
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(cleaned).find()) {
                flags.add("INJECTION_PATTERN: " + pattern.pattern());
            }
        }

        // Check base64 payloads (100+ chars)
        if (BASE64_PATTERN.matcher(cleaned).find()) {
            flags.add("BASE64_PAYLOAD");
        }

        // Check excessive special characters (>30%)
        long specialCharCount = cleaned.chars()
                .filter(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))
                .count();
        if (cleaned.length() > 0 && (double) specialCharCount / cleaned.length() > 0.3) {
            flags.add("EXCESSIVE_SPECIAL_CHARS");
        }

        if (!flags.isEmpty()) {
            log.warn("Suspicious content detected and blocked in user message: {}", flags);
            throw new ValidationException(
                    "Message contains suspicious content and has been blocked. Please rephrase your request."
            );
        }

        return new SanitizationResult(cleaned, flags);
    }
}
