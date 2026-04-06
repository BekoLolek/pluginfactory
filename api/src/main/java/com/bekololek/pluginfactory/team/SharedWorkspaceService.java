package com.bekololek.pluginfactory.team;

import com.bekololek.pluginfactory.common.exception.ForbiddenException;
import com.bekololek.pluginfactory.common.exception.NotFoundException;
import com.bekololek.pluginfactory.team.dto.SharedWorkspaceDto;
import com.bekololek.pluginfactory.user.User;
import com.bekololek.pluginfactory.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SharedWorkspaceService {

    private final SharedWorkspaceRepository sharedWorkspaceRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRepository userRepository;

    @Transactional
    public SharedWorkspaceDto createWorkspace(UUID teamId, UUID userId, String name, String description) {
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));

        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)) {
            throw new ForbiddenException("Only team members can create workspaces");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        SharedWorkspace workspace = new SharedWorkspace();
        workspace.setName(name);
        workspace.setDescription(description);
        workspace.setTeam(team);
        workspace.setCreatedBy(user);

        workspace = sharedWorkspaceRepository.save(workspace);

        return toDto(workspace);
    }

    @Transactional(readOnly = true)
    public SharedWorkspaceDto getWorkspace(UUID workspaceId) {
        SharedWorkspace workspace = sharedWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));
        return toDto(workspace);
    }

    @Transactional(readOnly = true)
    public List<SharedWorkspaceDto> getTeamWorkspaces(UUID teamId, UUID userId) {
        teamRepository.findById(teamId)
                .orElseThrow(() -> new NotFoundException("Team not found"));

        if (!teamMemberRepository.existsByTeam_IdAndUser_Id(teamId, userId)) {
            throw new ForbiddenException("Only team members can view workspaces");
        }

        return sharedWorkspaceRepository.findByTeam_Id(teamId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void deleteWorkspace(UUID workspaceId, UUID userId) {
        SharedWorkspace workspace = sharedWorkspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace not found"));

        UUID teamId = workspace.getTeam().getId();

        // Allow deletion if user is the creator
        if (workspace.getCreatedBy().getId().equals(userId)) {
            sharedWorkspaceRepository.delete(workspace);
            return;
        }

        // Allow deletion if user is team OWNER or ADMIN
        TeamMember member = teamMemberRepository.findByTeam_IdAndUser_Id(teamId, userId)
                .orElseThrow(() -> new ForbiddenException("Not a team member"));

        if (member.getRole() != TeamRole.OWNER && member.getRole() != TeamRole.ADMIN) {
            throw new ForbiddenException("Only the creator, team owner, or admin can delete workspaces");
        }

        sharedWorkspaceRepository.delete(workspace);
    }

    private SharedWorkspaceDto toDto(SharedWorkspace workspace) {
        return new SharedWorkspaceDto(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getTeam().getId(),
                workspace.getCreatedBy().getId(),
                workspace.getCreatedAt()
        );
    }
}
