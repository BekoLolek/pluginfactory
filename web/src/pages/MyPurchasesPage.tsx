import { useNavigate } from 'react-router-dom';
import { useMyPurchases } from '@/hooks/useMarketplace';
import LoadingSkeleton from '@/components/LoadingSkeleton';
import EmptyState from '@/components/EmptyState';

const statusColors: Record<string, string> = {
  COMPLETED: 'bg-green-500/15 text-green-400',
  PENDING: 'bg-yellow-500/15 text-yellow-400',
  FAILED: 'bg-red-500/15 text-red-400',
  REFUNDED: 'bg-slate-500/15 text-slate-400',
};

function formatPrice(priceCents: number): string {
  if (priceCents === 0) return 'Free';
  return `$${(priceCents / 100).toFixed(2)}`;
}

export default function MyPurchasesPage() {
  const navigate = useNavigate();
  const { data: purchases, isLoading } = useMyPurchases();

  return (
    <div>
      {/* Back */}
      <button
        onClick={() => navigate('/dashboard/marketplace')}
        className="flex items-center gap-1.5 text-sm text-slate-400 hover:text-white mb-6 transition-colors"
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
            d="M15 19l-7-7 7-7"
          />
        </svg>
        Back to Marketplace
      </button>

      <div className="mb-6">
        <h1 className="text-2xl font-bold text-white">My Purchases</h1>
        <p className="mt-1 text-slate-400">
          Plugins you have purchased or downloaded.
        </p>
      </div>

      {isLoading ? (
        <LoadingSkeleton variant="table-row" count={4} />
      ) : !purchases || purchases.length === 0 ? (
        <EmptyState
          title="No purchases yet"
          description="Browse the marketplace to find plugins for your Minecraft server."
          actionLabel="Browse Marketplace"
          onAction={() => navigate('/dashboard/marketplace')}
        />
      ) : (
        <div className="space-y-2">
          {purchases.map((purchase) => {
            const statusClass =
              statusColors[purchase.status] ?? statusColors['PENDING'];

            return (
              <div
                key={purchase.id}
                className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-xl p-4 hover:border-slate-700 transition-colors cursor-pointer"
                onClick={() =>
                  navigate(`/dashboard/marketplace/${purchase.listingId}`)
                }
              >
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-white mb-1">
                    Plugin {purchase.listingId.slice(0, 8)}...
                  </p>
                  <p className="text-xs text-slate-500">
                    Purchased{' '}
                    {new Date(purchase.createdAt).toLocaleDateString()} at{' '}
                    {new Date(purchase.createdAt).toLocaleTimeString()}
                  </p>
                </div>
                <div className="flex items-center gap-3 ml-4">
                  <span
                    className={`text-sm font-semibold ${
                      purchase.priceCents === 0
                        ? 'text-green-400'
                        : 'text-white'
                    }`}
                  >
                    {formatPrice(purchase.priceCents)}
                  </span>
                  <span
                    className={`px-2 py-0.5 rounded-md text-xs font-medium ${statusClass}`}
                  >
                    {purchase.status}
                  </span>
                  <svg
                    className="w-4 h-4 text-slate-600"
                    fill="none"
                    stroke="currentColor"
                    viewBox="0 0 24 24"
                  >
                    <path
                      strokeLinecap="round"
                      strokeLinejoin="round"
                      strokeWidth={2}
                      d="M9 5l7 7-7 7"
                    />
                  </svg>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
