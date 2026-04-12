package com.bekololek.pluginfactory.admin;

import com.bekololek.pluginfactory.admin.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/overview")
    public ResponseEntity<OverviewResponse> getOverview() {
        return ResponseEntity.ok(adminService.getOverview());
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserSummary>> getUsers(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getUsers(status, tier, search, pageable));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserDetail> getUserDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(adminService.getUserDetail(id));
    }

    @GetMapping("/builds")
    public ResponseEntity<Page<AdminBuildSummary>> getBuilds(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getBuilds(status, userId, from, to, pageable));
    }

    @GetMapping("/builds/stats")
    public ResponseEntity<BuildStatsResponse> getBuildStats(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(adminService.getBuildStats(from, to));
    }

    @GetMapping("/subscriptions")
    public ResponseEntity<SubscriptionListResponse> getSubscriptions(
            @RequestParam(required = false) String tier,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getSubscriptions(tier, status, pageable));
    }

    @GetMapping("/revenue")
    public ResponseEntity<RevenueResponse> getRevenue(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(adminService.getRevenue(from, to));
    }

    @GetMapping("/marketplace")
    public ResponseEntity<MarketplaceListResponse> getMarketplace(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getMarketplace(status, pageable));
    }

    @GetMapping("/teams")
    public ResponseEntity<Page<AdminTeamSummary>> getTeams(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(adminService.getTeams(pageable));
    }

    @GetMapping("/errors")
    public ResponseEntity<ErrorStatsResponse> getErrorStats(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        return ResponseEntity.ok(adminService.getErrorStats(from, to));
    }
}
