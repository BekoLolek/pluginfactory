import { useEffect, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { handleCallback } from '@/api/auth';
import AuthLayout from '@/layouts/AuthLayout';

export default function AuthCallbackPage() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const login = useAuthStore((s) => s.login);
  const [asyncError, setAsyncError] = useState<string | null>(null);

  const code = searchParams.get('code');
  const state = searchParams.get('state');

  // Derive validation errors during render — they're pure functions of the
  // URL params, so there's no need for an effect (which would also trip the
  // react-hooks/set-state-in-effect lint rule).
  const validationError = !code
    ? 'No authorization code received from Discord.'
    : !state
      ? 'Missing state parameter. Please try logging in again.'
      : null;

  const error = validationError ?? asyncError;

  useEffect(() => {
    if (!code || !state) {
      return;
    }

    let cancelled = false;

    async function exchangeCode() {
      try {
        const response = await handleCallback(code!, state!);
        if (!cancelled) {
          login(response);
          navigate('/dashboard', { replace: true });
        }
      } catch {
        if (!cancelled) {
          // setState inside an awaited callback is allowed by the rule:
          // we're reacting to an external system (the auth API) completing.
          setAsyncError('Authentication failed. Please try again.');
        }
      }
    }

    exchangeCode();

    return () => {
      cancelled = true;
    };
  }, [code, state, login, navigate]);

  if (error) {
    return (
      <AuthLayout>
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 text-center">
          <div className="w-12 h-12 rounded-full bg-red-500/10 flex items-center justify-center mx-auto mb-4">
            <svg className="w-6 h-6 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </div>
          <h2 className="text-lg font-semibold text-white mb-2">Authentication Error</h2>
          <p className="text-sm text-slate-400 mb-6">{error}</p>
          <button
            onClick={() => navigate('/', { replace: true })}
            className="px-6 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
          >
            Back to Login
          </button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 text-center">
        <div className="w-12 h-12 rounded-full bg-blue-500/10 flex items-center justify-center mx-auto mb-4">
          <svg className="w-6 h-6 text-blue-400 animate-spin" fill="none" viewBox="0 0 24 24">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        </div>
        <h2 className="text-lg font-semibold text-white mb-2">Signing you in</h2>
        <p className="text-sm text-slate-400">Please wait while we complete authentication...</p>
      </div>
    </AuthLayout>
  );
}
