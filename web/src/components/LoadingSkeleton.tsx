interface LoadingSkeletonProps {
  variant?: 'card' | 'table-row' | 'text-block';
  count?: number;
}

function SkeletonCard() {
  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 animate-pulse">
      <div className="h-4 bg-slate-800 rounded w-1/3 mb-4" />
      <div className="space-y-3">
        <div className="h-3 bg-slate-800 rounded w-full" />
        <div className="h-3 bg-slate-800 rounded w-5/6" />
        <div className="h-3 bg-slate-800 rounded w-2/3" />
      </div>
      <div className="mt-6 h-9 bg-slate-800 rounded-lg w-1/3" />
    </div>
  );
}

function SkeletonTableRow() {
  return (
    <div className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-xl p-4 animate-pulse">
      <div className="flex items-center gap-4 flex-1">
        <div className="w-8 h-8 bg-slate-800 rounded-full" />
        <div className="space-y-2 flex-1">
          <div className="h-3.5 bg-slate-800 rounded w-1/4" />
          <div className="h-2.5 bg-slate-800 rounded w-1/6" />
        </div>
      </div>
      <div className="flex items-center gap-3">
        <div className="h-5 w-16 bg-slate-800 rounded-full" />
        <div className="h-4 w-4 bg-slate-800 rounded" />
      </div>
    </div>
  );
}

function SkeletonTextBlock() {
  return (
    <div className="animate-pulse space-y-3">
      <div className="h-4 bg-slate-800 rounded w-3/4" />
      <div className="h-4 bg-slate-800 rounded w-full" />
      <div className="h-4 bg-slate-800 rounded w-5/6" />
      <div className="h-4 bg-slate-800 rounded w-1/2" />
    </div>
  );
}

export default function LoadingSkeleton({
  variant = 'card',
  count = 1,
}: LoadingSkeletonProps) {
  const items = Array.from({ length: count }, (_, i) => i);

  return (
    <div className="space-y-3">
      {items.map((i) => {
        switch (variant) {
          case 'card':
            return <SkeletonCard key={i} />;
          case 'table-row':
            return <SkeletonTableRow key={i} />;
          case 'text-block':
            return <SkeletonTextBlock key={i} />;
        }
      })}
    </div>
  );
}
