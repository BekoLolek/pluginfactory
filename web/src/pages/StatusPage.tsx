import { useEffect, useState, useCallback } from 'react';
import { Link } from 'react-router-dom';
import axios from 'axios';

const API_BASE =
  import.meta.env.VITE_API_URL ||
  (import.meta.env.PROD ? '' : 'http://localhost:8080');

interface ServiceCheck {
  name: string;
  description: string;
  status: 'operational' | 'degraded' | 'down' | 'checking';
}

/**
 * Public status page — accessible without authentication.
 *
 * Pings the backend Actuator health endpoint to determine API health,
 * and makes a lightweight DB check via the same endpoint (Spring auto-
 * includes datasource health in the composite).
 */
export default function StatusPage() {
  const [services, setServices] = useState<ServiceCheck[]>([
    { name: 'API', description: 'Core REST API', status: 'checking' },
    { name: 'Database', description: 'PostgreSQL datastore', status: 'checking' },
    { name: 'AI Engine', description: 'Plugin generation pipeline', status: 'checking' },
    { name: 'Build Sandbox', description: 'Docker build containers', status: 'checking' },
  ]);
  const [lastChecked, setLastChecked] = useState<Date | null>(null);
  const [checking, setChecking] = useState(false);

  const runChecks = useCallback(async () => {
    setChecking(true);

    // Start everything as "checking"
    setServices((prev) =>
      prev.map((s) => ({ ...s, status: 'checking' })),
    );

    let apiUp = false;

    // 1) Hit actuator health
    try {
      const res = await axios.get(`${API_BASE}/actuator/health`, {
        timeout: 10000,
      });
      apiUp = true;

      const data = res.data;
      const overallStatus: string = data?.status ?? 'UNKNOWN';

      // API
      setServices((prev) =>
        prev.map((s) =>
          s.name === 'API'
            ? {
                ...s,
                status: overallStatus === 'UP' ? 'operational' : 'degraded',
              }
            : s,
        ),
      );

      // If the health endpoint exposes component details (admin), use them.
      // Otherwise infer from the aggregate status.
      const components = data?.components;

      // Database
      const dbStatus = components?.db?.status ?? components?.dataSource?.status;
      setServices((prev) =>
        prev.map((s) =>
          s.name === 'Database'
            ? {
                ...s,
                status: dbStatus
                  ? dbStatus === 'UP'
                    ? 'operational'
                    : 'degraded'
                  : overallStatus === 'UP'
                    ? 'operational'
                    : 'degraded',
              }
            : s,
        ),
      );

      // AI Engine — infer from the overall status since there's no dedicated
      // health indicator. If the API is up, we assume the AI pipeline is
      // reachable (actual generation errors are per-request).
      setServices((prev) =>
        prev.map((s) =>
          s.name === 'AI Engine'
            ? { ...s, status: overallStatus === 'UP' ? 'operational' : 'degraded' }
            : s,
        ),
      );

      // Build Sandbox — same heuristic
      setServices((prev) =>
        prev.map((s) =>
          s.name === 'Build Sandbox'
            ? { ...s, status: overallStatus === 'UP' ? 'operational' : 'degraded' }
            : s,
        ),
      );
    } catch {
      // API unreachable — everything is down
      setServices((prev) =>
        prev.map((s) => ({ ...s, status: apiUp ? 'degraded' : 'down' })),
      );
    }

    setLastChecked(new Date());
    setChecking(false);
  }, []);

  useEffect(() => {
    runChecks();
    const interval = setInterval(runChecks, 60_000); // re-check every 60 s
    return () => clearInterval(interval);
  }, [runChecks]);

  const overallStatus: 'operational' | 'degraded' | 'down' | 'checking' =
    services.some((s) => s.status === 'checking')
      ? 'checking'
      : services.every((s) => s.status === 'operational')
        ? 'operational'
        : services.every((s) => s.status === 'down')
          ? 'down'
          : 'degraded';

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      {/* Nav */}
      <header className="sticky top-0 z-40 backdrop-blur-xl bg-slate-950/70 border-b border-slate-800/80">
        <nav className="max-w-3xl mx-auto px-6 h-16 flex items-center justify-between">
          <Link to="/" className="flex items-center gap-2.5 group">
            <img
              src="/favicon.svg"
              alt=""
              className="w-8 h-8 transition-transform group-hover:rotate-6"
            />
            <span className="text-lg font-semibold tracking-tight">
              Plugin<span className="text-blue-400">Factory</span>
            </span>
          </Link>
          <Link
            to="/"
            className="text-sm text-slate-400 hover:text-slate-100 transition-colors"
          >
            Back to home
          </Link>
        </nav>
      </header>

      <main className="max-w-3xl mx-auto px-6 py-16">
        {/* Overall banner */}
        <div
          className={`rounded-2xl border p-6 mb-10 text-center ${
            overallStatus === 'operational'
              ? 'bg-emerald-500/5 border-emerald-500/30'
              : overallStatus === 'down'
                ? 'bg-red-500/5 border-red-500/30'
                : overallStatus === 'degraded'
                  ? 'bg-amber-500/5 border-amber-500/30'
                  : 'bg-slate-900/60 border-slate-800'
          }`}
        >
          <div className="flex items-center justify-center gap-3 mb-2">
            <StatusDot status={overallStatus} size="lg" />
            <h1 className="text-2xl font-bold">
              {overallStatus === 'operational' && 'All systems operational'}
              {overallStatus === 'degraded' && 'Partial service disruption'}
              {overallStatus === 'down' && 'Service outage'}
              {overallStatus === 'checking' && 'Checking services...'}
            </h1>
          </div>
          {lastChecked && (
            <p className="text-sm text-slate-500">
              Last checked {lastChecked.toLocaleTimeString()}
            </p>
          )}
        </div>

        {/* Service list */}
        <div className="space-y-3">
          {services.map((service) => (
            <div
              key={service.name}
              className="flex items-center justify-between p-4 rounded-xl bg-slate-900/60 border border-slate-800"
            >
              <div>
                <p className="font-medium text-slate-100">{service.name}</p>
                <p className="text-sm text-slate-500">{service.description}</p>
              </div>
              <div className="flex items-center gap-2.5">
                <span
                  className={`text-sm font-medium ${
                    service.status === 'operational'
                      ? 'text-emerald-400'
                      : service.status === 'degraded'
                        ? 'text-amber-400'
                        : service.status === 'down'
                          ? 'text-red-400'
                          : 'text-slate-500'
                  }`}
                >
                  {service.status === 'operational' && 'Operational'}
                  {service.status === 'degraded' && 'Degraded'}
                  {service.status === 'down' && 'Down'}
                  {service.status === 'checking' && 'Checking'}
                </span>
                <StatusDot status={service.status} />
              </div>
            </div>
          ))}
        </div>

        {/* Refresh button */}
        <div className="mt-8 text-center">
          <button
            onClick={runChecks}
            disabled={checking}
            className="px-5 py-2.5 rounded-xl bg-slate-900/80 hover:bg-slate-800 disabled:opacity-50 disabled:cursor-not-allowed border border-slate-800 text-sm font-medium text-slate-200 transition-colors"
          >
            {checking ? (
              <span className="flex items-center gap-2">
                <svg
                  className="w-4 h-4 animate-spin"
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
                Checking...
              </span>
            ) : (
              'Refresh status'
            )}
          </button>
        </div>

        {/* Footer info */}
        <div className="mt-16 pt-8 border-t border-slate-800/80 text-center">
          <p className="text-sm text-slate-500">
            Status checks run automatically every 60 seconds.
          </p>
          <p className="text-sm text-slate-500 mt-1">
            Need help?{' '}
            <a
              href="https://discord.com/invite/PRNEMA9xgR"
              target="_blank"
              rel="noreferrer noopener"
              className="text-blue-400 hover:text-blue-300 transition-colors"
            >
              Join our Discord
            </a>
          </p>
        </div>
      </main>
    </div>
  );
}

function StatusDot({
  status,
  size = 'sm',
}: {
  status: 'operational' | 'degraded' | 'down' | 'checking';
  size?: 'sm' | 'lg';
}) {
  const dim = size === 'lg' ? 'w-3.5 h-3.5' : 'w-2.5 h-2.5';
  const color =
    status === 'operational'
      ? 'bg-emerald-400'
      : status === 'degraded'
        ? 'bg-amber-400'
        : status === 'down'
          ? 'bg-red-400'
          : 'bg-slate-500';
  const pulse =
    status === 'checking' ? 'animate-pulse' : status === 'operational' ? '' : '';

  return <span className={`inline-block rounded-full ${dim} ${color} ${pulse}`} />;
}
