package com.bekololek.pluginfactory.system;

import com.bekololek.pluginfactory.system.dto.SystemStatusDto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public, unauthenticated system status. The web app polls
 * {@code GET /api/v1/system/status} on load (and periodically) so it can
 * show a maintenance page — including to logged-out visitors — without
 * needing a token.
 */
@RestController
@RequestMapping("/api/v1/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemSettingService systemSettingService;

    @GetMapping("/status")
    public ResponseEntity<SystemStatusDto> status() {
        return ResponseEntity.ok(new SystemStatusDto(
                systemSettingService.isMaintenanceMode(),
                systemSettingService.getDiscordUrl()));
    }
}
