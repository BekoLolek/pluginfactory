import { useState } from 'react';
import { useParams, Link, useNavigate } from 'react-router-dom';
import {
  useTeam,
  useTeamMembers,
  useTeamWorkspaces,
  useDeleteTeam,
  useAddTeamMember,
  useRemoveTeamMember,
  useUpdateMemberRole,
  useCreateWorkspace,
  useDeleteWorkspace,
} from '@/hooks/useTeams';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import LoadingSkeleton from '@/components/LoadingSkeleton';

type Tab = 'members' | 'workspaces';

const roleBadgeClasses: Record<string, string> = {
  OWNER: 'bg-amber-500/15 text-amber-400',
  ADMIN: 'bg-purple-500/15 text-purple-400',
  MEMBER: 'bg-slate-500/15 text-slate-400',
};

export default function TeamDetailPage() {
  const { teamId } = useParams<{ teamId: string }>();
  const navigate = useNavigate();
  const user = useAuthStore((s) => s.user);
  const addToast = useToastStore((s) => s.addToast);

  const { data: team, isLoading: teamLoading } = useTeam(teamId ?? '');
  const { data: members, isLoading: membersLoading } = useTeamMembers(
    teamId ?? '',
  );
  const { data: workspaces, isLoading: workspacesLoading } = useTeamWorkspaces(
    teamId ?? '',
  );

  const deleteTeamMutation = useDeleteTeam();
  const addMemberMutation = useAddTeamMember(teamId ?? '');
  const removeMemberMutation = useRemoveTeamMember(teamId ?? '');
  const updateRoleMutation = useUpdateMemberRole(teamId ?? '');
  const createWorkspaceMutation = useCreateWorkspace(teamId ?? '');
  const deleteWorkspaceMutation = useDeleteWorkspace(teamId ?? '');

  const [activeTab, setActiveTab] = useState<Tab>('members');
  const [showAddMember, setShowAddMember] = useState(false);
  const [newMemberUserId, setNewMemberUserId] = useState('');
  const [newMemberRole, setNewMemberRole] = useState<'ADMIN' | 'MEMBER'>(
    'MEMBER',
  );
  const [showCreateWorkspace, setShowCreateWorkspace] = useState(false);
  const [workspaceName, setWorkspaceName] = useState('');
  const [workspaceDescription, setWorkspaceDescription] = useState('');
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const isOwner = team && user ? team.ownerId === user.id : false;
  const currentMember = members?.find((m) => m.userId === user?.id);
  const isAdminOrOwner =
    isOwner || (currentMember && currentMember.role === 'ADMIN');

  const handleDeleteTeam = async () => {
    if (!teamId) return;
    try {
      await deleteTeamMutation.mutateAsync(teamId);
      addToast('success', 'Team deleted successfully');
      navigate('/dashboard/teams');
    } catch {
      addToast('error', 'Failed to delete team');
    }
  };

  const handleAddMember = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newMemberUserId.trim()) return;

    try {
      await addMemberMutation.mutateAsync({
        userId: newMemberUserId.trim(),
        role: newMemberRole,
      });
      addToast('success', 'Member added successfully');
      setShowAddMember(false);
      setNewMemberUserId('');
      setNewMemberRole('MEMBER');
    } catch {
      addToast('error', 'Failed to add member');
    }
  };

  const handleRemoveMember = async (userId: string, displayName: string) => {
    try {
      await removeMemberMutation.mutateAsync(userId);
      addToast('success', `${displayName} removed from the team`);
    } catch {
      addToast('error', 'Failed to remove member');
    }
  };

  const handleUpdateRole = async (userId: string, role: string) => {
    try {
      await updateRoleMutation.mutateAsync({ userId, role });
      addToast('success', 'Role updated successfully');
    } catch {
      addToast('error', 'Failed to update role');
    }
  };

  const handleCreateWorkspace = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!workspaceName.trim()) return;

    try {
      await createWorkspaceMutation.mutateAsync({
        name: workspaceName.trim(),
        description: workspaceDescription.trim(),
      });
      addToast('success', 'Workspace created successfully');
      setShowCreateWorkspace(false);
      setWorkspaceName('');
      setWorkspaceDescription('');
    } catch {
      addToast('error', 'Failed to create workspace');
    }
  };

  const handleDeleteWorkspace = async (workspaceId: string) => {
    try {
      await deleteWorkspaceMutation.mutateAsync(workspaceId);
      addToast('success', 'Workspace deleted');
    } catch {
      addToast('error', 'Failed to delete workspace');
    }
  };

  if (teamLoading) {
    return <LoadingSkeleton variant="card" count={2} />;
  }

  if (!team) {
    return (
      <div className="text-center py-12">
        <p className="text-slate-400">Team not found.</p>
        <Link
          to="/dashboard/teams"
          className="text-sm text-blue-400 hover:text-blue-300 mt-2 inline-block transition-colors"
        >
          Back to teams
        </Link>
      </div>
    );
  }

  return (
    <div>
      {/* Breadcrumb */}
      <nav className="flex items-center gap-2 text-sm text-slate-500 mb-6" aria-label="Breadcrumb">
        <Link
          to="/dashboard/teams"
          className="hover:text-slate-300 transition-colors"
        >
          Teams
        </Link>
        <svg
          className="w-3.5 h-3.5"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 5l7 7-7 7"
          />
        </svg>
        <span className="text-slate-300">{team.name}</span>
      </nav>

      {/* Team Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 mb-6">
        <div className="flex items-start justify-between">
          <div>
            <h1 className="text-2xl font-bold text-white">{team.name}</h1>
            <div className="flex items-center gap-4 mt-2 text-sm text-slate-400">
              <span>
                {team.memberCount} / {team.maxMembers} members
              </span>
              <span>
                Created {new Date(team.createdAt).toLocaleDateString()}
              </span>
            </div>
          </div>
          {isOwner && (
            <button
              onClick={() => setShowDeleteConfirm(true)}
              className="px-4 py-2 rounded-lg text-sm font-medium text-red-400 hover:bg-red-500/10 transition-colors"
              aria-label="Delete team"
            >
              Delete Team
            </button>
          )}
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 border-b border-slate-800" role="tablist" aria-label="Team sections">
        <button
          onClick={() => setActiveTab('members')}
          className={`px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px ${
            activeTab === 'members'
              ? 'border-blue-500 text-blue-400'
              : 'border-transparent text-slate-400 hover:text-white'
          }`}
          role="tab"
          aria-selected={activeTab === 'members'}
          aria-controls="members-panel"
          id="members-tab"
        >
          Members
          {members && (
            <span className="ml-2 text-xs text-slate-500">
              ({members.length})
            </span>
          )}
        </button>
        <button
          onClick={() => setActiveTab('workspaces')}
          className={`px-4 py-2.5 text-sm font-medium transition-colors border-b-2 -mb-px ${
            activeTab === 'workspaces'
              ? 'border-blue-500 text-blue-400'
              : 'border-transparent text-slate-400 hover:text-white'
          }`}
          role="tab"
          aria-selected={activeTab === 'workspaces'}
          aria-controls="workspaces-panel"
          id="workspaces-tab"
        >
          Workspaces
          {workspaces && (
            <span className="ml-2 text-xs text-slate-500">
              ({workspaces.length})
            </span>
          )}
        </button>
      </div>

      {/* Members Tab */}
      {activeTab === 'members' && (
        <div role="tabpanel" id="members-panel" aria-labelledby="members-tab">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-white">Team Members</h2>
            {isAdminOrOwner && (
              <button
                onClick={() => setShowAddMember(true)}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
                aria-label="Add a new team member"
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 4v16m8-8H4"
                  />
                </svg>
                Add Member
              </button>
            )}
          </div>

          {membersLoading ? (
            <LoadingSkeleton variant="table-row" count={3} />
          ) : !members || members.length === 0 ? (
            <p className="text-sm text-slate-500 py-8 text-center">
              No members found.
            </p>
          ) : (
            <div className="space-y-2">
              {members.map((member) => (
                <div
                  key={member.id}
                  className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-xl p-4"
                >
                  <div className="flex items-center gap-3">
                    <div className="w-9 h-9 rounded-full bg-slate-700 flex items-center justify-center text-white text-sm font-medium shrink-0">
                      {member.displayName.charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <p className="text-sm font-medium text-white">
                        {member.displayName}
                      </p>
                      <p className="text-xs text-slate-500">
                        @{member.username}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    <span
                      className={`inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium ${
                        roleBadgeClasses[member.role] ?? roleBadgeClasses.MEMBER
                      }`}
                    >
                      {member.role}
                    </span>
                    {isAdminOrOwner &&
                      member.role !== 'OWNER' &&
                      member.userId !== user?.id && (
                        <div className="flex items-center gap-1">
                          {member.role === 'MEMBER' && (
                            <button
                              onClick={() =>
                                handleUpdateRole(member.userId, 'ADMIN')
                              }
                              className="px-2.5 py-1 rounded-md text-xs text-slate-400 hover:text-purple-400 hover:bg-slate-800 transition-colors"
                              title="Promote to Admin"
                              aria-label={`Promote ${member.displayName} to Admin`}
                            >
                              Promote
                            </button>
                          )}
                          {member.role === 'ADMIN' && isOwner && (
                            <button
                              onClick={() =>
                                handleUpdateRole(member.userId, 'MEMBER')
                              }
                              className="px-2.5 py-1 rounded-md text-xs text-slate-400 hover:text-slate-300 hover:bg-slate-800 transition-colors"
                              title="Demote to Member"
                              aria-label={`Demote ${member.displayName} to Member`}
                            >
                              Demote
                            </button>
                          )}
                          <button
                            onClick={() =>
                              handleRemoveMember(
                                member.userId,
                                member.displayName,
                              )
                            }
                            className="px-2.5 py-1 rounded-md text-xs text-slate-400 hover:text-red-400 hover:bg-slate-800 transition-colors"
                            title="Remove member"
                            aria-label={`Remove ${member.displayName} from team`}
                          >
                            Remove
                          </button>
                        </div>
                      )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Workspaces Tab */}
      {activeTab === 'workspaces' && (
        <div
          role="tabpanel"
          id="workspaces-panel"
          aria-labelledby="workspaces-tab"
        >
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-lg font-semibold text-white">Workspaces</h2>
            {isAdminOrOwner && (
              <button
                onClick={() => setShowCreateWorkspace(true)}
                className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
                aria-label="Create a new workspace"
              >
                <svg
                  className="w-4 h-4"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                  aria-hidden="true"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M12 4v16m8-8H4"
                  />
                </svg>
                Create Workspace
              </button>
            )}
          </div>

          {workspacesLoading ? (
            <LoadingSkeleton variant="card" count={2} />
          ) : !workspaces || workspaces.length === 0 ? (
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-12 text-center">
              <p className="text-sm text-slate-400">
                No workspaces yet. Create one to share builds with your team.
              </p>
            </div>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {workspaces.map((workspace) => (
                <div
                  key={workspace.id}
                  className="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition-colors"
                >
                  <div className="flex items-start justify-between mb-2">
                    <Link
                      to={`/dashboard/teams/${teamId}/workspaces/${workspace.id}`}
                      className="text-sm font-semibold text-white hover:text-blue-400 transition-colors"
                    >
                      {workspace.name}
                    </Link>
                    {isAdminOrOwner && (
                      <button
                        onClick={() => handleDeleteWorkspace(workspace.id)}
                        className="p-1 rounded-md text-slate-500 hover:text-red-400 hover:bg-slate-800 transition-colors"
                        aria-label={`Delete workspace ${workspace.name}`}
                      >
                        <svg
                          className="w-4 h-4"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                          aria-hidden="true"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                          />
                        </svg>
                      </button>
                    )}
                  </div>
                  <p className="text-xs text-slate-500 mb-3">
                    {workspace.description || 'No description'}
                  </p>
                  <p className="text-xs text-slate-600">
                    Created {new Date(workspace.createdAt).toLocaleDateString()}
                  </p>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Add Member Modal */}
      {showAddMember && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="add-member-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowAddMember(false);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setShowAddMember(false);
          }}
        >
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 w-full max-w-md mx-4">
            <h2
              id="add-member-title"
              className="text-lg font-semibold text-white mb-4"
            >
              Add Team Member
            </h2>
            <form onSubmit={handleAddMember}>
              <div className="space-y-4">
                <div>
                  <label
                    htmlFor="member-user-id"
                    className="block text-sm font-medium text-slate-300 mb-2"
                  >
                    User ID
                  </label>
                  <input
                    id="member-user-id"
                    type="text"
                    value={newMemberUserId}
                    onChange={(e) => setNewMemberUserId(e.target.value)}
                    placeholder="Enter user ID..."
                    className="w-full px-4 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    autoFocus
                    required
                  />
                </div>
                <div>
                  <label
                    htmlFor="member-role"
                    className="block text-sm font-medium text-slate-300 mb-2"
                  >
                    Role
                  </label>
                  <select
                    id="member-role"
                    value={newMemberRole}
                    onChange={(e) =>
                      setNewMemberRole(e.target.value as 'ADMIN' | 'MEMBER')
                    }
                    className="w-full px-4 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  >
                    <option value="MEMBER">Member</option>
                    <option value="ADMIN">Admin</option>
                  </select>
                </div>
              </div>
              <div className="flex items-center justify-end gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => {
                    setShowAddMember(false);
                    setNewMemberUserId('');
                    setNewMemberRole('MEMBER');
                  }}
                  className="px-4 py-2 rounded-lg text-sm font-medium text-slate-400 hover:text-white transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={
                    addMemberMutation.isPending || !newMemberUserId.trim()
                  }
                  className="px-5 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
                >
                  {addMemberMutation.isPending ? 'Adding...' : 'Add Member'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Create Workspace Modal */}
      {showCreateWorkspace && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="create-workspace-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowCreateWorkspace(false);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setShowCreateWorkspace(false);
          }}
        >
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 w-full max-w-md mx-4">
            <h2
              id="create-workspace-title"
              className="text-lg font-semibold text-white mb-4"
            >
              Create Workspace
            </h2>
            <form onSubmit={handleCreateWorkspace}>
              <div className="space-y-4">
                <div>
                  <label
                    htmlFor="workspace-name"
                    className="block text-sm font-medium text-slate-300 mb-2"
                  >
                    Name
                  </label>
                  <input
                    id="workspace-name"
                    type="text"
                    value={workspaceName}
                    onChange={(e) => setWorkspaceName(e.target.value)}
                    placeholder="Enter workspace name..."
                    className="w-full px-4 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                    autoFocus
                    required
                  />
                </div>
                <div>
                  <label
                    htmlFor="workspace-description"
                    className="block text-sm font-medium text-slate-300 mb-2"
                  >
                    Description
                  </label>
                  <textarea
                    id="workspace-description"
                    value={workspaceDescription}
                    onChange={(e) => setWorkspaceDescription(e.target.value)}
                    placeholder="Describe this workspace..."
                    rows={3}
                    className="w-full px-4 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent resize-none"
                  />
                </div>
              </div>
              <div className="flex items-center justify-end gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => {
                    setShowCreateWorkspace(false);
                    setWorkspaceName('');
                    setWorkspaceDescription('');
                  }}
                  className="px-4 py-2 rounded-lg text-sm font-medium text-slate-400 hover:text-white transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={
                    createWorkspaceMutation.isPending || !workspaceName.trim()
                  }
                  className="px-5 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
                >
                  {createWorkspaceMutation.isPending
                    ? 'Creating...'
                    : 'Create Workspace'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Delete Team Confirmation Modal */}
      {showDeleteConfirm && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-team-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowDeleteConfirm(false);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setShowDeleteConfirm(false);
          }}
        >
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 w-full max-w-md mx-4">
            <h2
              id="delete-team-title"
              className="text-lg font-semibold text-white mb-2"
            >
              Delete Team
            </h2>
            <p className="text-sm text-slate-400 mb-6">
              Are you sure you want to delete{' '}
              <span className="text-white font-medium">{team.name}</span>? This
              action cannot be undone and will remove all workspaces and members.
            </p>
            <div className="flex items-center justify-end gap-3">
              <button
                onClick={() => setShowDeleteConfirm(false)}
                className="px-4 py-2 rounded-lg text-sm font-medium text-slate-400 hover:text-white transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={handleDeleteTeam}
                disabled={deleteTeamMutation.isPending}
                className="px-5 py-2 rounded-lg bg-red-600 hover:bg-red-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
              >
                {deleteTeamMutation.isPending ? 'Deleting...' : 'Delete Team'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
