import type { BuildStatus } from '@/types';

interface BuildStatusBadgeProps {
  status: BuildStatus;
}

const statusConfig: Record<
  BuildStatus,
  { bg: string; text: string; dot: string }
> = {
  CHATTING: {
    bg: 'bg-blue-500/15',
    text: 'text-blue-400',
    dot: 'bg-blue-400',
  },
  PLANNING: {
    bg: 'bg-purple-500/15',
    text: 'text-purple-400',
    dot: 'bg-purple-400',
  },
  APPROVED: {
    bg: 'bg-cyan-500/15',
    text: 'text-cyan-400',
    dot: 'bg-cyan-400',
  },
  BUILDING: {
    bg: 'bg-amber-500/15',
    text: 'text-amber-400',
    dot: 'bg-amber-400',
  },
  TESTING: {
    bg: 'bg-orange-500/15',
    text: 'text-orange-400',
    dot: 'bg-orange-400',
  },
  COMPLETED: {
    bg: 'bg-green-500/15',
    text: 'text-green-400',
    dot: 'bg-green-400',
  },
  FAILED: {
    bg: 'bg-red-500/15',
    text: 'text-red-400',
    dot: 'bg-red-400',
  },
  CANCELLED: {
    bg: 'bg-slate-500/15',
    text: 'text-slate-400',
    dot: 'bg-slate-400',
  },
};

export default function BuildStatusBadge({ status }: BuildStatusBadgeProps) {
  const config = statusConfig[status] ?? statusConfig.CANCELLED;

  return (
    <span
      className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium ${config.bg} ${config.text}`}
    >
      <span className={`w-1.5 h-1.5 rounded-full ${config.dot}`} />
      {status}
    </span>
  );
}
