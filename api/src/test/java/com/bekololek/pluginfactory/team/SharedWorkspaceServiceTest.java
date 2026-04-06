package com.bekololek.pluginfactory.team;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.team.dto.SharedWorkspaceDto;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SharedWorkspaceServiceTest {

    @Mock
    private SharedWorkspaceRepository sharedWorkspaceRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SharedWorkspaceService sharedWorkspaceService;

    private final UUID teamId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID workspaceId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @Test
    void createWorkspace_memberAllowed() {
        Team team = createTeam();
        User user = createUser(userId, "member@test.com", "Member");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(true);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(sharedWorkspaceRepository.save(any(SharedWorkspace.class))).thenAnswer(inv -> {
            SharedWorkspace ws = inv.getArgument(0);
            ws.setId(workspaceId);
            ws.setCreatedAt(Instant.now());
            return ws;
        });

        SharedWorkspaceDto result = sharedWorkspaceService.createWorkspace(teamId, userId, "My Workspace", "A description");

        assertThat(result.name()).isEqualTo("My Workspace");
        assertThat(result.description()).isEqualTo("A description");
        assertThat(result.teamId()).isEqualTo(teamId);
        assertThat(result.createdById()).isEqualTo(userId);
    }

    @Test
    void createWorkspace_nonMemberBlocked() {
        Team team = createTeam();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(false);

        assertThatThrownBy(() -> sharedWorkspaceService.createWorkspace(teamId, userId, "My Workspace", "Desc"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only team members");
    }

    @Test
    void createWorkspace_teamNotFound() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sharedWorkspaceService.createWorkspace(teamId, userId, "Name", "Desc"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Team not found");
    }

    @Test
    void getWorkspace_success() {
        SharedWorkspace workspace = createWorkspace();

        when(sharedWorkspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        SharedWorkspaceDto result = sharedWorkspaceService.getWorkspace(workspaceId);

        assertThat(result.name()).isEqualTo("Test Workspace");
        assertThat(result.id()).isEqualTo(workspaceId);
    }

    @Test
    void getWorkspace_notFound() {
        when(sharedWorkspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sharedWorkspaceService.getWorkspace(workspaceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Workspace not found");
    }

    @Test
    void getTeamWorkspaces_memberAllowed() {
        Team team = createTeam();
        SharedWorkspace workspace = createWorkspace();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(true);
        when(sharedWorkspaceRepository.findByTeam_Id(teamId)).thenReturn(List.of(workspace));

        List<SharedWorkspaceDto> result = sharedWorkspaceService.getTeamWorkspaces(teamId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Test Workspace");
    }

    @Test
    void getTeamWorkspaces_nonMemberBlocked() {
        Team team = createTeam();

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(false);

        assertThatThrownBy(() -> sharedWorkspaceService.getTeamWorkspaces(teamId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only team members");
    }

    @Test
    void deleteWorkspace_creatorAllowed() {
        SharedWorkspace workspace = createWorkspace();
        // Set creator as the current user
        workspace.getCreatedBy().setId(userId);

        when(sharedWorkspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        sharedWorkspaceService.deleteWorkspace(workspaceId, userId);

        verify(sharedWorkspaceRepository).delete(workspace);
    }

    @Test
    void deleteWorkspace_adminAllowed() {
        SharedWorkspace workspace = createWorkspace();
        // Creator is different from requester
        UUID creatorId = UUID.randomUUID();
        workspace.getCreatedBy().setId(creatorId);

        TeamMember adminMember = new TeamMember();
        adminMember.setRole(TeamRole.ADMIN);

        when(sharedWorkspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)).thenReturn(Optional.of(adminMember));

        sharedWorkspaceService.deleteWorkspace(workspaceId, userId);

        verify(sharedWorkspaceRepository).delete(workspace);
    }

    @Test
    void deleteWorkspace_ownerAllowed() {
        SharedWorkspace workspace = createWorkspace();
        UUID creatorId = UUID.randomUUID();
        workspace.getCreatedBy().setId(creatorId);

        TeamMember ownerMember = new TeamMember();
        ownerMember.setRole(TeamRole.OWNER);

        when(sharedWorkspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)).thenReturn(Optional.of(ownerMember));

        sharedWorkspaceService.deleteWorkspace(workspaceId, userId);

        verify(sharedWorkspaceRepository).delete(workspace);
    }

    @Test
    void deleteWorkspace_regularMemberBlocked() {
        SharedWorkspace workspace = createWorkspace();
        UUID creatorId = UUID.randomUUID();
        workspace.getCreatedBy().setId(creatorId);

        TeamMember memberMember = new TeamMember();
        memberMember.setRole(TeamRole.MEMBER);

        when(sharedWorkspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)).thenReturn(Optional.of(memberMember));

        assertThatThrownBy(() -> sharedWorkspaceService.deleteWorkspace(workspaceId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("creator, team owner, or admin");

        verify(sharedWorkspaceRepository, never()).delete(any());
    }

    @Test
    void deleteWorkspace_nonMemberBlocked() {
        SharedWorkspace workspace = createWorkspace();
        UUID creatorId = UUID.randomUUID();
        workspace.getCreatedBy().setId(creatorId);

        when(sharedWorkspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sharedWorkspaceService.deleteWorkspace(workspaceId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Not a team member");
    }

    private User createUser(UUID id, String email, String displayName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(displayName);
        return user;
    }

    private Team createTeam() {
        User owner = createUser(ownerId, "owner@test.com", "Owner");
        Team team = new Team();
        team.setId(teamId);
        team.setName("Test Team");
        team.setOwner(owner);
        team.setMaxMembers(10);
        team.setCreatedAt(Instant.now());
        return team;
    }

    private SharedWorkspace createWorkspace() {
        Team team = createTeam();
        User creator = createUser(UUID.randomUUID(), "creator@test.com", "Creator");

        SharedWorkspace workspace = new SharedWorkspace();
        workspace.setId(workspaceId);
        workspace.setName("Test Workspace");
        workspace.setDescription("Test description");
        workspace.setTeam(team);
        workspace.setCreatedBy(creator);
        workspace.setCreatedAt(Instant.now());
        workspace.setUpdatedAt(Instant.now());
        return workspace;
    }
}
