package com.bekololek.pluginfactory.admin;

import com.bekololek.pluginfactory.admin.dto.*;
import com.bekololek.pluginfactory.build.*;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.marketplace.MarketplaceListing;
import com.bekololek.pluginfactory.marketplace.MarketplaceListingRepository;
import com.bekololek.pluginfactory.marketplace.Purchase;
import com.bekololek.pluginfactory.marketplace.PurchaseRepository;
import com.bekololek.pluginfactory.subscription.Subscription;
import com.bekololek.pluginfactory.subscription.SubscriptionRepository;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.team.Team;
import com.bekololek.pluginfactory.team.TeamMember;
import com.bekololek.pluginfactory.team.TeamMemberRepository;
import com.bekololek.pluginfactory.team.TeamRepository;
import com.bekololek.pluginfactory.user.ApiKey;
import com.bekololek.pluginfactory.user.ApiKeyRepository;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final BuildSessionRepository buildSessionRepository;
    private final BuildIterationRepository buildIterationRepository;
    private final BuildErrorRepository buildErrorRepository;
    private final TokenBudgetRepository tokenBudgetRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final MarketplaceListingRepository marketplaceListingRepository;
    private final PurchaseRepository purchaseRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ApiKeyRepository apiKeyRepository;

    // ── Overview ──────────────────────────────────────────────────────

    public OverviewResponse getOverview() {
        Instant now = Instant.now();
        Instant last24h = now.minus(24, ChronoUnit.HOURS);
        Instant last7d = now.minus(7, ChronoUnit.DAYS);
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();

        long totalUsers = userRepository.count();
        long activeUsersLast24h = userRepository.countByLastActiveAtAfter(last24h);
        long activeUsersLast7d = userRepository.countByLastActiveAtAfter(last7d);
        long newUsersToday = userRepository.countByCreatedAtAfter(startOfToday);

        long totalBuilds = buildSessionRepository.count();
        long buildsToday = buildSessionRepository.countByCreatedAtAfter(startOfToday);

        long completed = buildSessionRepository.countByStatus(BuildStatus.COMPLETED);
        long failed = buildSessionRepository.countByStatus(BuildStatus.FAILED);
        double successRate = (completed + failed) > 0
                ? (double) completed / (completed + failed) : 0.0;

        long activePaid = subscriptionRepository.countByStatusAndTierNot(
                Subscription.SubscriptionStatus.ACTIVE, Tier.FREE);

        long mrrCents = calculateMrr();
        long totalRevenueCents = purchaseRepository.sumRevenueCents();
        long activeTeams = teamRepository.count();

        return new OverviewResponse(
                totalUsers, activeUsersLast24h, activeUsersLast7d, newUsersToday,
                totalBuilds, buildsToday, successRate, activePaid,
                mrrCents, totalRevenueCents, activeTeams
        );
    }

    // ── Users ─────────────────────────────────────────────────────────

    public Page<AdminUserSummary> getUsers(String status, String tier, String search,
                                           Pageable pageable) {
        User.UserStatus userStatus = status != null ? User.UserStatus.valueOf(status) : null;
        Page<User> users = userRepository.findWithFilters(userStatus, search, pageable);

        return users.map(user -> {
            Subscription sub = subscriptionRepository.findByUserId(user.getId()).orElse(null);
            return new AdminUserSummary(
                    user.getId(), user.getEmail(), user.getDisplayName(),
                    user.getStatus().name(), user.getRole().name(),
                    sub != null ? sub.getTier().name() : "FREE",
                    sub != null ? sub.getBuildsUsedThisPeriod() : 0,
                    user.getLastActiveAt(), user.getCreatedAt()
            );
        });
    }

    public AdminUserDetail getUserDetail(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Subscription sub = subscriptionRepository.findByUserId(userId).orElse(null);

        Page<BuildSession> builds = buildSessionRepository.findByUserId(userId,
                PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<Team> teams = teamRepository.findTeamsByMemberId(userId);

        List<Purchase> purchases = purchaseRepository.findByBuyerId(userId);
        List<ApiKey> apiKeys = apiKeyRepository.findByUserId(userId);

        var userInfo = new AdminUserDetail.UserInfo(
                user.getId(), user.getEmail(), user.getDisplayName(),
                user.getStatus().name(), user.getRole().name(),
                user.getCreatedAt(), user.getLastActiveAt()
        );

        var subInfo = sub != null ? new AdminUserDetail.SubscriptionInfo(
                sub.getTier().name(), sub.getStatus().name(),
                sub.getBuildsUsedThisPeriod(), sub.getTokensUsedThisPeriod(),
                sub.getCurrentPeriodStart(), sub.getCurrentPeriodEnd(),
                sub.getStripeCustomerId()
        ) : null;

        var buildInfos = builds.getContent().stream()
                .map(b -> new AdminUserDetail.BuildInfo(
                        b.getId(), b.getStatus().name(), b.getComplexityScore(), b.getCreatedAt()))
                .toList();

        var teamInfos = teams.stream()
                .map(t -> {
                    String role = t.getMembers().stream()
                            .filter(m -> m.getUser().getId().equals(userId))
                            .findFirst()
                            .map(m -> m.getRole().name())
                            .orElse("MEMBER");
                    return new AdminUserDetail.TeamInfo(t.getId(), t.getName(), role);
                })
                .toList();

        var purchaseInfos = purchases.stream()
                .map(p -> {
                    String title = marketplaceListingRepository.findById(p.getListingId())
                            .map(MarketplaceListing::getTitle).orElse("Unknown");
                    return new AdminUserDetail.PurchaseInfo(title, p.getPriceCents(), p.getCreatedAt());
                })
                .toList();

        var apiKeyInfos = apiKeys.stream()
                .map(k -> new AdminUserDetail.ApiKeyInfo(
                        k.getName(), k.getLastFour(), k.getLastUsedAt(), k.isRevoked()))
                .toList();

        return new AdminUserDetail(userInfo, subInfo, buildInfos, teamInfos, purchaseInfos, apiKeyInfos);
    }

    // ── Builds ────────────────────────────────────────────────────────

    public Page<AdminBuildSummary> getBuilds(String status, UUID userId,
                                             Instant from, Instant to, Pageable pageable) {
        BuildStatus buildStatus = status != null ? BuildStatus.valueOf(status) : null;

        Page<BuildSession> builds = buildSessionRepository.findWithFilters(
                buildStatus, userId, from, to, pageable);

        return builds.map(b -> {
            String email = userRepository.findById(b.getUserId())
                    .map(User::getEmail).orElse("unknown");
            TokenBudget tb = tokenBudgetRepository.findBySessionId(b.getId()).orElse(null);
            long iterations = buildIterationRepository.countBySessionId(b.getId());
            return new AdminBuildSummary(
                    b.getId(), email, b.getStatus().name(), b.getCurrentPhase().name(),
                    b.getComplexityScore(),
                    tb != null ? tb.getConsumedTokens() : 0,
                    (int) iterations,
                    b.getCreatedAt(), b.getCompletedAt()
            );
        });
    }

    public BuildStatsResponse getBuildStats(Instant from, Instant to) {
        if (from == null) from = Instant.now().minus(30, ChronoUnit.DAYS);
        if (to == null) to = Instant.now();

        List<BuildSession> builds = buildSessionRepository.findByCreatedAtBetween(from, to);

        Map<String, Long> byStatus = builds.stream()
                .collect(Collectors.groupingBy(b -> b.getStatus().name(), Collectors.counting()));

        // Group by user's tier
        Map<String, Long> byTier = new LinkedHashMap<>();
        Map<UUID, String> userTierCache = new HashMap<>();
        for (BuildSession b : builds) {
            String tier = userTierCache.computeIfAbsent(b.getUserId(), uid ->
                    subscriptionRepository.findByUserId(uid)
                            .map(s -> s.getTier().name()).orElse("FREE"));
            byTier.merge(tier, 1L, Long::sum);
        }

        double avgComplexity = builds.stream()
                .filter(b -> b.getComplexityScore() != null)
                .mapToInt(BuildSession::getComplexityScore)
                .average().orElse(0.0);

        // Avg tokens per build
        double avgTokens = builds.stream()
                .mapToInt(b -> tokenBudgetRepository.findBySessionId(b.getId())
                        .map(TokenBudget::getConsumedTokens).orElse(0))
                .average().orElse(0.0);

        // Avg iterations per build
        double avgIterations = builds.stream()
                .mapToLong(b -> buildIterationRepository.countBySessionId(b.getId()))
                .average().orElse(0.0);

        // Timeline grouped by day
        Map<LocalDate, List<BuildSession>> byDay = builds.stream()
                .collect(Collectors.groupingBy(b ->
                        b.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate()));

        List<BuildStatsResponse.DayStats> timeline = byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new BuildStatsResponse.DayStats(
                        e.getKey().toString(),
                        e.getValue().size(),
                        e.getValue().stream().filter(b -> b.getStatus() == BuildStatus.COMPLETED).count(),
                        e.getValue().stream().filter(b -> b.getStatus() == BuildStatus.FAILED).count()
                ))
                .toList();

        return new BuildStatsResponse(
                builds.size(), byStatus, byTier, avgComplexity,
                avgTokens, avgIterations, timeline
        );
    }

    // ── Subscriptions ─────────────────────────────────────────────────

    public SubscriptionListResponse getSubscriptions(String tier, String status, Pageable pageable) {
        Tier tierEnum = tier != null ? Tier.valueOf(tier) : null;
        Subscription.SubscriptionStatus statusEnum = status != null
                ? Subscription.SubscriptionStatus.valueOf(status) : null;

        Page<Subscription> subs = subscriptionRepository.findWithFilters(tierEnum, statusEnum, pageable);

        List<AdminSubscriptionSummary> content = subs.getContent().stream()
                .map(s -> {
                    String email = userRepository.findById(s.getUserId())
                            .map(User::getEmail).orElse("unknown");
                    return new AdminSubscriptionSummary(
                            s.getUserId(), email, s.getTier().name(), s.getStatus().name(),
                            s.getBuildsUsedThisPeriod(), s.getTokensUsedThisPeriod(),
                            s.getCurrentPeriodEnd(), s.getStripeCustomerId()
                    );
                })
                .toList();

        Map<String, Long> byTier = new LinkedHashMap<>();
        for (Tier t : Tier.values()) {
            byTier.put(t.name(), subscriptionRepository.countByTier(t));
        }

        Map<String, Long> byStatusMap = new LinkedHashMap<>();
        for (Subscription.SubscriptionStatus ss : Subscription.SubscriptionStatus.values()) {
            byStatusMap.put(ss.name(), (long) subscriptionRepository.findByStatus(ss).size());
        }

        long mrrCents = calculateMrr();

        var summary = new SubscriptionListResponse.SubscriptionSummary(byTier, byStatusMap, mrrCents);
        return new SubscriptionListResponse(content, subs.getTotalElements(), subs.getTotalPages(), summary);
    }

    // ── Revenue ───────────────────────────────────────────────────────

    public RevenueResponse getRevenue(Instant from, Instant to) {
        long marketplaceRevenue = purchaseRepository.sumRevenueCents();
        long mrrCents = calculateMrr();

        List<Purchase> allPurchases = purchaseRepository.findAll();

        // Timeline by day
        Map<LocalDate, Long> dailyRevenue = allPurchases.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .filter(p -> (from == null || !p.getCreatedAt().isBefore(from))
                        && (to == null || !p.getCreatedAt().isAfter(to)))
                .collect(Collectors.groupingBy(
                        p -> p.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate(),
                        Collectors.summingLong(Purchase::getPriceCents)));

        List<RevenueResponse.DayRevenue> timeline = dailyRevenue.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new RevenueResponse.DayRevenue(e.getKey().toString(), e.getValue()))
                .toList();

        // Top selling listings
        Map<UUID, List<Purchase>> byListing = allPurchases.stream()
                .filter(p -> "COMPLETED".equals(p.getStatus()))
                .collect(Collectors.groupingBy(Purchase::getListingId));

        List<RevenueResponse.TopListing> topListings = byListing.entrySet().stream()
                .map(e -> {
                    String title = marketplaceListingRepository.findById(e.getKey())
                            .map(MarketplaceListing::getTitle).orElse("Unknown");
                    long sales = e.getValue().size();
                    long revenue = e.getValue().stream().mapToLong(Purchase::getPriceCents).sum();
                    return new RevenueResponse.TopListing(e.getKey(), title, sales, revenue);
                })
                .sorted(Comparator.comparingLong(RevenueResponse.TopListing::revenueCents).reversed())
                .limit(10)
                .toList();

        return new RevenueResponse(
                marketplaceRevenue + mrrCents, marketplaceRevenue, mrrCents,
                timeline, topListings
        );
    }

    // ── Marketplace ───────────────────────────────────────────────────

    public MarketplaceListResponse getMarketplace(String status, Pageable pageable) {
        Page<MarketplaceListing> listings = status != null
                ? marketplaceListingRepository.findByStatus(status, pageable)
                : marketplaceListingRepository.findAll(pageable);

        List<AdminMarketplaceSummary> content = listings.getContent().stream()
                .map(l -> {
                    String email = userRepository.findById(l.getSellerId())
                            .map(User::getEmail).orElse("unknown");
                    return new AdminMarketplaceSummary(
                            l.getId(), l.getTitle(), email, l.getCategory(),
                            l.getPriceCents(), l.getDownloadCount(), l.getAverageRating(),
                            l.getReviewCount(), l.getStatus(), l.getCreatedAt()
                    );
                })
                .toList();

        long totalListings = marketplaceListingRepository.count();
        long totalPurchases = purchaseRepository.count();
        long totalRevenue = purchaseRepository.sumRevenueCents();

        var summary = new MarketplaceListResponse.MarketplaceSummaryStats(
                totalListings, totalPurchases, totalRevenue);
        return new MarketplaceListResponse(
                content, listings.getTotalElements(), listings.getTotalPages(), summary);
    }

    // ── Teams ─────────────────────────────────────────────────────────

    public Page<AdminTeamSummary> getTeams(Pageable pageable) {
        Page<Team> teams = teamRepository.findAll(pageable);

        return teams.map(t -> {
            int memberCount = t.getMembers().size();
            int workspaceCount = t.getWorkspaces().size();
            // Count builds across all team workspaces
            long buildCount = t.getWorkspaces().stream()
                    .flatMap(ws -> buildSessionRepository.findByWorkspaceId(ws.getId()).stream())
                    .count();
            return new AdminTeamSummary(
                    t.getId(), t.getName(), t.getOwner().getEmail(),
                    memberCount, workspaceCount, buildCount, t.getCreatedAt()
            );
        });
    }

    // ── Errors ────────────────────────────────────────────────────────

    public ErrorStatsResponse getErrorStats(Instant from, Instant to) {
        List<BuildError> errors = buildErrorRepository.findWithFilters(from, to);

        Map<String, Long> byCategory = errors.stream()
                .collect(Collectors.groupingBy(BuildError::getCategory, Collectors.counting()));

        Map<String, Long> bySeverity = errors.stream()
                .collect(Collectors.groupingBy(BuildError::getSeverity, Collectors.counting()));

        // Top errors — group by truncated message (first 120 chars)
        List<ErrorStatsResponse.TopError> topErrors = errors.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getMessage().length() > 120
                                ? e.getMessage().substring(0, 120) : e.getMessage(),
                        Collectors.toList()))
                .entrySet().stream()
                .map(e -> new ErrorStatsResponse.TopError(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().get(0).getCategory()))
                .sorted(Comparator.comparingLong(ErrorStatsResponse.TopError::count).reversed())
                .limit(20)
                .toList();

        return new ErrorStatsResponse(errors.size(), byCategory, bySeverity, topErrors);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private long calculateMrr() {
        // Simple MRR: count active paid subscriptions × approximate tier price
        // These are approximate monthly cents values matching your Stripe prices
        Map<Tier, Long> tierMonthlyCents = Map.of(
                Tier.FREE, 0L,
                Tier.BASIC, 999L,
                Tier.PRO, 2999L,
                Tier.TEAM, 7999L
        );

        long mrr = 0;
        for (Subscription sub : subscriptionRepository.findByStatus(Subscription.SubscriptionStatus.ACTIVE)) {
            if (sub.getTier() != Tier.FREE) {
                mrr += tierMonthlyCents.getOrDefault(sub.getTier(), 0L);
            }
        }
        return mrr;
    }
}
