package com.bekololek.pluginfactory.email;

import com.bekololek.pluginfactory.build.BuildSession;
import com.bekololek.pluginfactory.build.BuildSessionService;
import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationService {

    private final EmailService emailService;
    private final BuildSessionService buildSessionService;
    private final UserRepository userRepository;
    private final PlanDocumentRepository planDocumentRepository;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    private final EmailProperties emailProperties;

    public void notifyBuildSuccess(UUID sessionId) {
        try {
            BuildSession session = buildSessionService.getSessionById(sessionId);
            User user = userRepository.findById(session.getUserId()).orElse(null);
            if (user == null) return;

            String pluginName = resolvePluginName(sessionId);
            Map<String, Object> vars = baseVars();
            vars.put("displayName", user.getDisplayName());
            vars.put("pluginName", pluginName);
            vars.put("dashboardUrl", baseUrl + "/builds/" + sessionId);

            emailService.sendHtml(
                    user.getEmail(),
                    "Your plugin is ready — " + pluginName,
                    "build-success",
                    vars);
        } catch (Exception e) {
            log.error("Failed to send build-success email for session {}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * @param category one of "COMPILATION", "SECURITY", or "GENERAL" — never the raw error string
     */
    public void notifyBuildFailed(UUID sessionId, String category) {
        try {
            BuildSession session = buildSessionService.getSessionById(sessionId);
            User user = userRepository.findById(session.getUserId()).orElse(null);
            if (user == null) return;

            String pluginName = resolvePluginName(sessionId);
            Map<String, Object> vars = baseVars();
            vars.put("displayName", user.getDisplayName());
            vars.put("pluginName", pluginName);
            vars.put("failureReason", friendlyFailureReason(category));
            vars.put("dashboardUrl", baseUrl + "/builds/" + sessionId);

            emailService.sendHtml(
                    user.getEmail(),
                    "Build failed — " + pluginName,
                    "build-failed",
                    vars);
        } catch (Exception e) {
            log.error("Failed to send build-failed email for session {}: {}", sessionId, e.getMessage());
        }
    }

    public void notifyInactivity(User user) {
        try {
            Map<String, Object> vars = baseVars();
            vars.put("displayName", user.getDisplayName());
            vars.put("dashboardUrl", baseUrl);

            emailService.sendHtml(
                    user.getEmail(),
                    "Missing your plugins — come back and build something",
                    "inactivity-reminder",
                    vars);
        } catch (Exception e) {
            log.error("Failed to send inactivity email to user {}: {}", user.getId(), e.getMessage());
        }
    }

    public void sendManual(String recipientEmail, String template) {
        String displayName = userRepository.findByEmail(recipientEmail)
                .map(User::getDisplayName)
                .orElse(recipientEmail);

        Map<String, Object> vars = baseVars();
        vars.put("displayName", displayName);
        vars.put("dashboardUrl", baseUrl);
        if ("build-success".equals(template)) {
            vars.put("pluginName", "your plugin");
        } else if ("build-failed".equals(template)) {
            vars.put("pluginName", "your plugin");
            vars.put("failureReason", "The build failed during compilation.");
        }

        String subject = switch (template) {
            case "build-success"       -> "Your plugin is ready";
            case "build-failed"        -> "Build failed";
            case "inactivity-reminder" -> "Missing your plugins — come back and build something";
            default -> throw new IllegalArgumentException("Unknown template: " + template);
        };

        emailService.sendHtml(recipientEmail, subject, template, vars);
    }

    private Map<String, Object> baseVars() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("discordUrl", emailProperties.getDiscordUrl());
        return vars;
    }

    private static String friendlyFailureReason(String category) {
        return switch (category) {
            case "COMPILATION" -> "The build failed during compilation.";
            case "SECURITY"    -> "The build failed during the security scan.";
            default            -> "The build failed due to an unexpected issue.";
        };
    }

    private String resolvePluginName(UUID sessionId) {
        return planDocumentRepository.findBySessionId(sessionId)
                .map(PlanDocument::getPluginName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("your plugin");
    }
}
