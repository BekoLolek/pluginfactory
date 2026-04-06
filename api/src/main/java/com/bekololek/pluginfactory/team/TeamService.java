package com.bekololek.pluginfactory.team;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.common.exception.ValidationException;
import com.bekololek.pluginfactory.team.dto.TeamDto;
import com.bekololek.pluginfactory.team.dto.TeamMemberDto;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public TeamDto createTeam(UUID ownerId, String name) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        Team team = new Team();
        team.setName(name);
        team.setOwner(owner);

        team = teamRepository.save(team);

        TeamMember ownerMember = new TeamMember();
        ownerMember.setTeam(team);
        ownerMember.setUser(owner);
        ownerMember.setRole(TeamRole.OWNER);
        teamMemberRepository.save(ownerMember);

        return toDto(team, 1);
    }

    @Transactional(readOnly = true)
    public TeamDto getTeam(UUID teamId, UUID userId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));
        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)) {
            throw new ForbiddenException("Not a team member");
        }
        int memberCount = teamMemberRepository.findByTeam_Id(teamId).size();
        return toDto(team, memberCount);
    }

    @Transactional(readOnly = true)
    public List<TeamDto> getUserTeams(UUID userId) {
        List<Team> teams = teamRepository.findTeamsByMemberId(userId);
        return teams.stream()
                .map(team -> {
                    int memberCount = teamMemberRepository.findByTeam_Id(team.getId()).size();
                    return toDto(team, memberCount);
                })
                .toList();
    }

    @Transactional
    public TeamMemberDto addMember(UUID teamId, UUID requesterId, UUID userId, TeamRole role) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));

        TeamMember requester = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, requesterId)
                .orElseThrow(() -> new ForbiddenException("Not a team member"));

        if (requester.getRole() != TeamRole.OWNER && requester.getRole() != TeamRole.ADMIN) {
            throw new ForbiddenException("Only OWNER or ADMIN can add members");
        }

        int currentMemberCount = teamMemberRepository.findByTeam_Id(teamId).size();
        if (currentMemberCount >= team.getMaxMembers()) {
            throw new ValidationException("Team has reached maximum member limit");
        }

        if (teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)) {
            throw new ValidationException("User is already a team member");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        TeamMember member = new TeamMember();
        member.setTeam(team);
        member.setUser(user);
        member.setRole(role);
        member = teamMemberRepository.save(member);

        return toMemberDto(member);
    }

    @Transactional
    public void removeMember(UUID teamId, UUID requesterId, UUID userId) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));

        TeamMember requester = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, requesterId)
                .orElseThrow(() -> new ForbiddenException("Not a team member"));

        if (requester.getRole() != TeamRole.OWNER && requester.getRole() != TeamRole.ADMIN) {
            throw new ForbiddenException("Only OWNER or ADMIN can remove members");
        }

        TeamMember target = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        if (target.getRole() == TeamRole.OWNER) {
            throw new ForbiddenException("Cannot remove the team owner");
        }

        teamMemberRepository.delete(target);
    }

    @Transactional
    public TeamMemberDto updateMemberRole(UUID teamId, UUID requesterId, UUID userId, TeamRole role) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));

        TeamMember requester = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, requesterId)
                .orElseThrow(() -> new ForbiddenException("Not a team member"));

        if (requester.getRole() != TeamRole.OWNER) {
            throw new ForbiddenException("Only OWNER can update member roles");
        }

        TeamMember target = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)
                .orElseThrow(() -> new NotFoundException("Member not found"));

        target.setRole(role);
        target = teamMemberRepository.save(target);

        return toMemberDto(target);
    }

    @Transactional
    public void deleteTeam(UUID teamId, UUID requesterId) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));

        if (!team.getOwner().getId().equals(requesterId)) {
            throw new ForbiddenException("Only the team owner can delete the team");
        }

        teamRepository.delete(team);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID teamId, UUID userId) {
        return teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId);
    }

    private TeamDto toDto(Team team, int memberCount) {
        return new TeamDto(
                team.getId(),
                team.getName(),
                team.getOwner().getId(),
                team.getMaxMembers(),
                memberCount,
                team.getCreatedAt()
        );
    }

    private TeamMemberDto toMemberDto(TeamMember member) {
        User user = member.getUser();
        return new TeamMemberDto(
                member.getId(),
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                member.getRole(),
                member.getJoinedAt()
        );
    }
}
