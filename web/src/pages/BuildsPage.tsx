import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useBuilds, useDeleteBuild } from '@/hooks/useBuilds';
import BuildStatusBadge from '@/components/BuildStatusBadge';
import LoadingSkeleton from '@/components/LoadingSkeleton';
import EmptyState from '@/components/EmptyState';
import { classifyComplexity } from '@/utils/complexity';

const PAGE_SIZE = 10;

export default function BuildsPage() {
  const [page, setPage] = useState(0);
  const { data, isLoading } = useBuilds(page, PAGE_SIZE);
  const deleteMutation = useDeleteBuild();

  const builds = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  return (
    <div>
      <div className="flex items-center justify-between mb-8">
        <div>
          <h1 className="text-2xl font-bold text-white">Builds</h1>
          <p className="mt-1 text-slate-400">
            {totalElements} total build{totalElements !== 1 ? 's' : ''}
          </p>
        </div>
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

      {isLoading ? (
        <LoadingSkeleton variant="table-row" count={5} />
      ) : builds.length === 0 ? (
        <EmptyState
          title="No builds found"
          description="Get started by creating your first Minecraft plugin build."
          actionLabel="Create your first plugin build"
          onAction={() => {
            window.location.href = '/dashboard/builds/new';
          }}
        />
      ) : (
        <>
          <div className="space-y-2 mb-6">
            {builds.map((build) => (
              <div
                key={build.id}
                className="flex items-center bg-slate-900 border border-slate-800 rounded-xl hover:border-slate-700 transition-colors"
              >
                <Link
                  to={`/dashboard/builds/${build.id}`}
                  className="flex-1 flex items-center justify-between p-4"
                >
                  <div className="flex items-center gap-4">
                    <div>
                      <p className="text-sm font-medium text-white">
                        Build {build.id.slice(0, 8)}
                      </p>
                      <p className="text-xs text-slate-500 mt-0.5">
                        Created{' '}
                        {new Date(build.createdAt).toLocaleDateString()} at{' '}
                        {new Date(build.createdAt).toLocaleTimeString()}
                      </p>
                    </div>
                  </div>
                  <div className="flex items-center gap-3">
                    {build.complexityScore !== null &&
                      (() => {
                        const info = classifyComplexity(
                          build.complexityScore,
                        );
                        return (
                          <span
                            className={`px-2 py-0.5 rounded-full text-xs font-medium ${info.classes}`}
                            title={`${info.description} (raw score: ${build.complexityScore})`}
                          >
                            {info.label}
                          </span>
                        );
                      })()}
                    <span className="text-xs text-slate-500">
                      {build.currentPhase}
                    </span>
                    <BuildStatusBadge status={build.status} />
                    <svg
                      className="w-4 h-4 text-slate-600"
                      fill="none"
                      stroke="currentColor"
                      viewBox="0 0 24 24"
                    >
                      <path
                        strokeLinecap="round"
                        strokeLinejoin="round"
                        strokeWidth={2}
                        d="M9 5l7 7-7 7"
                      />
                    </svg>
                  </div>
                </Link>
                <button
                  onClick={(e) => {
                    e.stopPropagation();
                    if (
                      window.confirm(
                        'Delete this build? This cannot be undone.',
                      )
                    ) {
                      deleteMutation.mutate(build.id);
                    }
                  }}
                  disabled={deleteMutation.isPending}
                  className="shrink-0 p-3 mr-2 rounded-lg text-slate-600 hover:text-red-400 hover:bg-red-500/10 disabled:opacity-30 transition-colors"
                  title="Delete build"
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
                      d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16"
                    />
                  </svg>
                </button>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              <span className="text-sm text-slate-500">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() =>
                  setPage((p) => Math.min(totalPages - 1, p + 1))
                }
                disabled={page >= totalPages - 1}
                className="px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
