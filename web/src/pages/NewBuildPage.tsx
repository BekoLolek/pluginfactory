import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCreateBuild } from '@/hooks/useBuilds';

export default function NewBuildPage() {
  const navigate = useNavigate();
  const createBuild = useCreateBuild();
  const triggered = useRef(false);

  useEffect(() => {
    if (triggered.current) return;
    triggered.current = true;

    createBuild.mutate(undefined, {
      onSuccess: (session) => {
        navigate(`/dashboard/builds/${session.id}`, { replace: true });
      },
    });
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  if (createBuild.isError) {
    return (
      <div>
        <div className="mb-8">
          <h1 className="text-2xl font-bold text-white">New Build</h1>
          <p className="mt-1 text-slate-400">
            Start a new AI-powered plugin build session.
          </p>
        </div>

        <div className="max-w-2xl">
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-8 text-center">
            <div className="mb-4 p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-sm">
              Failed to create build session. Please try again.
            </div>
            <button
              onClick={() => {
                triggered.current = false;
                createBuild.mutate(undefined, {
                  onSuccess: (session) => {
                    navigate(`/dashboard/builds/${session.id}`, {
                      replace: true,
                    });
                  },
                });
              }}
              className="px-6 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
            >
              Retry
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex items-center justify-center py-20">
      <div className="text-center">
        <svg
          className="w-8 h-8 animate-spin text-blue-500 mx-auto mb-4"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
          />
        </svg>
        <p className="text-slate-400">Creating build session...</p>
      </div>
    </div>
  );
}
