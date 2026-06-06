package com.bekololek.pluginfactory.build;

import com.bekololek.pluginfactory.agent.ImplementerAgent;
import com.bekololek.pluginfactory.agent.dto.ImplementationResult;
import com.bekololek.pluginfactory.container.ContainerPoolManager;
import com.bekololek.pluginfactory.container.ContainerSessionRepository;
import com.bekololek.pluginfactory.container.DockerService;
import com.bekololek.pluginfactory.container.ExecResult;
import com.bekololek.pluginfactory.agent.FunctionalTestAgent;
import com.bekololek.pluginfactory.container.FunctionalTestService;
import com.bekololek.pluginfactory.container.TestServerService;
import com.bekololek.pluginfactory.email.EmailNotificationService;
import com.bekololek.pluginfactory.subscription.SubscriptionService;
import com.bekololek.pluginfactory.subscription.Tier;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BuildPipelineServiceTest {

    @Mock
    private ImplementerAgent implementerAgent;

    @Mock
    private DockerService dockerService;

    @Mock
    private ContainerPoolManager containerPoolManager;

    @Mock
    private BuildSessionService buildSessionService;

    @Mock
    private BuildProgressService buildProgressService;

    @Mock
    private TokenBudgetService tokenBudgetService;

    @Mock
    private ArtifactService artifactService;

    @Mock
    private SecurityScanService securityScanService;

    @Mock
    private BuildIterationRepository buildIterationRepository;

    @Mock
    private BuildErrorRepository buildErrorRepository;

    @Mock
    private ContainerSessionRepository containerSessionRepository;

    @Mock
    private ErrorClassifier errorClassifier;

    @Mock
    private RetryPolicy retryPolicy;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private TestServerService testServerService;

    @Mock
    private FunctionalTestAgent functionalTestAgent;

    @Mock
    private FunctionalTestService functionalTestService;

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private BuildPipelineService buildPipelineService;

    private UUID sessionId;

    @BeforeEach
    void setUp() {
        sessionId = UUID.randomUUID();
    }

    /** A real tar of target/ containing a shaded jar, as the build container returns. */
    private byte[] tarWithJar() {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             TarArchiveOutputStream tar = new TarArchiveOutputStream(baos)) {
            byte[] jar = "PK fake jar bytes".getBytes();
            TarArchiveEntry entry = new TarArchiveEntry("target/testplugin-1.0.0.jar");
            entry.setSize(jar.length);
            tar.putArchiveEntry(entry);
            tar.write(jar);
            tar.closeArchiveEntry();
            tar.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void executeBuild_successFlow() {
        // Arrange
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pom.xml", "<project/>");
        files.put("src/main/resources/plugin.yml", "name: Test");
        files.put("src/main/java/com/bekololek/generated/TestPlugin.java",
                "package com.bekololek.generated; public class TestPlugin {}");

        ImplementationResult implResult = new ImplementationResult(files, 500);
        when(implementerAgent.implement(sessionId)).thenReturn(implResult);

        UUID iterationId = UUID.randomUUID();
        BuildIteration stubIteration = new BuildIteration();
        stubIteration.setId(iterationId);
        stubIteration.setSessionId(sessionId);
        stubIteration.setIterationNumber(1);
        stubIteration.setStatus("RUNNING");
        when(buildIterationRepository.findById(iterationId)).thenReturn(Optional.of(stubIteration));
        when(buildIterationRepository.save(any(BuildIteration.class))).thenAnswer(inv -> inv.getArgument(0));

        String containerId = "container-123";
        when(containerPoolManager.claimContainer(DockerService.ContainerType.BUILD)).thenReturn(containerId);
        when(containerSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(dockerService.executeCommand(eq(containerId), any(), any(), any()))
                .thenReturn(new ExecResult(0, "BUILD SUCCESS", ""));
        when(dockerService.copyFromContainer(eq(containerId), anyString()))
                .thenReturn(tarWithJar());

        when(securityScanService.scanSource(anyString()))
                .thenReturn(new SecurityScanResult(true, Collections.emptyList()));
        when(testServerService.runSmokeTest(any(), any()))
                .thenReturn(new TestServerService.SmokeResult(true, "enabled cleanly"));

        // Owner + tier lookup for the functional-test gate. FREE tier skips
        // functional testing, keeping this test focused on the deliver path.
        BuildSession ownerSession = new BuildSession();
        ownerSession.setUserId(UUID.randomUUID());
        when(buildSessionService.getSessionById(sessionId)).thenReturn(ownerSession);
        when(subscriptionService.getTierForUser(any())).thenReturn(Tier.FREE);

        when(buildSessionService.updatePhase(any(), any())).thenReturn(new BuildSession());
        when(buildSessionService.updateStatus(any(), any())).thenReturn(new BuildSession());
        when(artifactService.storeArtifact(any(), any(), any(), any(), any(), eq(true)))
                .thenReturn(new Artifact());

        // Act
        buildPipelineService.executeBuild(sessionId, iterationId);

        // Assert - verify all phases were traversed
        verify(buildSessionService).updatePhase(sessionId, BuildPhase.IMPLEMENTATION);
        verify(buildSessionService).updatePhase(sessionId, BuildPhase.COMPILATION);
        verify(buildSessionService).updatePhase(sessionId, BuildPhase.SECURITY_SCAN);
        verify(buildSessionService).updatePhase(sessionId, BuildPhase.DELIVERING);

        // Verify progress notifications
        verify(buildProgressService).notifyPhaseChange(sessionId, BuildPhase.IMPLEMENTATION);
        verify(buildProgressService).notifyPhaseChange(sessionId, BuildPhase.COMPILATION);
        verify(buildProgressService).notifyPhaseChange(sessionId, BuildPhase.SECURITY_SCAN);
        verify(buildProgressService).notifyPhaseChange(sessionId, BuildPhase.DELIVERING);

        // Verify completion
        verify(buildSessionService).updateStatus(sessionId, BuildStatus.COMPLETED);
        verify(buildProgressService).notifyStatusChange(sessionId, BuildStatus.COMPLETED);

        // Verify artifact stored
        verify(artifactService).storeArtifact(eq(sessionId), any(), any(), any(), any(), eq(true));

        // Verify tokens consumed
        verify(tokenBudgetService).consumeTokens(sessionId, "implementation", 500);

        // Verify container released
        verify(containerPoolManager).releaseContainer(containerId, DockerService.ContainerType.BUILD);

        // Verify no errors recorded
        verify(buildErrorRepository, never()).save(any());
    }

    @Test
    void executeBuild_compilationFailure() {
        // Arrange
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pom.xml", "<project/>");
        files.put("src/main/java/com/bekololek/generated/Test.java", "invalid java");

        ImplementationResult implResult = new ImplementationResult(files, 300);
        when(implementerAgent.implement(sessionId)).thenReturn(implResult);

        UUID iterationId = UUID.randomUUID();
        BuildIteration stubIteration = new BuildIteration();
        stubIteration.setId(iterationId);
        stubIteration.setSessionId(sessionId);
        stubIteration.setIterationNumber(1);
        stubIteration.setStatus("RUNNING");
        when(buildIterationRepository.findById(iterationId)).thenReturn(Optional.of(stubIteration));
        when(buildIterationRepository.save(any(BuildIteration.class))).thenAnswer(inv -> inv.getArgument(0));

        String containerId = "container-456";
        when(containerPoolManager.claimContainer(DockerService.ContainerType.BUILD)).thenReturn(containerId);
        when(containerSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(dockerService.executeCommand(eq(containerId), any(), any(), any()))
                .thenReturn(new ExecResult(1, "", "COMPILATION ERROR: invalid syntax"));

        when(buildSessionService.updatePhase(any(), any())).thenReturn(new BuildSession());
        when(buildSessionService.updateStatus(any(), any())).thenReturn(new BuildSession());
        when(buildErrorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(buildErrorRepository.findByIterationId(any())).thenReturn(Collections.emptyList());
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorCategory.STRUCTURAL);
        when(retryPolicy.shouldRetry(any(), any(), eq(0))).thenReturn(false);

        // Act
        buildPipelineService.executeBuild(sessionId, iterationId);

        // Assert - verify error handling
        verify(buildSessionService).updateStatus(sessionId, BuildStatus.FAILED);
        verify(buildProgressService).notifyStatusChange(sessionId, BuildStatus.FAILED);

        // Verify error recorded
        ArgumentCaptor<BuildError> errorCaptor = ArgumentCaptor.forClass(BuildError.class);
        verify(buildErrorRepository).save(errorCaptor.capture());
        BuildError error = errorCaptor.getValue();
        assertThat(error.getCategory()).isEqualTo("STRUCTURAL");
        assertThat(error.getSeverity()).isEqualTo("ERROR");

        // Verify artifact NOT stored
        verify(artifactService, never()).storeArtifact(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void executeBuild_securityFailure() {
        // Arrange
        Map<String, String> files = new LinkedHashMap<>();
        files.put("pom.xml", "<project/>");
        files.put("src/main/java/com/bekololek/generated/Evil.java",
                "Runtime.getRuntime().exec(\"evil\");");

        ImplementationResult implResult = new ImplementationResult(files, 400);
        when(implementerAgent.implement(sessionId)).thenReturn(implResult);

        UUID iterationId = UUID.randomUUID();
        BuildIteration stubIteration = new BuildIteration();
        stubIteration.setId(iterationId);
        stubIteration.setSessionId(sessionId);
        stubIteration.setIterationNumber(1);
        stubIteration.setStatus("RUNNING");
        when(buildIterationRepository.findById(iterationId)).thenReturn(Optional.of(stubIteration));
        when(buildIterationRepository.save(any(BuildIteration.class))).thenAnswer(inv -> inv.getArgument(0));

        String containerId = "container-789";
        when(containerPoolManager.claimContainer(DockerService.ContainerType.BUILD)).thenReturn(containerId);
        when(containerSessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        when(dockerService.executeCommand(eq(containerId), any(), any(), any()))
                .thenReturn(new ExecResult(0, "BUILD SUCCESS", ""));
        when(dockerService.copyFromContainer(eq(containerId), anyString()))
                .thenReturn(tarWithJar());

        when(securityScanService.scanSource(anyString()))
                .thenReturn(new SecurityScanResult(false, java.util.List.of("Runtime.exec() detected")));

        when(buildSessionService.updatePhase(any(), any())).thenReturn(new BuildSession());
        when(buildSessionService.updateStatus(any(), any())).thenReturn(new BuildSession());
        when(buildErrorRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(buildErrorRepository.findByIterationId(any())).thenReturn(Collections.emptyList());
        when(errorClassifier.classify(any())).thenReturn(ErrorClassifier.ErrorCategory.SECURITY);
        when(retryPolicy.shouldRetry(any(), eq(ErrorClassifier.ErrorCategory.SECURITY), eq(0))).thenReturn(false);

        // Act
        buildPipelineService.executeBuild(sessionId, iterationId);

        // Assert - verify security failure handling
        verify(buildSessionService).updateStatus(sessionId, BuildStatus.FAILED);

        ArgumentCaptor<BuildError> errorCaptor = ArgumentCaptor.forClass(BuildError.class);
        verify(buildErrorRepository).save(errorCaptor.capture());
        BuildError error = errorCaptor.getValue();
        assertThat(error.getCategory()).isEqualTo("SECURITY");

        // Verify artifact NOT stored
        verify(artifactService, never()).storeArtifact(any(), any(), any(), any(), any(), any(Boolean.class));
    }

    @Test
    void createTarArchive_producesValidBytes() {
        Map<String, String> files = Map.of(
                "test.txt", "hello world",
                "dir/nested.txt", "nested content"
        );

        byte[] tar = buildPipelineService.createTarArchive(files);

        assertThat(tar).isNotNull();
        assertThat(tar.length).isGreaterThan(0);
    }

    @Test
    void createSourceZip_producesValidBytes() {
        Map<String, String> files = Map.of(
                "test.txt", "hello world",
                "dir/nested.txt", "nested content"
        );

        byte[] zip = buildPipelineService.createSourceZip(files);

        assertThat(zip).isNotNull();
        assertThat(zip.length).isGreaterThan(0);
    }

    @Test
    void extractPluginYml_found() {
        Map<String, String> files = Map.of(
                "pom.xml", "<project/>",
                "src/main/resources/plugin.yml", "name: TestPlugin"
        );

        String yml = buildPipelineService.extractPluginYml(files);

        assertThat(yml).isEqualTo("name: TestPlugin");
    }

    @Test
    void extractPluginYml_notFound() {
        Map<String, String> files = Map.of("pom.xml", "<project/>");

        String yml = buildPipelineService.extractPluginYml(files);

        assertThat(yml).isNull();
    }
}
