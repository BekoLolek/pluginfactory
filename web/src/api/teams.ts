import client from './client';
import type {
  Team,
  TeamMember,
  SharedWorkspace,
  CreateTeamRequest,
  AddTeamMemberRequest,
  CreateWorkspaceRequest,
} from '@/types';

export async function getMyTeams(): Promise<Team[]> {
  const { data } = await client.get<Team[]>('/api/v1/teams');
  return data;
}

export async function getTeam(teamId: string): Promise<Team> {
  const { data } = await client.get<Team>(`/api/v1/teams/${teamId}`);
  return data;
}

export async function createTeam(request: CreateTeamRequest): Promise<Team> {
  const { data } = await client.post<Team>('/api/v1/teams', request);
  return data;
}

export async function deleteTeam(teamId: string): Promise<void> {
  await client.delete(`/api/v1/teams/${teamId}`);
}

export async function getTeamMembers(teamId: string): Promise<TeamMember[]> {
  const { data } = await client.get<TeamMember[]>(
    `/api/v1/teams/${teamId}/members`,
  );
  return data;
}

export async function addTeamMember(
  teamId: string,
  request: AddTeamMemberRequest,
): Promise<TeamMember> {
  const { data } = await client.post<TeamMember>(
    `/api/v1/teams/${teamId}/members`,
    request,
  );
  return data;
}

export async function removeTeamMember(
  teamId: string,
  userId: string,
): Promise<void> {
  await client.delete(`/api/v1/teams/${teamId}/members/${userId}`);
}

export async function updateMemberRole(
  teamId: string,
  userId: string,
  role: string,
): Promise<void> {
  await client.put(`/api/v1/teams/${teamId}/members/${userId}/role`, { role });
}

export async function getTeamWorkspaces(
  teamId: string,
): Promise<SharedWorkspace[]> {
  const { data } = await client.get<SharedWorkspace[]>(
    `/api/v1/teams/${teamId}/workspaces`,
  );
  return data;
}

export async function createWorkspace(
  teamId: string,
  request: CreateWorkspaceRequest,
): Promise<SharedWorkspace> {
  const { data } = await client.post<SharedWorkspace>(
    `/api/v1/teams/${teamId}/workspaces`,
    request,
  );
  return data;
}

export async function deleteWorkspace(
  teamId: string,
  workspaceId: string,
): Promise<void> {
  await client.delete(`/api/v1/teams/${teamId}/workspaces/${workspaceId}`);
}
