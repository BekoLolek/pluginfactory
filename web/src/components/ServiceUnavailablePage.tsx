import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useServiceStatusStore } from '@/stores/serviceStatusStore';

/**
 * Full-screen overlay shown when the backend is unreachable.
 *
 * Mounted once at the top of {@code App.tsx} and toggled by
 * {@link useServiceStatusStore}, which is flipped from the axios
 * response interceptor on network errors or gateway-level HTTP statuses
 * (502 / 503 / 504).
 *
 * The "Try again" button clears the outage flag and invalidates every
 * active query so React Query refetches them. If the backend is back,
 * the successful responses will keep the flag cleared; if it's still
 * down, the interceptor will flip it again and this overlay reappears.
 */
export default function ServiceUnavailablePage() {
  const setUnavailable = useServiceStatusStore((s) => s.setUnavailable);
  const queryClient = useQueryClient();
  const [retrying, setRetrying] = useState(false);

  const handleRetry = async () => {
    setRetrying(true);
    setUnavailable(false);
    try {
      await queryClient.invalidateQueries();
    } finally {
      // Give refetches a beat so the user doesn't see the button snap
      // back instantly if the service is still down.
      setTimeout(() => setRetrying(false), 800);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center bg-slate-950/95 backdrop-blur-sm p-6">
      <div className="max-w-md w-full bg-slate-900 border border-slate-800 rounded-2xl p-8 text-center shadow-2xl">
        <div className="w-16 h-16 rounded-full bg-amber-500/10 flex items-center justify-center mx-auto mb-5">
          <svg
            className="w-8 h-8 text-amber-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"
            />
          </svg>
        </div>
        <h1 className="text-xl font-semibold text-white mb-2">
          Service temporarily unavailable
        </h1>
        <p className="text-sm text-slate-400 mb-6 leading-relaxed">
          We're having trouble reaching the Plugin Factory backend right now.
          This is usually brief — please try again in a couple of minutes.
        </p>
        <button
          onClick={handleRetry}
          disabled={retrying}
          className="w-full py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
        >
          {retrying ? 'Retrying...' : 'Try again'}
        </button>
        <p className="mt-4 text-xs text-slate-600">
          If this keeps happening, check{' '}
          <a
            href="https://status.pluginfactory.org"
            target="_blank"
            rel="noopener noreferrer"
            className="text-slate-500 hover:text-slate-300 transition-colors underline"
          >
            our status page
          </a>
          .
        </p>
      </div>
    </div>
  );
}
