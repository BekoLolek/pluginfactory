import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { useAuthStore } from '@/stores/authStore';
import { getDiscordUrl } from '@/api/auth';
import AuthLayout from '@/layouts/AuthLayout';

export default function LoginPage() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (isAuthenticated()) {
    return <Navigate to="/dashboard" replace />;
  }

  const handleDiscordLogin = async () => {
    setLoading(true);
    setError(null);
    try {
      const url = await getDiscordUrl();
      if (!url.startsWith('https://discord.com/') && !url.startsWith('https://discordapp.com/')) {
        setError('Invalid redirect URL received. Please try again.');
        setLoading(false);
        return;
      }
      window.location.href = url;
    } catch {
      setError('Failed to connect to Discord. Please try again.');
      setLoading(false);
    }
  };

  return (
    <AuthLayout>
      <div className="text-center mb-10">
        <h1 className="text-4xl font-bold text-white tracking-tight">
          Plugin<span className="text-blue-400">Factory</span>
        </h1>
        <p className="mt-3 text-slate-400 text-lg">
          AI-powered Minecraft plugin development
        </p>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 shadow-xl">
        <div className="text-center mb-8">
          <h2 className="text-xl font-semibold text-white">Welcome back</h2>
          <p className="mt-2 text-sm text-slate-400">
            Sign in with your Discord account to get started
          </p>
        </div>

        {error && (
          <div className="mb-6 p-3 rounded-lg bg-red-500/10 border border-red-500/20 text-red-400 text-sm text-center">
            {error}
          </div>
        )}

        <button
          onClick={handleDiscordLogin}
          disabled={loading}
          className="w-full flex items-center justify-center gap-3 px-6 py-3 rounded-xl bg-[#5865F2] hover:bg-[#4752C4] disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium transition-colors"
        >
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="currentColor">
            <path d="M20.317 4.37a19.791 19.791 0 00-4.885-1.515.074.074 0 00-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 00-5.487 0 12.64 12.64 0 00-.617-1.25.077.077 0 00-.079-.037A19.736 19.736 0 003.677 4.37a.07.07 0 00-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 00.031.057 19.9 19.9 0 005.993 3.03.078.078 0 00.084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 00-.041-.106 13.107 13.107 0 01-1.872-.892.077.077 0 01-.008-.128 10.2 10.2 0 00.372-.292.074.074 0 01.077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 01.078.01c.12.098.246.198.373.292a.077.077 0 01-.006.127 12.299 12.299 0 01-1.873.892.077.077 0 00-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 00.084.028 19.839 19.839 0 006.002-3.03.077.077 0 00.032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 00-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z" />
          </svg>
          {loading ? 'Connecting...' : 'Continue with Discord'}
        </button>

        <div className="mt-8 pt-6 border-t border-slate-800">
          <div className="grid grid-cols-3 gap-4 text-center">
            <div>
              <p className="text-2xl font-bold text-blue-400">AI</p>
              <p className="text-xs text-slate-500 mt-1">Powered</p>
            </div>
            <div>
              <p className="text-2xl font-bold text-blue-400">1-Click</p>
              <p className="text-xs text-slate-500 mt-1">Plugin Build</p>
            </div>
            <div>
              <p className="text-2xl font-bold text-blue-400">Secure</p>
              <p className="text-xs text-slate-500 mt-1">Sandbox</p>
            </div>
          </div>
        </div>
      </div>

      <p className="mt-6 text-center text-xs text-slate-600">
        By signing in, you agree to our Terms of Service and Privacy Policy.
      </p>
    </AuthLayout>
  );
}
