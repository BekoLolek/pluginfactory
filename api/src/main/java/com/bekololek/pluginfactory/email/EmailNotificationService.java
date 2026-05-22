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

    public void notifyBuildSuccess(UUID sessionId) {
        try {
            BuildSession session = buildSessionService.getSessionById(sessionId);
            User user = userRepository.findById(session.getUserId()).orElse(null);
            if (user == null) return;

            String pluginName = resolvePluginName(sessionId);
            Map<String, Object> vars = new HashMap<>();
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

    public void notifyBuildFailed(UUID sessionId, String errorMessage) {
        try {
            BuildSession session = buildSessionService.getSessionById(sessionId);
            User user = userRepository.findById(session.getUserId()).orElse(null);
            if (user == null) return;

            String pluginName = resolvePluginName(sessionId);
            String errorSummary = errorMessage != null && errorMessage.length() > 600
                    ? errorMessage.substring(0, 600) + "…"
                    : (errorMessage != null ? errorMessage : "An unexpected error occurred.");

            Map<String, Object> vars = new HashMap<>();
            vars.put("displayName", user.getDisplayName());
            vars.put("pluginName", pluginName);
            vars.put("errorSummary", errorSummary);
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
            Map<String, Object> vars = new HashMap<>();
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

    private String resolvePluginName(UUID sessionId) {
        return planDocumentRepository.findBySessionId(sessionId)
                .map(PlanDocument::getPluginName)
                .filter(n -> n != null && !n.isBlank())
                .orElse("your plugin");
    }
}
