import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMyTeams, useCreateTeam } from '@/hooks/useTeams';
import { useToastStore } from '@/stores/toastStore';
import LoadingSkeleton from '@/components/LoadingSkeleton';
import EmptyState from '@/components/EmptyState';

export default function TeamDashboardPage() {
  const { data: teams, isLoading } = useMyTeams();
  const createTeam = useCreateTeam();
  const addToast = useToastStore((s) => s.addToast);

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [teamName, setTeamName] = useState('');

  const handleCreateTeam = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!teamName.trim()) return;

    try {
      await createTeam.mutateAsync({ name: teamName.trim() });
      addToast('success', 'Team created successfully');
      setShowCreateModal(false);
      setTeamName('');
    } catch {
      addToast('error', 'Failed to create team');
    }
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">Teams</h1>
          <p className="mt-1 text-slate-400">
            Manage your teams and collaborate on plugin development.
          </p>
        </div>
        <button
          onClick={() => setShowCreateModal(true)}
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
          aria-label="Create a new team"
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
          Create Team
        </button>
      </div>

      {/* Team list */}
      {isLoading ? (
        <LoadingSkeleton variant="card" count={3} />
      ) : !teams || teams.length === 0 ? (
        <EmptyState
          title="No teams yet"
          description="Create a team to start collaborating with other developers on plugin projects."
          actionLabel="Create your first team"
          onAction={() => setShowCreateModal(true)}
        />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {teams.map((team) => (
            <Link
              key={team.id}
              to={`/dashboard/teams/${team.id}`}
              className="bg-slate-900 border border-slate-800 rounded-xl p-5 hover:border-slate-700 transition-colors group"
            >
              <div className="flex items-start justify-between mb-3">
                <div className="w-10 h-10 rounded-lg bg-blue-600/20 flex items-center justify-center shrink-0">
                  <svg
                    className="w-5 h-5 text-blue-400"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                    aria-hidden="true"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
                    />
                  </svg>
                </div>
                <svg
                  className="w-4 h-4 text-slate-600 group-hover:text-slate-400 transition-colors"
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
              </div>
              <h3 className="text-sm font-semibold text-white mb-1">
                {team.name}
              </h3>
              <div className="flex items-center gap-4 text-xs text-slate-500">
                <span>
                  {team.memberCount} / {team.maxMembers} members
                </span>
                <span>
                  Created {new Date(team.createdAt).toLocaleDateString()}
                </span>
              </div>
            </Link>
          ))}
        </div>
      )}

      {/* Create Team Modal */}
      {showCreateModal && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm"
          role="dialog"
          aria-modal="true"
          aria-labelledby="create-team-title"
          onClick={(e) => {
            if (e.target === e.currentTarget) setShowCreateModal(false);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Escape') setShowCreateModal(false);
          }}
        >
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 w-full max-w-md mx-4">
            <h2
              id="create-team-title"
              className="text-lg font-semibold text-white mb-4"
            >
              Create Team
            </h2>
            <form onSubmit={handleCreateTeam}>
              <label
                htmlFor="team-name"
                className="block text-sm font-medium text-slate-300 mb-2"
              >
                Team Name
              </label>
              <input
                id="team-name"
                type="text"
                value={teamName}
                onChange={(e) => setTeamName(e.target.value)}
                placeholder="Enter team name..."
                maxLength={100}
                className="w-full px-4 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm placeholder-slate-500 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                autoFocus
                required
              />
              <div className="flex items-center justify-end gap-3 mt-6">
                <button
                  type="button"
                  onClick={() => {
                    setShowCreateModal(false);
                    setTeamName('');
                  }}
                  className="px-4 py-2 rounded-lg text-sm font-medium text-slate-400 hover:text-white transition-colors"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={createTeam.isPending || !teamName.trim()}
                  className="px-5 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
                >
                  {createTeam.isPending ? 'Creating...' : 'Create Team'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
