/**
 * Plan complexity score → human-readable band.
 *
 * The backend's {@code ComplexityEstimator} produces a raw weighted sum:
 * {@code commands*10 + eventListeners*15 + configEntries*3 + dependencies*25
 * + estimatedLoc*0.1}. The score has no upper bound and no built-in
 * labels, so we classify it here for display. Thresholds are calibrated
 * from representative plugin shapes:
 *
 * - < 50   — a 1-command utility plugin with no deps: {@link 'simple'}
 * - < 150  — a few commands, a listener or two, one dep: {@link 'medium'}
 * - < 300  — multi-command plugin with several listeners + deps: {@link 'complex'}
 * - ≥ 300  — large plugin with many deps and thousands of LOC: {@link 'very-complex'}
 */
export type ComplexityBand = 'simple' | 'medium' | 'complex' | 'very-complex';

export interface ComplexityInfo {
  band: ComplexityBand;
  label: string;
  /** Tailwind classes for a pill/badge background + text. */
  classes: string;
  /** Short description for tooltips / helper text. */
  description: string;
}

export function classifyComplexity(score: number): ComplexityInfo {
  if (score < 50) {
    return {
      band: 'simple',
      label: 'Simple',
      classes: 'bg-green-500/15 text-green-400 border border-green-500/20',
      description: 'A small plugin — minimal commands, no external dependencies.',
    };
  }
  if (score < 150) {
    return {
      band: 'medium',
      label: 'Medium',
      classes: 'bg-amber-500/15 text-amber-400 border border-amber-500/20',
      description: 'A typical plugin — a handful of commands and listeners.',
    };
  }
  if (score < 300) {
    return {
      band: 'complex',
      label: 'Complex',
      classes: 'bg-orange-500/15 text-orange-400 border border-orange-500/20',
      description: 'A substantial plugin — many commands, listeners, or dependencies.',
    };
  }
  return {
    band: 'very-complex',
    label: 'Very complex',
    classes: 'bg-red-500/15 text-red-400 border border-red-500/20',
    description:
      'A large plugin — expect several dependencies and thousands of lines of code.',
  };
}
