import { useState } from 'react';
import { downloadArtifact } from '@/api/builds';
import { useArtifacts } from '@/hooks/useBuilds';
import type { BuildSession, BuildPhase } from '@/types';

interface BuildProgressPanelProps {
  session: BuildSession;
}

interface PhaseStep {
  phase: BuildPhase;
  label: string;
}

const phases: PhaseStep[] = [
  { phase: 'IMPLEMENTATION', label: 'Implementation' },
  { phase: 'COMPILATION', label: 'Compilation' },
  { phase: 'SECURITY_SCAN', label: 'Security Scan' },
  { phase: 'DELIVERING', label: 'Delivering' },
];

const phaseOrder: Record<string, number> = {
  IMPLEMENTATION: 0,
  COMPILATION: 1,
  SECURITY_SCAN: 2,
  INTEGRATION_TEST: 2,
  DELIVERING: 3,
};

function getPhaseIndex(phase: BuildPhase): number {
  return phaseOrder[phase] ?? -1;
}

export default function BuildProgressPanel({
  session,
}: BuildProgressPanelProps) {
  const { data: artifacts } = useArtifacts(session.id);
  const [downloading, setDownloading] = useState(false);

  const isCompleted = session.status === 'COMPLETED';
  const isFailed = session.status === 'FAILED';
  const currentIndex = getPhaseIndex(session.currentPhase);

  const handleDownload = async () => {
    if (!artifacts || artifacts.length === 0) return;
    setDownloading(true);
    try {
      const latestArtifact = artifacts[artifacts.length - 1];
      const blob = await downloadArtifact(latestArtifact.id);
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `plugin-${latestArtifact.pluginVersion}.jar`;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
    } catch {
      // Download error handled silently
    } finally {
      setDownloading(false);
    }
  };

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6" aria-live="polite" aria-busy={!isCompleted && !isFailed}>
      <h3 className="text-lg font-semibold text-white mb-6">Build Progress</h3>

      {/* Stepper */}
      <div className="space-y-0">
        {phases.map((step, index) => {
          const isActive =
            !isCompleted && !isFailed && currentIndex === index;
          const isDone = isCompleted || currentIndex > index;
          const isFailedPhase = isFailed && currentIndex === index;

          return (
            <div key={step.phase} className="flex items-stretch gap-4">
              {/* Vertical line + icon */}
              <div className="flex flex-col items-center">
                <div
                  className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${
                    isFailedPhase
                      ? 'bg-red-500/20 text-red-400'
                      : isDone
                        ? 'bg-green-500/20 text-green-400'
                        : isActive
                          ? 'bg-blue-500/20 text-blue-400'
                          : 'bg-slate-800 text-slate-600'
                  }`}
                >
                  {isFailedPhase ? (
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
                        d="M6 18L18 6M6 6l12 12"
                      />
                    </svg>
                  ) : isDone ? (
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
                        d="M5 13l4 4L19 7"
                      />
                    </svg>
                  ) : isActive ? (
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
                  ) : (
                    <span className="w-2 h-2 rounded-full bg-current" />
                  )}
                </div>
                {/* Connecting line to next step (including the Done step) */}
                <div
                  className={`w-0.5 flex-1 min-h-4 ${
                    isDone ? 'bg-green-500/40' : 'bg-slate-800'
                  }`}
                />
              </div>

              {/* Label */}
              <div className="pb-4">
                <p
                  className={`text-sm font-medium ${
                    isFailedPhase
                      ? 'text-red-400'
                      : isDone
                        ? 'text-green-400'
                        : isActive
                          ? 'text-blue-400'
                          : 'text-slate-600'
                  }`}
                >
                  {step.label}
                  {isActive && (
                    <span className="ml-2 text-xs text-slate-500">
                      In progress...
                    </span>
                  )}
                </p>
              </div>
            </div>
          );
        })}

        {/* Done step */}
        <div className="flex items-stretch gap-4">
          <div className="flex flex-col items-center">
            <div
              className={`w-8 h-8 rounded-full flex items-center justify-center shrink-0 ${
                isCompleted
                  ? 'bg-green-500/20 text-green-400'
                  : 'bg-slate-800 text-slate-600'
              }`}
            >
              {isCompleted ? (
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
                    d="M5 13l4 4L19 7"
                  />
                </svg>
              ) : (
                <span className="w-2 h-2 rounded-full bg-current" />
              )}
            </div>
          </div>
          <div>
            <p
              className={`text-sm font-medium ${
                isCompleted ? 'text-green-400' : 'text-slate-600'
              }`}
            >
              Done
            </p>
          </div>
        </div>
      </div>

      {/* Completed */}
      {isCompleted && (
        <div className="mt-6 p-4 rounded-lg bg-green-500/10 border border-green-500/20">
          <p className="text-sm text-green-400 font-medium mb-3">
            Build completed successfully!
          </p>
          <button
            onClick={handleDownload}
            disabled={downloading || !artifacts || artifacts.length === 0}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-green-600 hover:bg-green-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
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
                d="M12 10v6m0 0l-3-3m3 3l3-3m2 8H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"
              />
            </svg>
            {downloading ? 'Downloading...' : 'Download JAR'}
          </button>
        </div>
      )}

      {/* Failed */}
      {isFailed && (
        <div className="mt-6 p-4 rounded-lg bg-red-500/10 border border-red-500/20">
          <p className="text-sm text-red-400 font-medium">Build failed</p>
          <p className="text-xs text-red-300/70 mt-1">
            The build encountered an error during the{' '}
            {phases[currentIndex]?.label ?? session.currentPhase} phase. You
            can try creating a new build session.
          </p>
        </div>
      )}
    </div>
  );
}
