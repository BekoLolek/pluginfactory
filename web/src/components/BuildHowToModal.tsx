import { useState } from 'react';

/**
 * One-time "how building works" explainer shown when a user opens a build.
 * Focuses on token economy (the thing users most often burn through without
 * realising) in plain language, plus how to keep the clarification cheap.
 *
 * Dismissal is remembered in localStorage when the user ticks "Don't show
 * again", so it won't nag on every build. Otherwise it reappears next time —
 * gentle, not sticky.
 */
const DISMISS_KEY = 'pf-build-howto-dismissed';

export function buildHowToDismissed(): boolean {
  try {
    return localStorage.getItem(DISMISS_KEY) === 'true';
  } catch {
    return false;
  }
}

const COSTS: { label: string; value: string }[] = [
  { label: 'Each question you answer back-and-forth', value: '~2,000' },
  { label: 'Planning your plugin', value: '~12,000' },
  { label: 'Building & testing it', value: '~30,000' },
  { label: 'A simple plugin, start to finish', value: '~40,000' },
];

const TIERS = 'Free 75k · Basic 300k · Pro 900k · Team 6M';

export default function BuildHowToModal({ onClose }: { onClose: () => void }) {
  const [dontShow, setDontShow] = useState(false);

  const handleClose = () => {
    if (dontShow) {
      try {
        localStorage.setItem(DISMISS_KEY, 'true');
      } catch {
        // ignore storage failures — worst case the modal shows again
      }
    }
    onClose();
  };

  return (
    <div
      className="fixed inset-0 z-[120] flex items-center justify-center bg-slate-950/80 backdrop-blur-sm p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="howto-title"
      onClick={handleClose}
    >
      <div
        className="max-w-lg w-full bg-slate-900 border border-slate-800 rounded-2xl shadow-2xl overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6">
          <div className="flex items-start gap-3 mb-4">
            <div className="w-10 h-10 rounded-lg bg-blue-500/10 flex items-center justify-center shrink-0">
              <svg className="w-5 h-5 text-blue-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <div>
              <h2 id="howto-title" className="text-lg font-semibold text-white">
                How building works
              </h2>
              <p className="text-sm text-slate-400 mt-0.5">
                Every build runs on your monthly tokens — here's roughly where they go.
              </p>
            </div>
          </div>

          {/* Token cost table */}
          <div className="rounded-xl border border-slate-800 divide-y divide-slate-800 overflow-hidden">
            {COSTS.map((c) => (
              <div key={c.label} className="flex items-center justify-between gap-4 px-4 py-2.5">
                <span className="text-sm text-slate-300">{c.label}</span>
                <span className="text-sm font-semibold text-white whitespace-nowrap">
                  {c.value} <span className="text-slate-500 font-normal">tokens</span>
                </span>
              </div>
            ))}
          </div>
          <p className="text-xs text-slate-500 mt-2">
            Complex plugins and auto-fixes cost more. Your monthly allowance: {TIERS}.
          </p>

          {/* Tips */}
          <div className="mt-4 rounded-xl bg-slate-800/40 border border-slate-700/50 p-4">
            <p className="text-sm font-medium text-slate-200 mb-2">
              Spend fewer tokens on questions
            </p>
            <ul className="space-y-1.5 text-sm text-slate-400">
              <li className="flex gap-2">
                <span className="text-blue-400">•</span>
                Describe everything up front — features, commands, Minecraft version.
              </li>
              <li className="flex gap-2">
                <span className="text-blue-400">•</span>
                Answer all clarifying questions in <span className="text-slate-300">one</span> reply.
              </li>
              <li className="flex gap-2">
                <span className="text-blue-400">•</span>
                In a hurry? Flip <span className="text-slate-300">"Skip questions — just build it"</span> and the
                assistant goes straight from your description.
              </li>
            </ul>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-between gap-3 px-6 py-4 bg-slate-900/60 border-t border-slate-800">
          <label className="flex items-center gap-2 text-sm text-slate-400 cursor-pointer select-none">
            <input
              type="checkbox"
              checked={dontShow}
              onChange={(e) => setDontShow(e.target.checked)}
              className="h-4 w-4 rounded border-slate-600 bg-slate-800 text-blue-600 focus:ring-blue-500 focus:ring-offset-slate-900"
            />
            Don't show this again
          </label>
          <button
            type="button"
            onClick={handleClose}
            className="px-5 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
          >
            Got it
          </button>
        </div>
      </div>
    </div>
  );
}
