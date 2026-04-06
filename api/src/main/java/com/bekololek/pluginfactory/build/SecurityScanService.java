package com.bekololek.pluginfactory.build;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SecurityScanService {

    private static final List<SecurityRule> RULES = List.of(
            new SecurityRule(
                    Pattern.compile("Runtime\\s*\\.\\s*getRuntime\\s*\\(\\s*\\)\\s*\\.\\s*exec"),
                    "Runtime.exec() is not allowed - potential arbitrary command execution"
            ),
            new SecurityRule(
                    Pattern.compile("new\\s+ProcessBuilder"),
                    "ProcessBuilder is not allowed - potential arbitrary command execution"
            ),
            new SecurityRule(
                    Pattern.compile("new\\s+ServerSocket"),
                    "ServerSocket is not allowed - plugins should not open server sockets"
            ),
            new SecurityRule(
                    Pattern.compile("new\\s+java\\.net\\.ServerSocket"),
                    "java.net.ServerSocket is not allowed - plugins should not open server sockets"
            ),
            new SecurityRule(
                    Pattern.compile("URLConnection|HttpURLConnection|HttpsURLConnection"),
                    "URLConnection is not allowed - use Bukkit scheduler for async HTTP calls"
            ),
            new SecurityRule(
                    Pattern.compile("sun\\.misc\\.Unsafe"),
                    "sun.misc.Unsafe is not allowed - unsafe memory operations"
            ),
            new SecurityRule(
                    Pattern.compile("net\\.minecraft\\.server"),
                    "NMS (net.minecraft.server) is not allowed - use Paper/Bukkit API only"
            ),
            new SecurityRule(
                    Pattern.compile("Class\\.forName"),
                    "Class.forName is not allowed - potential dynamic class loading exploit"
            ),
            new SecurityRule(
                    Pattern.compile("java\\.lang\\.reflect\\.(?:Method|Field|Constructor)\\."),
                    "Suspicious reflection usage detected - java.lang.reflect operations are restricted"
            ),
            new SecurityRule(
                    Pattern.compile("System\\.exit"),
                    "System.exit() is not allowed - plugins must not terminate the JVM"
            ),
            new SecurityRule(
                    Pattern.compile("new\\s+File(?:Output|Input|Writer|Reader)\\s*\\(\\s*(?:\"[^\"]*(?:/|\\\\)(?!plugin)|new\\s+File\\s*\\(\\s*\"[^\"]*(?:/|\\\\)(?!plugin))"),
                    "File operations outside plugin data folder are not allowed"
            ),
            new SecurityRule(
                    Pattern.compile("java\\.nio\\.file\\.Files\\.(?:write|delete|move|copy)\\s*\\("),
                    "Direct java.nio.file.Files operations are restricted - use plugin data folder"
            )
    );

    public SecurityScanResult scanSource(String allSource) {
        List<String> violations = new ArrayList<>();

        for (SecurityRule rule : RULES) {
            if (rule.pattern().matcher(allSource).find()) {
                violations.add(rule.message());
                log.warn("Security violation found: {}", rule.message());
            }
        }

        boolean passed = violations.isEmpty();
        log.info("Security scan completed: passed={}, violations={}", passed, violations.size());
        return new SecurityScanResult(passed, violations);
    }

    private record SecurityRule(Pattern pattern, String message) {
    }
}
