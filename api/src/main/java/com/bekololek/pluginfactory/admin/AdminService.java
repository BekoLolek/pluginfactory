package com.bekololek.pluginfactory.admin;

import com.bekololek.pluginfactory.admin.dto.*;
import com.bekololek.pluginfactory.agent.ChatbotAgent;
import com.bekololek.pluginfactory.agent.dto.AgentResponse;
import com.bekololek.pluginfactory.build.*;
import com.bekololek.pluginfactory.build.dto.ChatMessageDto;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.marketplace.MarketplaceListing;
import com.bekololek.pluginfactory.marketplace.MarketplaceListingRepository;
import com.bekololek.pluginfactory.marketplace.Purchase;
import com.bekololek.pluginfactory.marketplace.PurchaseRepository;
import com.bekololek.pluginfactory.plan.PlanDocument;
import com.bekololek.pluginfactory.plan.PlanDocumentRepository;
import com.bekololek.pluginfactory.plan.dto.*;
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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
@Slf4j
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
    private final FailedBuildRecoveryService failedBuildRecoveryService;
    private final PlanDocumentRepository planDocumentRepository;
    private final BuildSessionService buildSessionService;
    private final TokenBudgetService tokenBudgetService;
    private final ChatMessageService chatMessageService;
    private final ChatbotAgent chatbotAgent;
    private final ObjectMapper objectMapper;

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

    public AdminBuildSummary getBuild(UUID sessionId) {
        BuildSession b = buildSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Build session not found"));
        String email = userRepository.findById(b.getUserId())
                .map(User::getEmail).orElse("unknown");
        TokenBudget tb = tokenBudgetRepository.findBySessionId(b.getId()).orElse(null);
        long iterations = buildIterationRepository.countBySessionId(b.getId());
        BuildError latest = buildErrorRepository.findFirstBySessionIdOrderByCreatedAtDesc(b.getId());
        return new AdminBuildSummary(
                b.getId(), email, b.getStatus().name(), b.getCurrentPhase().name(),
                b.getComplexityScore(),
                tb != null ? tb.getConsumedTokens() : 0,
                (int) iterations,
                b.getCreatedAt(), b.getCompletedAt(),
                latest != null ? latest.getCategory() : null,
                latest != null ? truncate(latest.getMessage(), 240) : null
        );
    }

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
            BuildError latest = buildErrorRepository.findFirstBySessionIdOrderByCreatedAtDesc(b.getId());
            return new AdminBuildSummary(
                    b.getId(), email, b.getStatus().name(), b.getCurrentPhase().name(),
                    b.getComplexityScore(),
                    tb != null ? tb.getConsumedTokens() : 0,
                    (int) iterations,
                    b.getCreatedAt(), b.getCompletedAt(),
                    latest != null ? latest.getCategory() : null,
                    latest != null ? truncate(latest.getMessage(), 240) : null
            );
        });
    }

    public Page<AdminErrorRecord> getRecentErrors(UUID sessionId, UUID userId, String category,
                                                  String severity, Instant from, Instant to,
                                                  Pageable pageable) {
        Page<BuildError> errors = buildErrorRepository.findRecentWithFilters(
                sessionId, userId, category, severity, from, to, pageable);
        return errors.map(this::toRecord);
    }

    public List<AdminErrorRecord> getSessionErrors(UUID sessionId) {
        return buildErrorRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toRecord)
                .toList();
    }

    public BuildIteration recoverFailedBuild(UUID sessionId) {
        return failedBuildRecoveryService.adminRecover(sessionId);
    }

    /**
     * Refund the LLM tokens consumed by a single build session back to
     * the user's monthly pool. Idempotent — calling twice yields the
     * same {@code refundedAmount} and only debits the user once.
     *
     * <p>Note: REQUIRES_NEW because this class is read-only by default;
     * we need a writable transaction for the {@link TokenBudget} update
     * and the user's subscription decrement to commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TokenRefundResponse refundTokens(UUID sessionId) {
        if (buildSessionRepository.findById(sessionId).isEmpty()) {
            throw new NotFoundException("Build session not found");
        }
        TokenBudget before = tokenBudgetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Token budget not found"));
        boolean alreadyRefunded = before.getRefundedAt() != null;
        int amount = tokenBudgetService.refundSessionTokens(sessionId);
        TokenBudget after = tokenBudgetRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Token budget not found"));
        return new TokenRefundResponse(sessionId, amount, alreadyRefunded, after.getRefundedAt());
    }

    /**
     * Move a FAILED session back into an actionable state.
     *
     * <p>Routing depends on what artifacts exist:
     * <ul>
     *   <li>Plan + at least one iteration → re-run the implementation/
     *       compile pipeline via the existing recovery path. Same
     *       behaviour as {@link #recoverFailedBuild}.</li>
     *   <li>Plan but no iterations → reset to PLANNING/PLAN_REVIEW.
     *       This is the typical state of sessions killed by the old
     *       PLAN_REVIEW reaper bug: the plan is durable, the user just
     *       needs to come back and approve it.</li>
     *   <li>No plan → reset to CHATTING/CLARIFICATION so the user can
     *       resume the conversation that was in progress.</li>
     * </ul>
     *
     * <p>Tokens already consumed are NOT refunded by this call —
     * {@link #refundTokens} is a separate explicit action so an admin
     * can choose retrigger, refund, or both.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RetriggerResponse retriggerFailedBuild(UUID sessionId) {
        BuildSession session = buildSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Build session not found"));
        if (session.getStatus() != BuildStatus.FAILED
                && session.getStatus() != BuildStatus.CHATTING
                && session.getStatus() != BuildStatus.PLANNING) {
            throw new ValidationException(
                    "Session cannot be retriggered in current state (current: " + session.getStatus() + ")");
        }

        boolean hasPlan = planDocumentRepository.findBySessionId(sessionId).isPresent();
        long iterationCount = buildIterationRepository.countBySessionId(sessionId);

        if (hasPlan && iterationCount > 0) {
            // Existing pipeline-rerun path: delegate to FailedBuildRecoveryService
            // which creates a new iteration and kicks the async build worker.
            BuildIteration iteration = failedBuildRecoveryService.adminRecover(sessionId);
            return new RetriggerResponse(
                    sessionId,
                    "PIPELINE_RESTARTED",
                    BuildStatus.BUILDING.name(),
                    BuildPhase.IMPLEMENTATION.name(),
                    iteration.getIterationNumber()
            );
        }

        if (hasPlan) {
            // Plan exists but build never started. Hand the session back
            // to the user at the plan-review gate so they can approve.
            session.setCompletedAt(null);
            buildSessionRepository.save(session);
            buildSessionService.updateStatus(sessionId, BuildStatus.PLANNING);
            buildSessionService.updatePhase(sessionId, BuildPhase.PLAN_REVIEW);
            return new RetriggerResponse(
                    sessionId,
                    "RESET_TO_PLAN_REVIEW",
                    BuildStatus.PLANNING.name(),
                    BuildPhase.PLAN_REVIEW.name(),
                    null
            );
        }

        // No plan was ever generated — drop back into the chat phase so
        // the user can keep refining requirements.
        session.setCompletedAt(null);
        buildSessionRepository.save(session);
        buildSessionService.updateStatus(sessionId, BuildStatus.CHATTING);
        buildSessionService.updatePhase(sessionId, BuildPhase.CLARIFICATION);
        return new RetriggerResponse(
                sessionId,
                "RESET_TO_CLARIFICATION",
                BuildStatus.CHATTING.name(),
                BuildPhase.CLARIFICATION.name(),
                null
        );
    }

    public PlanDocumentDto getSessionPlan(UUID sessionId) {
        if (buildSessionRepository.findById(sessionId).isEmpty()) {
            throw new NotFoundException("Build session not found");
        }
        PlanDocument plan = planDocumentRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new NotFoundException("Plan not found for this session"));
        return toPlanDto(plan);
    }

    // ── Chat (admin viewer + send-as-user) ────────────────────────────

    /** Full conversation transcript for a session, oldest message first. */
    public List<ChatMessageDto> getSessionMessages(UUID sessionId) {
        if (buildSessionRepository.findById(sessionId).isEmpty()) {
            throw new NotFoundException("Build session not found");
        }
        return chatMessageService.getMessages(sessionId).stream()
                .map(m -> new ChatMessageDto(
                        m.getId(), m.getRole(), m.getContent(),
                        m.getModelUsed(), m.getTokensConsumed(), m.getCreatedAt()))
                .toList();
    }

    /**
     * Inject a message into a user's session as if the user sent it, so the
     * chatbot agent responds and the pipeline advances (e.g. nudging a stalled
     * clarification toward plan generation). The injected message is stored
     * with role {@code "user"} and the session owner's token budget is charged,
     * exactly like a real user message.
     *
     * <p>{@code NOT_SUPPORTED} suspends this class's read-only transaction:
     * {@link ChatbotAgent#handleMessage} is a long-running call (it hits the
     * Anthropic API and may take ~90s when it generates a plan) whose child
     * services each manage their own short write transactions — the same way
     * the non-transactional {@code ChatController} invokes it. Running it
     * inside the class-level read-only tx would fail those child writes.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AgentResponse sendMessageAsUser(UUID sessionId, String content) {
        BuildSession session = buildSessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("Build session not found"));
        if (session.getStatus() != BuildStatus.CHATTING
                && session.getStatus() != BuildStatus.PLANNING) {
            throw new ValidationException(
                    "Session is not in a chat-eligible state (current: " + session.getStatus()
                    + "). Only CHATTING or PLANNING sessions accept messages.");
        }
        log.info("Admin sending message as user {} for session {}", session.getUserId(), sessionId);
        return chatbotAgent.handleMessage(sessionId, session.getUserId(), content);
    }

    private PlanDocumentDto toPlanDto(PlanDocument plan) {
        return new PlanDocumentDto(
                plan.getId(),
                plan.getSessionId(),
                plan.getPluginName(),
                plan.getDescription(),
                plan.getMinecraftVersion(),
                plan.getServerType(),
                parsePlanJson(plan.getCommands(), new TypeReference<List<CommandSpec>>() {}),
                parsePlanJson(plan.getEventListeners(), new TypeReference<List<EventListenerSpec>>() {}),
                parsePlanJson(plan.getConfigSchema(), new TypeReference<List<ConfigEntry>>() {}),
                parsePlanJson(plan.getDependencies(), new TypeReference<List<DependencySpec>>() {}),
                parsePlanJson(plan.getTestScenarios(), new TypeReference<List<TestScenario>>() {}),
                plan.getEstimatedLoc(),
                plan.getComplexityScore(),
                plan.getVersion(),
                plan.getCreatedAt(),
                plan.getViabilityStatus(),
                parsePlanJson(plan.getSetupSteps(), new TypeReference<java.util.List<String>>() {}),
                parsePlanJson(plan.getAutoHandled(), new TypeReference<java.util.List<String>>() {})
        );
    }

    private <T> T parsePlanJson(String json, TypeReference<T> typeRef) {
        try {
            T result = objectMapper.readValue(json, typeRef);
            if (result != null) return result;
        } catch (Exception ignored) {}
        @SuppressWarnings("unchecked")
        T empty = (T) Collections.emptyList();
        return empty;
    }

    private AdminErrorRecord toRecord(BuildError e) {
        BuildSession session = buildSessionRepository.findById(e.getSessionId()).orElse(null);
        Integer iterationNumber = e.getIterationId() == null ? null
                : buildIterationRepository.findById(e.getIterationId())
                .map(BuildIteration::getIterationNumber).orElse(null);
        UUID uid = session != null ? session.getUserId() : null;
        String email = uid != null
                ? userRepository.findById(uid).map(User::getEmail).orElse("unknown")
                : "unknown";
        return new AdminErrorRecord(
                e.getId(), e.getSessionId(), e.getIterationId(), iterationNumber,
                uid, email, e.getCategory(), e.getSeverity(),
                e.getMessage(), e.getStackTrace(), e.getRetryCount(), e.getCreatedAt()
        );
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
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
