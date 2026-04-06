package com.bekololek.pluginfactory.team;

import com.bekololek.pluginfactory.common.util.AuthenticatedUser;
import com.bekololek.pluginfactory.team.dto.AddTeamMemberRequest;
import com.bekololek.pluginfactory.team.dto.CreateTeamRequest;
import com.bekololek.pluginfactory.team.dto.CreateWorkspaceRequest;
import com.bekololek.pluginfactory.team.dto.SharedWorkspaceDto;
import com.bekololek.pluginfactory.team.dto.TeamDto;
import com.bekololek.pluginfactory.team.dto.TeamMemberDto;
import com.bekololek.pluginfactory.team.dto.UpdateRoleRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;
    private final SharedWorkspaceService sharedWorkspaceService;

    @PostMapping
    public ResponseEntity<TeamDto> createTeam(@Valid @RequestBody CreateTeamRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        TeamDto team = teamService.createTeam(userId, request.name());
        return ResponseEntity.ok(team);
    }

    @GetMapping
    public ResponseEntity<List<TeamDto>> getUserTeams() {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        List<TeamDto> teams = teamService.getUserTeams(userId);
        return ResponseEntity.ok(teams);
    }

    @GetMapping("/{teamId}")
    public ResponseEntity<TeamDto> getTeam(@PathVariable UUID teamId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        TeamDto team = teamService.getTeam(teamId, userId);
        return ResponseEntity.ok(team);
    }

    @DeleteMapping("/{teamId}")
    public ResponseEntity<Void> deleteTeam(@PathVariable UUID teamId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        teamService.deleteTeam(teamId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{teamId}/members")
    public ResponseEntity<TeamMemberDto> addMember(@PathVariable UUID teamId,
                                                    @Valid @RequestBody AddTeamMemberRequest request) {
        UUID requesterId = AuthenticatedUser.getCurrentUserId();
        TeamMemberDto member = teamService.addMember(teamId, requesterId, request.userId(), request.role());
        return ResponseEntity.ok(member);
    }

    @DeleteMapping("/{teamId}/members/{userId}")
    public ResponseEntity<Void> removeMember(@PathVariable UUID teamId, @PathVariable UUID userId) {
        UUID requesterId = AuthenticatedUser.getCurrentUserId();
        teamService.removeMember(teamId, requesterId, userId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{teamId}/members/{userId}/role")
    public ResponseEntity<TeamMemberDto> updateMemberRole(@PathVariable UUID teamId,
                                                           @PathVariable UUID userId,
                                                           @Valid @RequestBody UpdateRoleRequest request) {
        UUID requesterId = AuthenticatedUser.getCurrentUserId();
        TeamMemberDto member = teamService.updateMemberRole(teamId, requesterId, userId, request.role());
        return ResponseEntity.ok(member);
    }

    @PostMapping("/{teamId}/workspaces")
    public ResponseEntity<SharedWorkspaceDto> createWorkspace(@PathVariable UUID teamId,
                                                               @Valid @RequestBody CreateWorkspaceRequest request) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        SharedWorkspaceDto workspace = sharedWorkspaceService.createWorkspace(
                teamId, userId, request.name(), request.description());
        return ResponseEntity.ok(workspace);
    }

    @GetMapping("/{teamId}/workspaces")
    public ResponseEntity<List<SharedWorkspaceDto>> getTeamWorkspaces(@PathVariable UUID teamId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        List<SharedWorkspaceDto> workspaces = sharedWorkspaceService.getTeamWorkspaces(teamId, userId);
        return ResponseEntity.ok(workspaces);
    }

    @DeleteMapping("/{teamId}/workspaces/{workspaceId}")
    public ResponseEntity<Void> deleteWorkspace(@PathVariable UUID teamId,
                                                 @PathVariable UUID workspaceId) {
        UUID userId = AuthenticatedUser.getCurrentUserId();
        sharedWorkspaceService.deleteWorkspace(workspaceId, userId);
        return ResponseEntity.noContent().build();
    }
}
