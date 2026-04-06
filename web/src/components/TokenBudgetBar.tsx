import type { TokenBudget, ThresholdStatus } from '@/types';

interface TokenBudgetBarProps {
  budget: TokenBudget;
}

function formatTokenCount(count: number): string {
  if (count >= 1000) {
    return `${Math.round(count / 1000)}K`;
  }
  return String(count);
}

const statusConfig: Record<
  ThresholdStatus,
  { label: string; barColor: string; textColor: string }
> = {
  NORMAL: {
    label: 'NORMAL',
    barColor: 'bg-emerald-500',
    textColor: 'text-emerald-400',
  },
  WARNING: {
    label: 'WARNING',
    barColor: 'bg-amber-500',
    textColor: 'text-amber-400',
  },
  CRITICAL: {
    label: 'CRITICAL',
    barColor: 'bg-red-500',
    textColor: 'text-red-400',
  },
  EXHAUSTED: {
    label: 'EXHAUSTED',
    barColor: 'bg-red-700',
    textColor: 'text-red-400',
  },
};

export default function TokenBudgetBar({ budget }: TokenBudgetBarProps) {
  const percentage =
    budget.allocatedTokens > 0
      ? Math.min(
          100,
          Math.round(
            (budget.consumedTokens / budget.allocatedTokens) * 100,
          ),
        )
      : 0;

  const config = statusConfig[budget.thresholdStatus] ?? statusConfig.NORMAL;

  // Determine bar color based on percentage thresholds
  let barColor = statusConfig.NORMAL.barColor;
  if (percentage >= 95) {
    barColor = statusConfig.CRITICAL.barColor;
  } else if (percentage >= 80) {
    barColor = statusConfig.WARNING.barColor;
  }

  return (
    <div className="bg-slate-800/50 rounded-lg px-4 py-3">
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs text-slate-400">Token Budget</span>
        <span className={`text-xs font-medium ${config.textColor}`}>
          {config.label}
        </span>
      </div>

      <div className="w-full h-2 bg-slate-700 rounded-full overflow-hidden">
        <div
          className={`h-full rounded-full transition-all duration-500 ease-out ${barColor}`}
          style={{ width: `${percentage}%` }}
        />
      </div>

      <div className="flex items-center justify-between mt-1.5">
        <span className="text-xs text-slate-500">
          {formatTokenCount(budget.consumedTokens)} /{' '}
          {formatTokenCount(budget.allocatedTokens)} tokens
        </span>
        <span className="text-xs text-slate-500">{percentage}%</span>
      </div>
    </div>
  );
}
