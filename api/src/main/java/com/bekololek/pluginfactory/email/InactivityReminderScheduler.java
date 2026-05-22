package com.bekololek.pluginfactory.email;

import com.bekololek.pluginfactory.build.BuildSessionRepository;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class InactivityReminderScheduler {

    private final BuildSessionRepository buildSessionRepository;
    private final UserRepository userRepository;
    private final EmailNotificationService emailNotificationService;

    // Runs every day at 10:00 AM UTC
    @Scheduled(cron = "0 0 10 * * *")
    public void sendInactivityReminders() {
        // Find users whose last build was between 10 and 11 days ago.
        // The 1-day window means each user is emailed exactly once per inactivity period
        // (i.e. one email at the 10-day mark, not every day after that).
        Instant from = Instant.now().minus(11, ChronoUnit.DAYS);
        Instant to   = Instant.now().minus(10, ChronoUnit.DAYS);

        List<UUID> inactiveUserIds = buildSessionRepository
                .findUserIdsWithLastSessionBetween(from, to);

        if (inactiveUserIds.isEmpty()) {
            log.debug("Inactivity reminder: no eligible users today");
            return;
        }

        log.info("Inactivity reminder: sending to {} user(s)", inactiveUserIds.size());

        for (UUID userId : inactiveUserIds) {
            userRepository.findById(userId).ifPresent(user -> {
                if (user.getStatus() == User.UserStatus.ACTIVE) {
                    emailNotificationService.notifyInactivity(user);
                }
            });
        }
    }
}
