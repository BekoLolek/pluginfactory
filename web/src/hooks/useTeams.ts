import {
  useQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import {
  getMyTeams,
  getTeam,
  createTeam,
  deleteTeam,
  getTeamMembers,
  addTeamMember,
  removeTeamMember,
  updateMemberRole,
  getTeamWorkspaces,
  createWorkspace,
  deleteWorkspace,
} from '@/api/teams';
import type { AddTeamMemberRequest, CreateWorkspaceRequest } from '@/types';

export function useMyTeams() {
  return useQuery({
    queryKey: ['teams'],
    queryFn: getMyTeams,
  });
}

export function useTeam(teamId: string) {
  return useQuery({
    queryKey: ['team', teamId],
    queryFn: () => getTeam(teamId),
    enabled: !!teamId,
  });
}

export function useTeamMembers(teamId: string) {
  return useQuery({
    queryKey: ['team-members', teamId],
    queryFn: () => getTeamMembers(teamId),
    enabled: !!teamId,
  });
}

export function useTeamWorkspaces(teamId: string) {
  return useQuery({
    queryKey: ['team-workspaces', teamId],
    queryFn: () => getTeamWorkspaces(teamId),
    enabled: !!teamId,
  });
}

export function useCreateTeam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createTeam,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
  });
}

export function useDeleteTeam() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: deleteTeam,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['teams'] });
    },
  });
}

export function useAddTeamMember(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: AddTeamMemberRequest) => addTeamMember(teamId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['team-members', teamId],
      });
      void queryClient.invalidateQueries({ queryKey: ['team', teamId] });
    },
  });
}

export function useRemoveTeamMember(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (userId: string) => removeTeamMember(teamId, userId),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['team-members', teamId],
      });
      void queryClient.invalidateQueries({ queryKey: ['team', teamId] });
    },
  });
}

export function useUpdateMemberRole(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: string }) =>
      updateMemberRole(teamId, userId, role),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['team-members', teamId],
      });
    },
  });
}

export function useCreateWorkspace(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateWorkspaceRequest) =>
      createWorkspace(teamId, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['team-workspaces', teamId],
      });
    },
  });
}

export function useDeleteWorkspace(teamId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (workspaceId: string) => deleteWorkspace(teamId, workspaceId),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['team-workspaces', teamId],
      });
    },
  });
}
