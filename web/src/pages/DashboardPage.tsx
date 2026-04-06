import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useAuthStore } from '@/stores/authStore';
import { useBuilds } from '@/hooks/useBuilds';
import { getUsage } from '@/api/users';
import BuildStatusBadge from '@/components/BuildStatusBadge';
import LoadingSkeleton from '@/components/LoadingSkeleton';
import EmptyState from '@/components/EmptyState';

export default function DashboardPage() {
  const user = useAuthStore((s) => s.user);
  const { data, isLoading } = useBuilds(0, 5);

  const { data: usage } = useQuery({
    queryKey: ['usage'],
    queryFn: getUsage,
    retry: false,
  });

  const recentBuilds = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;

  const completedCount = recentBuilds.filter(
    (b) => b.status === 'COMPLETED',
  ).length;
  const activeCount = recentBuilds.filter(
    (b) =>
      b.status !== 'COMPLETED' &&
      b.status !== 'FAILED' &&
      b.status !== 'CANCELLED',
  ).length;

  const usagePercent = usage
    ? Math.min(100, Math.round((usage.buildsUsed / usage.buildsLimit) * 100))
    : 0;
  const nearLimit = usage
    ? usage.buildsUsed >= usage.buildsLimit * 0.8
    : false;
  const atLimit = usage ? usage.buildsUsed >= usage.buildsLimit : false;

  return (
    <div>
      {/* Welcome header */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-white">
          Welcome back{user ? `, ${user.displayName}` : ''}
        </h1>
        <p className="mt-1 text-slate-400">
          Here is an overview of your plugin development activity.
        </p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <p className="text-sm text-slate-400">Total Builds</p>
          <p className="mt-1 text-3xl font-bold text-white">
            {totalElements}
          </p>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <p className="text-sm text-slate-400">Active Builds</p>
          <p className="mt-1 text-3xl font-bold text-blue-400">
            {activeCount}
          </p>
        </div>
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
          <p className="text-sm text-slate-400">Completed</p>
          <p className="mt-1 text-3xl font-bold text-green-400">
            {completedCount}
          </p>
        </div>
        {usage && (
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <p className="text-sm text-slate-400">Current Tier</p>
            <p className="mt-1 text-3xl font-bold text-purple-400 capitalize">
              {usage.tier.toLowerCase()}
            </p>
          </div>
        )}
      </div>

      {/* Usage section */}
      {usage && (
        <div className="mb-8">
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5">
            <div className="flex items-center justify-between mb-3">
              <div>
                <p className="text-sm font-medium text-white">
                  Builds This Period
                </p>
                <p className="text-xs text-slate-500 mt-0.5">
                  {usage.buildsUsed} of {usage.buildsLimit} used
                </p>
              </div>
              {nearLimit && !atLimit && (
                <Link
                  to="/dashboard/settings/subscription"
                  className="text-xs text-amber-400 hover:text-amber-300 transition-colors"
                >
                  Approaching limit -- Upgrade
                </Link>
              )}
              {atLimit && (
                <Link
                  to="/dashboard/settings/subscription"
                  className="text-xs text-red-400 hover:text-red-300 transition-colors"
                >
                  Limit reached -- Upgrade now
                </Link>
              )}
            </div>
            <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full transition-all duration-500 ${
                  atLimit
                    ? 'bg-red-500'
                    : nearLimit
                      ? 'bg-amber-500'
                      : 'bg-blue-500'
                }`}
                style={{ width: `${usagePercent}%` }}
              />
            </div>
          </div>
        </div>
      )}

      {/* Quick actions */}
      <div className="mb-8">
        <Link
          to="/dashboard/builds/new"
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
        >
          <svg
            className="w-4 h-4"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 4v16m8-8H4"
            />
          </svg>
          New Build
        </Link>
      </div>

      {/* Recent builds */}
      <div>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-lg font-semibold text-white">Recent Builds</h2>
          <Link
            to="/dashboard/builds"
            className="text-sm text-blue-400 hover:text-blue-300 transition-colors"
          >
            View all
          </Link>
        </div>

        {isLoading ? (
          <LoadingSkeleton variant="table-row" count={3} />
        ) : recentBuilds.length === 0 ? (
          <EmptyState
            title="No builds yet"
            description="Get started by creating your first Minecraft plugin."
            actionLabel="Create your first plugin"
            onAction={() => {
              window.location.href = '/dashboard/builds/new';
            }}
          />
        ) : (
          <div className="space-y-2">
            {recentBuilds.map((build) => (
              <Link
                key={build.id}
                to={`/dashboard/builds/${build.id}`}
                className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-xl p-4 hover:border-slate-700 transition-colors"
              >
                <div className="flex items-center gap-4">
                  <div>
                    <p className="text-sm font-medium text-white">
                      Build {build.id.slice(0, 8)}
                    </p>
                    <p className="text-xs text-slate-500 mt-0.5">
                      {new Date(build.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-3">
                  <BuildStatusBadge status={build.status} />
                  <span className="text-xs text-slate-500">
                    {build.currentPhase}
                  </span>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
