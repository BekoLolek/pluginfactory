package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.build.dto.SourceCodeRequestDto;
import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.subscription.Subscription;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceCodeRequestServiceTest {

    @Mock
    private SourceCodeRequestRepository sourceCodeRequestRepository;

    @Mock
    private ArtifactRepository artifactRepository;

    @Mock
    private SourceBundleRepository sourceBundleRepository;

    @Mock
    private SubscriptionService subscriptionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WatermarkService watermarkService;

    private SourceCodeRequestService sourceCodeRequestService;

    @BeforeEach
    void setUp() {
        sourceCodeRequestService = new SourceCodeRequestService(
                sourceCodeRequestRepository,
                artifactRepository,
                sourceBundleRepository,
                subscriptionService,
                userRepository,
                watermarkService,
                "data/artifacts"
        );
    }

    @Test
    void requestSourceCode_freeUser_forbidden() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.FREE);

        assertThatThrownBy(() -> sourceCodeRequestService.requestSourceCode(
                userId, artifactId, "1.0", "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Source code access requires PRO or TEAM subscription");
    }

    @Test
    void requestSourceCode_basicUser_forbidden() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.BASIC);

        assertThatThrownBy(() -> sourceCodeRequestService.requestSourceCode(
                userId, artifactId, "1.0", "127.0.0.1"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Source code access requires PRO or TEAM subscription");
    }

    @Test
    void requestSourceCode_proUser_success() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.PRO);

        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setSessionId(UUID.randomUUID());
        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact));

        User user = new User();
        user.setId(userId);
        user.setEmail("test@test.com");
        user.setDisplayName("Test");
        user.setAuthProvider("discord");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(sourceCodeRequestRepository.save(any(SourceCodeRequest.class)))
                .thenAnswer(invocation -> {
                    SourceCodeRequest req = invocation.getArgument(0);
                    if (req.getId() == null) {
                        req.setId(UUID.randomUUID());
                    }
                    if (req.getRequestedAt() == null) {
                        req.setRequestedAt(java.time.Instant.now());
                    }
                    return req;
                });

        SourceCodeRequestDto result = sourceCodeRequestService.requestSourceCode(
                userId, artifactId, "1.0", "127.0.0.1");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.artifactId()).isEqualTo(artifactId);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.licenseVersion()).isEqualTo("1.0");
        assertThat(result.watermarkId()).isNotNull();
    }

    @Test
    void requestSourceCode_teamUser_success() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.TEAM);

        Artifact artifact = new Artifact();
        artifact.setId(artifactId);
        artifact.setSessionId(UUID.randomUUID());
        when(artifactRepository.findById(artifactId)).thenReturn(Optional.of(artifact));

        User user = new User();
        user.setId(userId);
        user.setEmail("team@test.com");
        user.setDisplayName("Team User");
        user.setAuthProvider("discord");
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(sourceCodeRequestRepository.save(any(SourceCodeRequest.class)))
                .thenAnswer(invocation -> {
                    SourceCodeRequest req = invocation.getArgument(0);
                    if (req.getId() == null) {
                        req.setId(UUID.randomUUID());
                    }
                    if (req.getRequestedAt() == null) {
                        req.setRequestedAt(java.time.Instant.now());
                    }
                    return req;
                });

        SourceCodeRequestDto result = sourceCodeRequestService.requestSourceCode(
                userId, artifactId, "1.0", "192.168.1.1");

        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.artifactId()).isEqualTo(artifactId);
        assertThat(result.status()).isEqualTo("PENDING");
    }

    @Test
    void requestSourceCode_artifactNotFound() {
        UUID userId = UUID.randomUUID();
        UUID artifactId = UUID.randomUUID();

        when(subscriptionService.getTierForUser(userId)).thenReturn(Tier.PRO);
        when(artifactRepository.findById(artifactId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sourceCodeRequestService.requestSourceCode(
                userId, artifactId, "1.0", "127.0.0.1"))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Artifact not found");
    }

    @Test
    void downloadSourceCode_wrongUser_forbidden() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        User otherUser = new User();
        otherUser.setId(otherUserId);

        SourceCodeRequest request = new SourceCodeRequest();
        request.setId(requestId);
        request.setUser(otherUser);
        request.setStatus("FULFILLED");
        request.setDownloadPath("some/path.zip");

        when(sourceCodeRequestRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> sourceCodeRequestService.downloadSourceCode(requestId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Access denied to source code request");
    }

    @Test
    void downloadSourceCode_notFound() {
        UUID requestId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(sourceCodeRequestRepository.findById(requestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sourceCodeRequestService.downloadSourceCode(requestId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Source code request not found");
    }
}
