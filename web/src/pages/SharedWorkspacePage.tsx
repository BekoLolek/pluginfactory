import { useParams, Link } from 'react-router-dom';
import { useTeam, useTeamWorkspaces } from '@/hooks/useTeams';
import LoadingSkeleton from '@/components/LoadingSkeleton';

export default function SharedWorkspacePage() {
  const { teamId, workspaceId } = useParams<{
    teamId: string;
    workspaceId: string;
  }>();

  const { data: team, isLoading: teamLoading } = useTeam(teamId ?? '');
  const { data: workspaces, isLoading: workspacesLoading } = useTeamWorkspaces(
    teamId ?? '',
  );

  const workspace = workspaces?.find((w) => w.id === workspaceId);
  const isLoading = teamLoading || workspacesLoading;

  if (isLoading) {
    return <LoadingSkeleton variant="card" count={2} />;
  }

  if (!team || !workspace) {
    return (
      <div className="text-center py-12">
        <p className="text-slate-400">Workspace not found.</p>
        <Link
          to={teamId ? `/dashboard/teams/${teamId}` : '/dashboard/teams'}
          className="text-sm text-blue-400 hover:text-blue-300 mt-2 inline-block transition-colors"
        >
          Back to team
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
        <Link
          to={`/dashboard/teams/${teamId}`}
          className="hover:text-slate-300 transition-colors"
        >
          {team.name}
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
        <span className="text-slate-300">{workspace.name}</span>
      </nav>

      {/* Workspace Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 mb-6">
        <h1 className="text-2xl font-bold text-white mb-2">
          {workspace.name}
        </h1>
        {workspace.description && (
          <p className="text-sm text-slate-400 mb-3">
            {workspace.description}
          </p>
        )}
        <div className="flex items-center gap-4 text-xs text-slate-500">
          <span>Team: {team.name}</span>
          <span>
            Created {new Date(workspace.createdAt).toLocaleDateString()}
          </span>
        </div>
      </div>

      {/* Builds Placeholder */}
      <div className="mb-6">
        <h2 className="text-lg font-semibold text-white mb-4">
          Shared Builds
        </h2>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-12 text-center">
          <div className="w-12 h-12 rounded-full bg-slate-800 flex items-center justify-center mx-auto mb-4">
            <svg
              className="w-6 h-6 text-slate-500"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
              />
            </svg>
          </div>
          <h3 className="text-lg font-semibold text-white mb-1">
            No shared builds yet
          </h3>
          <p className="text-sm text-slate-400 max-w-sm mx-auto">
            Builds shared to this workspace will appear here. Team members can
            collaborate on plugins in shared workspaces.
          </p>
        </div>
      </div>

      {/* Quick navigation back */}
      <div className="flex gap-3">
        <Link
          to={`/dashboard/teams/${teamId}`}
          className="inline-flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
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
              d="M15 19l-7-7 7-7"
            />
          </svg>
          Back to {team.name}
        </Link>
      </div>
    </div>
  );
}
