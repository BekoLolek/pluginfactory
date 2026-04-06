package com.bekololek.pluginfactory.team;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.team.dto.TeamDto;
import com.bekololek.pluginfactory.team.dto.TeamMemberDto;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private TeamService teamService;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void createTeam_success() {
        User owner = createUser(ownerId, "owner@test.com", "Owner");

        Team savedTeam = new Team();
        savedTeam.setId(teamId);
        savedTeam.setName("Test Team");
        savedTeam.setOwner(owner);
        savedTeam.setMaxMembers(10);
        savedTeam.setCreatedAt(Instant.now());

        when(userRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(teamRepository.save(any(Team.class))).thenReturn(savedTeam);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamDto result = teamService.createTeam(ownerId, "Test Team");

        assertThat(result.name()).isEqualTo("Test Team");
        assertThat(result.ownerId()).isEqualTo(ownerId);
        assertThat(result.memberCount()).isEqualTo(1);

        ArgumentCaptor<TeamMember> memberCaptor = ArgumentCaptor.forClass(TeamMember.class);
        verify(teamMemberRepository).save(memberCaptor.capture());
        assertThat(memberCaptor.getValue().getRole()).isEqualTo(TeamRole.OWNER);
    }

    @Test
    void createTeam_userNotFound_throwsNotFoundException() {
        when(userRepository.findById(ownerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.createTeam(ownerId, "Test Team"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void getTeam_success() {
        Team team = createTeam(teamId, "Test Team");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(true);
        when(teamMemberRepository.findByTeam_Id(teamId)).thenReturn(List.of(new TeamMember()));

        TeamDto result = teamService.getTeam(teamId, userId);

        assertThat(result.name()).isEqualTo("Test Team");
        assertThat(result.memberCount()).isEqualTo(1);
    }

    @Test
    void getTeam_notFound_throwsNotFoundException() {
        when(teamRepository.findById(teamId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeam(teamId, userId))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Team not found");
    }

    @Test
    void getUserTeams_returnsList() {
        Team team1 = createTeam(UUID.randomUUID(), "Team 1");
        Team team2 = createTeam(UUID.randomUUID(), "Team 2");

        when(teamRepository.findTeamsByMemberId(userId)).thenReturn(List.of(team1, team2));
        when(teamMemberRepository.findByTeam_Id(team1.getId())).thenReturn(List.of(new TeamMember()));
        when(teamMemberRepository.findByTeam_Id(team2.getId())).thenReturn(List.of(new TeamMember(), new TeamMember()));

        List<TeamDto> result = teamService.getUserTeams(userId);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).name()).isEqualTo("Team 1");
        assertThat(result.get(1).name()).isEqualTo("Team 2");
    }

    @Test
    void addMember_success() {
        Team team = createTeam(teamId, "Test Team");
        User newUser = createUser(userId, "new@test.com", "NewUser");

        TeamMember requesterMember = createTeamMember(teamId, ownerId, TeamRole.OWNER);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(requesterMember));
        when(teamMemberRepository.findByTeam_Id(teamId)).thenReturn(List.of(requesterMember));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(newUser));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> {
            TeamMember m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setJoinedAt(Instant.now());
            m.setUser(newUser);
            return m;
        });

        TeamMemberDto result = teamService.addMember(teamId, ownerId, userId, TeamRole.MEMBER);

        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.role()).isEqualTo(TeamRole.MEMBER);
    }

    @Test
    void addMember_maxMembersExceeded_throwsValidationException() {
        Team team = createTeam(teamId, "Test Team");
        team.setMaxMembers(1);

        TeamMember requesterMember = createTeamMember(teamId, ownerId, TeamRole.OWNER);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(requesterMember));
        when(teamMemberRepository.findByTeam_Id(teamId)).thenReturn(List.of(requesterMember));

        assertThatThrownBy(() -> teamService.addMember(teamId, ownerId, userId, TeamRole.MEMBER))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("maximum member limit");
    }

    @Test
    void addMember_nonAdminBlocked_throwsForbiddenException() {
        Team team = createTeam(teamId, "Test Team");

        TeamMember requesterMember = createTeamMember(teamId, ownerId, TeamRole.MEMBER);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(requesterMember));

        assertThatThrownBy(() -> teamService.addMember(teamId, ownerId, userId, TeamRole.MEMBER))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only OWNER or ADMIN");
    }

    @Test
    void addMember_adminCanAdd() {
        Team team = createTeam(teamId, "Test Team");
        User newUser = createUser(userId, "new@test.com", "NewUser");

        TeamMember requesterMember = createTeamMember(teamId, ownerId, TeamRole.ADMIN);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(requesterMember));
        when(teamMemberRepository.findByTeam_Id(teamId)).thenReturn(List.of(requesterMember));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(newUser));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> {
            TeamMember m = inv.getArgument(0);
            m.setId(UUID.randomUUID());
            m.setJoinedAt(Instant.now());
            m.setUser(newUser);
            return m;
        });

        TeamMemberDto result = teamService.addMember(teamId, ownerId, userId, TeamRole.MEMBER);

        assertThat(result.userId()).isEqualTo(userId);
    }

    @Test
    void addMember_alreadyMember_throwsValidationException() {
        Team team = createTeam(teamId, "Test Team");

        TeamMember requesterMember = createTeamMember(teamId, ownerId, TeamRole.OWNER);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(requesterMember));
        when(teamMemberRepository.findByTeam_Id(teamId)).thenReturn(List.of(requesterMember));
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(true);

        assertThatThrownBy(() -> teamService.addMember(teamId, ownerId, userId, TeamRole.MEMBER))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already a team member");
    }

    @Test
    void removeMember_success() {
        TeamMember requesterMember = createTeamMember(teamId, ownerId, TeamRole.OWNER);
        TeamMember targetMember = createTeamMember(teamId, userId, TeamRole.MEMBER);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(createTeam(teamId, "Test Team")));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(requesterMember));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)).thenReturn(Optional.of(targetMember));

        teamService.removeMember(teamId, ownerId, userId);

        verify(teamMemberRepository).delete(targetMember);
    }

    @Test
    void removeMember_cannotRemoveOwner_throwsForbiddenException() {
        UUID anotherAdminId = UUID.randomUUID();
        TeamMember requesterMember = createTeamMember(teamId, anotherAdminId, TeamRole.ADMIN);
        TeamMember ownerMember = createTeamMember(teamId, ownerId, TeamRole.OWNER);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(createTeam(teamId, "Test Team")));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, anotherAdminId)).thenReturn(Optional.of(requesterMember));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(ownerMember));

        assertThatThrownBy(() -> teamService.removeMember(teamId, anotherAdminId, ownerId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Cannot remove the team owner");

        verify(teamMemberRepository, never()).delete(any());
    }

    @Test
    void updateMemberRole_ownerSuccess() {
        User targetUser = createUser(userId, "target@test.com", "Target");
        TeamMember requesterMember = createTeamMember(teamId, ownerId, TeamRole.OWNER);
        TeamMember targetMember = createTeamMember(teamId, userId, TeamRole.MEMBER);
        targetMember.setUser(targetUser);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(createTeam(teamId, "Test Team")));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, ownerId)).thenReturn(Optional.of(requesterMember));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)).thenReturn(Optional.of(targetMember));
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamMemberDto result = teamService.updateMemberRole(teamId, ownerId, userId, TeamRole.ADMIN);

        assertThat(result.role()).isEqualTo(TeamRole.ADMIN);
    }

    @Test
    void updateMemberRole_nonOwnerBlocked() {
        UUID adminId = UUID.randomUUID();
        TeamMember requesterMember = createTeamMember(teamId, adminId, TeamRole.ADMIN);

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(createTeam(teamId, "Test Team")));
        when(teamMemberRepository.findByTeam_IdAndUser_Id(teamId, adminId)).thenReturn(Optional.of(requesterMember));

        assertThatThrownBy(() -> teamService.updateMemberRole(teamId, adminId, userId, TeamRole.ADMIN))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only OWNER can update member roles");
    }

    @Test
    void deleteTeam_ownerSuccess() {
        Team team = createTeam(teamId, "Test Team");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        teamService.deleteTeam(teamId, ownerId);

        verify(teamRepository).delete(team);
    }

    @Test
    void deleteTeam_nonOwnerBlocked() {
        Team team = createTeam(teamId, "Test Team");

        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));

        assertThatThrownBy(() -> teamService.deleteTeam(teamId, userId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("Only the team owner");

        verify(teamRepository, never()).delete(any());
    }

    @Test
    void isMember_returnsTrue() {
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(true);

        assertThat(teamService.isMember(teamId, userId)).isTrue();
    }

    @Test
    void isMember_returnsFalse() {
        when(teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)).thenReturn(false);

        assertThat(teamService.isMember(teamId, userId)).isFalse();
    }

    private User createUser(UUID id, String email, String displayName) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setDisplayName(displayName);
        return user;
    }

    private Team createTeam(UUID id, String name) {
        User owner = createUser(ownerId, "owner@test.com", "Owner");
        Team team = new Team();
        team.setId(id);
        team.setName(name);
        team.setOwner(owner);
        team.setMaxMembers(10);
        team.setCreatedAt(Instant.now());
        return team;
    }

    private TeamMember createTeamMember(UUID teamId, UUID memberId, TeamRole role) {
        TeamMember member = new TeamMember();
        member.setId(UUID.randomUUID());
        member.setRole(role);
        User user = createUser(memberId, memberId + "@test.com", "User-" + memberId);
        member.setUser(user);
        return member;
    }
}
