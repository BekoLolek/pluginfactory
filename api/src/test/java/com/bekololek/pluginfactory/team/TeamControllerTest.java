package com.bekololek.pluginfactory.team;

import com.bekololek.pluginfactory.auth.JwtAuthenticationFilter;
import com.bekololek.pluginfactory.auth.JwtService;
import com.bekololek.pluginfactory.common.config.CorsConfig;
import com.bekololek.pluginfactory.common.config.SecurityConfig;
import com.bekololek.pluginfactory.team.dto.SharedWorkspaceDto;
import com.bekololek.pluginfactory.team.dto.TeamDto;
import com.bekololek.pluginfactory.team.dto.TeamMemberDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TeamController.class)
@Import({SecurityConfig.class, CorsConfig.class, JwtAuthenticationFilter.class})
@TestPropertySource(properties = {
        "cors.allowed-origins=http://localhost:5173",
        "jwt.secret=test-secret-key-at-least-256-bits-long-for-hs256-algorithm-testing-only",
        "jwt.access-expiry=900000",
        "jwt.refresh-expiry=604800000"
})
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private TeamService teamService;

    @MockBean
    private SharedWorkspaceService sharedWorkspaceService;

    private final UUID userId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();

    @Test
    void createTeam_returns200() throws Exception {
        String token = setupAuth();
        TeamDto teamDto = new TeamDto(teamId, "My Team", userId, 10, 1, Instant.now());

        when(teamService.createTeam(eq(userId), eq("My Team"))).thenReturn(teamDto);

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Team\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Team"))
                .andExpect(jsonPath("$.memberCount").value(1));
    }

    @Test
    void createTeam_blankName_returns400() throws Exception {
        String token = setupAuth();

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserTeams_returns200() throws Exception {
        String token = setupAuth();
        TeamDto teamDto = new TeamDto(teamId, "My Team", userId, 10, 2, Instant.now());

        when(teamService.getUserTeams(userId)).thenReturn(List.of(teamDto));

        mockMvc.perform(get("/api/v1/teams")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("My Team"));
    }

    @Test
    void getTeam_returns200() throws Exception {
        String token = setupAuth();
        TeamDto teamDto = new TeamDto(teamId, "My Team", userId, 10, 3, Instant.now());

        when(teamService.getTeam(teamId, userId)).thenReturn(teamDto);

        mockMvc.perform(get("/api/v1/teams/{teamId}", teamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Team"))
                .andExpect(jsonPath("$.memberCount").value(3));
    }

    @Test
    void deleteTeam_returns204() throws Exception {
        String token = setupAuth();

        mockMvc.perform(delete("/api/v1/teams/{teamId}", teamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void addMember_returns200() throws Exception {
        String token = setupAuth();
        UUID newUserId = UUID.randomUUID();
        TeamMemberDto memberDto = new TeamMemberDto(
                UUID.randomUUID(), newUserId, "new@test.com", "NewUser", TeamRole.MEMBER, Instant.now());

        when(teamService.addMember(eq(teamId), eq(userId), eq(newUserId), eq(TeamRole.MEMBER)))
                .thenReturn(memberDto);

        mockMvc.perform(post("/api/v1/teams/{teamId}/members", teamId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + newUserId + "\",\"role\":\"MEMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("NewUser"))
                .andExpect(jsonPath("$.role").value("MEMBER"));
    }

    @Test
    void removeMember_returns204() throws Exception {
        String token = setupAuth();
        UUID targetUserId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/teams/{teamId}/members/{userId}", teamId, targetUserId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void updateMemberRole_returns200() throws Exception {
        String token = setupAuth();
        UUID targetUserId = UUID.randomUUID();
        TeamMemberDto memberDto = new TeamMemberDto(
                UUID.randomUUID(), targetUserId, "user@test.com", "User", TeamRole.ADMIN, Instant.now());

        when(teamService.updateMemberRole(eq(teamId), eq(userId), eq(targetUserId), eq(TeamRole.ADMIN)))
                .thenReturn(memberDto);

        mockMvc.perform(put("/api/v1/teams/{teamId}/members/{userId}/role", teamId, targetUserId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void createWorkspace_returns200() throws Exception {
        String token = setupAuth();
        SharedWorkspaceDto workspaceDto = new SharedWorkspaceDto(
                UUID.randomUUID(), "My Workspace", "Description", teamId, userId, Instant.now());

        when(sharedWorkspaceService.createWorkspace(eq(teamId), eq(userId), eq("My Workspace"), eq("Description")))
                .thenReturn(workspaceDto);

        mockMvc.perform(post("/api/v1/teams/{teamId}/workspaces", teamId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Workspace\",\"description\":\"Description\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("My Workspace"));
    }

    @Test
    void getTeamWorkspaces_returns200() throws Exception {
        String token = setupAuth();
        SharedWorkspaceDto workspaceDto = new SharedWorkspaceDto(
                UUID.randomUUID(), "Workspace 1", "Desc", teamId, userId, Instant.now());

        when(sharedWorkspaceService.getTeamWorkspaces(teamId, userId)).thenReturn(List.of(workspaceDto));

        mockMvc.perform(get("/api/v1/teams/{teamId}/workspaces", teamId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].name").value("Workspace 1"));
    }

    @Test
    void deleteWorkspace_returns204() throws Exception {
        String token = setupAuth();
        UUID workspaceId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/teams/{teamId}/workspaces/{workspaceId}", teamId, workspaceId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void createTeam_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/teams")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"My Team\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getUserTeams_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/teams"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getTeam_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/teams/{teamId}", teamId))
                .andExpect(status().isUnauthorized());
    }

    private String setupAuth() {
        String token = "test-token";
        when(jwtService.extractUserId(token)).thenReturn(userId);
        return token;
    }
}
