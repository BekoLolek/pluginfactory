package com.bekololek.pluginfactory.agent;

import com.bekololek.pluginfactory.plan.PlanDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retrieval-augmented "skills" for the implementer: a library of verified,
 * compilable Paper snippets. For each plan we select only the patterns the
 * plugin actually needs (by plan shape + keyword signals) and inject them into
 * the implementer's user message. This grounds the model in correct idioms for
 * the specific capabilities at hand instead of generic guidance.
 */
@Service
@Slf4j
public class PatternLibraryService {

    private static final String BASE = "prompts/patterns/";
    private static final int MAX_PATTERNS = 6;

    /** A keyword-triggered pattern: pulled in when any keyword appears in the plan signal. */
    private record KeywordPattern(String id, String file, List<String> keywords) {}

    private static final List<KeywordPattern> KEYWORD_PATTERNS = List.of(
            new KeywordPattern("custom-items-pdc", "custom-items-pdc.md", List.of(
                    "persistentdata", "pdc", "nbt", "namespacedkey", "custom item", "custom weapon",
                    "tagged item", "identify item", "unique item", "custom tool")),
            new KeywordPattern("recipes", "recipes.md", List.of(
                    "recipe", "craft", "crafting", "shaped", "shapeless", "smithing")),
            new KeywordPattern("gui-menus", "gui-menus.md", List.of(
                    "gui", "menu", "inventory click", "inventoryclick", "open inventory",
                    "shop interface", "selector", "clickable")),
            new KeywordPattern("scoreboard-teams", "scoreboard-teams.md", List.of(
                    "scoreboard", "sidebar", "objective", "team", "tab list", "below name")),
            new KeywordPattern("vault-economy", "vault-economy.md", List.of(
                    "vault", "economy", "balance", "money", "currency", "eco ")),
            new KeywordPattern("cooldowns", "cooldowns.md", List.of(
                    "cooldown", "per-player timer", "once per", "delay between")),
            new KeywordPattern("persistence-yaml", "persistence-yaml.md", List.of(
                    "persist", "save data", "across restart", "survive restart", "data file",
                    "store player", "database", "save player", "autosave")),
            new KeywordPattern("scheduler-tasks", "scheduler-tasks.md", List.of(
                    "schedule", "timer", "repeat", "interval", "countdown", "periodic",
                    "bukkitrunnable", "tick"))
    );

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Returns a markdown block of the patterns relevant to this plan, or an
     * empty string if none apply. Structural patterns (commands, events,
     * config) come from the plan shape; the rest from keyword signals.
     */
    public String selectPatterns(PlanDocument plan) {
        if (plan == null) {
            return "";
        }
        String signal = signalText(plan);
        LinkedHashSet<String> chosen = new LinkedHashSet<>();

        if (!isEmptyArray(plan.getCommands())) {
            chosen.add("commands-tabcomplete");
        }
        if (!isEmptyArray(plan.getEventListeners())) {
            chosen.add("events");
        }
        boolean needsConfig = !isEmptyArray(plan.getConfigSchema())
                || (plan.getViabilityStatus() != null
                    && !"READY".equalsIgnoreCase(plan.getViabilityStatus()));
        if (needsConfig) {
            chosen.add("config-reload");
        }

        for (KeywordPattern p : KEYWORD_PATTERNS) {
            if (chosen.size() >= MAX_PATTERNS) {
                break;
            }
            for (String kw : p.keywords()) {
                if (signal.contains(kw)) {
                    chosen.add(p.id());
                    break;
                }
            }
        }

        if (chosen.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder()
                .append("## Verified patterns for this plugin\n")
                .append("Authoritative, compilable Paper snippets for the capabilities this plugin needs. ")
                .append("Follow these idioms exactly — imports, null-safety, and registration matter.\n\n");
        for (String id : chosen) {
            String body = load(fileFor(id));
            if (!body.isBlank()) {
                sb.append(body).append("\n\n---\n\n");
            }
        }
        log.info("PatternLibrary selected {} patterns: {}", chosen.size(), chosen);
        return sb.toString();
    }

    private String fileFor(String id) {
        return switch (id) {
            case "commands-tabcomplete" -> "commands-tabcomplete.md";
            case "events" -> "events.md";
            case "config-reload" -> "config-reload.md";
            default -> KEYWORD_PATTERNS.stream()
                    .filter(p -> p.id().equals(id))
                    .map(KeywordPattern::file)
                    .findFirst()
                    .orElse(id + ".md");
        };
    }

    private String signalText(PlanDocument plan) {
        StringBuilder sb = new StringBuilder();
        appendLower(sb, plan.getDescription());
        appendLower(sb, plan.getCommands());
        appendLower(sb, plan.getEventListeners());
        appendLower(sb, plan.getConfigSchema());
        appendLower(sb, plan.getDependencies());
        appendLower(sb, plan.getClasses());
        appendLower(sb, plan.getAutoHandled());
        appendLower(sb, plan.getSetupSteps());
        return sb.toString();
    }

    private void appendLower(StringBuilder sb, String s) {
        if (s != null) {
            sb.append(' ').append(s.toLowerCase(Locale.ROOT));
        }
    }

    private boolean isEmptyArray(String json) {
        if (json == null) {
            return true;
        }
        String t = json.trim();
        return t.isEmpty() || t.equals("[]") || t.equals("null");
    }

    private String load(String file) {
        return cache.computeIfAbsent(file, f -> {
            try (InputStream is = new ClassPathResource(BASE + f).getInputStream()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Pattern file not found on classpath: {}", f);
                return "";
            }
        });
    }
}
