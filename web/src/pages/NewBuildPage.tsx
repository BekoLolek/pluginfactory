import { useEffect, useRef } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import type { AxiosError } from 'axios';
import { useCreateBuild } from '@/hooks/useBuilds';

interface BackendErrorBody {
  message?: string;
  status?: number;
}

/**
 * Pulls the user-facing message out of the backend's {@code ErrorResponse}
 * JSON, falling back to a generic string when the error wasn't an axios
 * error or the body didn't include a message.
 */
function extractErrorInfo(error: unknown): {
  status: number | null;
  message: string;
} {
  const axiosError = error as AxiosError<BackendErrorBody>;
  const status = axiosError?.response?.status ?? null;
  const backendMessage = axiosError?.response?.data?.message;
  return {
    status,
    message: backendMessage ?? 'Failed to create build session. Please try again.',
  };
}

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
    const { status, message } = extractErrorInfo(createBuild.error);
    // 403 from this endpoint means the user hit a subscription limit
    // (build count or remaining monthly tokens). Surface that clearly
    // and send them to the subscription page instead of just saying
    // "try again" — retrying won't help.
    const isLimitError = status === 403;

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
            <div className="mb-4 p-4 rounded-lg bg-red-500/10 border border-red-500/20 text-left">
              <p className="text-sm font-medium text-red-400 mb-1">
                {isLimitError
                  ? 'You’ve reached your plan’s build limit'
                  : 'Couldn’t start a new build'}
              </p>
              <p className="text-sm text-red-300/90">{message}</p>
              {isLimitError && (
                <p className="text-xs text-red-300/70 mt-2">
                  Your free tier allows a limited number of builds per month.
                  Upgrade to keep building, or wait until your quota resets.
                </p>
              )}
            </div>
            {isLimitError ? (
              <div className="flex items-center justify-center gap-3">
                <Link
                  to="/dashboard/settings/subscription"
                  className="px-6 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
                >
                  Upgrade your plan
                </Link>
                <Link
                  to="/dashboard/builds"
                  className="px-6 py-2.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 text-sm font-medium transition-colors"
                >
                  Back to builds
                </Link>
              </div>
            ) : (
              <button
                onClick={() => {
                  triggered.current = false;
                  createBuild.reset();
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
            )}
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
