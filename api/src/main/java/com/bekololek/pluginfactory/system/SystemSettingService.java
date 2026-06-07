package com.bekololek.pluginfactory.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Global runtime settings the admin dashboard can flip. Backed by the
 * {@code system_settings} table so values survive API restarts/redeploys.
 *
 * <p>Currently exposes the maintenance-mode flag: when on, the user-facing
 * web app shows a "temporarily down" page (with a Discord link) instead of
 * the normal UI.
 */
@Service
@Slf4j
public class SystemSettingService {

    static final String MAINTENANCE_KEY = "maintenance_mode";

    private final SystemSettingRepository repository;
    private final String discordUrl;

    public SystemSettingService(SystemSettingRepository repository,
                                @Value("${app.discord-url:}") String discordUrl) {
        this.repository = repository;
        this.discordUrl = discordUrl;
    }

    public boolean isMaintenanceMode() {
        return repository.findById(MAINTENANCE_KEY)
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(false);
    }

    @Transactional
    public void setMaintenanceMode(boolean enabled) {
        SystemSetting setting = repository.findById(MAINTENANCE_KEY)
                .orElseGet(() -> {
                    SystemSetting s = new SystemSetting();
                    s.setKey(MAINTENANCE_KEY);
                    return s;
                });
        setting.setValue(Boolean.toString(enabled));
        setting.setUpdatedAt(Instant.now());
        repository.save(setting);
        log.info("Maintenance mode set to {}", enabled);
    }

    /** Public Discord invite shown on the maintenance page (may be empty). */
    public String getDiscordUrl() {
        return discordUrl == null ? "" : discordUrl;
    }
}
