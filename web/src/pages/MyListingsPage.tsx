import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMyListings, useDeleteListing } from '@/hooks/useMarketplace';
import { useToastStore } from '@/stores/toastStore';
import StarRating from '@/components/StarRating';
import LoadingSkeleton from '@/components/LoadingSkeleton';
import EmptyState from '@/components/EmptyState';

const statusColors: Record<string, string> = {
  ACTIVE: 'bg-green-500/15 text-green-400',
  PENDING: 'bg-yellow-500/15 text-yellow-400',
  SUSPENDED: 'bg-red-500/15 text-red-400',
  DRAFT: 'bg-slate-500/15 text-slate-400',
};

export default function MyListingsPage() {
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const { data: listings, isLoading } = useMyListings();
  const deleteMutation = useDeleteListing();
  const [deletingId, setDeletingId] = useState<string | null>(null);

  const handleDelete = (id: string, title: string) => {
    if (!window.confirm(`Delete listing "${title}"? This cannot be undone.`)) {
      return;
    }
    setDeletingId(id);
    deleteMutation.mutate(id, {
      onSuccess: () => {
        addToast('success', 'Listing deleted.');
        setDeletingId(null);
      },
      onError: () => {
        addToast('error', 'Failed to delete listing.');
        setDeletingId(null);
      },
    });
  };

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

      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">My Listings</h1>
          <p className="mt-1 text-slate-400">
            Manage your published plugins.
          </p>
        </div>
        <Link
          to="/dashboard/marketplace/publish"
          className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
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
              d="M12 4v16m8-8H4"
            />
          </svg>
          Publish Plugin
        </Link>
      </div>

      {isLoading ? (
        <LoadingSkeleton variant="table-row" count={4} />
      ) : !listings || listings.length === 0 ? (
        <EmptyState
          title="No listings yet"
          description="Publish your first plugin to the marketplace and share it with the community."
          actionLabel="Publish a Plugin"
          onAction={() => navigate('/dashboard/marketplace/publish')}
        />
      ) : (
        <div className="space-y-2">
          {listings.map((listing) => {
            const statusClass =
              statusColors[listing.status] ?? statusColors['DRAFT'];

            return (
              <div
                key={listing.id}
                className="flex items-center justify-between bg-slate-900 border border-slate-800 rounded-xl p-4 hover:border-slate-700 transition-colors"
              >
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1">
                    <Link
                      to={`/dashboard/marketplace/${listing.id}`}
                      className="text-sm font-medium text-white hover:text-blue-400 transition-colors truncate"
                    >
                      {listing.title}
                    </Link>
                    <span
                      className={`shrink-0 px-2 py-0.5 rounded-md text-xs font-medium ${statusClass}`}
                    >
                      {listing.status}
                    </span>
                  </div>
                  <div className="flex items-center gap-4 text-xs text-slate-500">
                    <span className="flex items-center gap-1">
                      <svg
                        className="w-3.5 h-3.5"
                        fill="none"
                        stroke="currentColor"
                        viewBox="0 0 24 24"
                      >
                        <path
                          strokeLinecap="round"
                          strokeLinejoin="round"
                          strokeWidth={2}
                          d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
                        />
                      </svg>
                      {listing.downloadCount}
                    </span>
                    <span className="flex items-center gap-1">
                      <StarRating rating={listing.averageRating} size="sm" />
                      <span>({listing.reviewCount})</span>
                    </span>
                    <span>MC {listing.minecraftVersion}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 ml-4">
                  <Link
                    to={`/dashboard/marketplace/${listing.id}`}
                    className="px-3 py-1.5 rounded-lg text-xs text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
                  >
                    View
                  </Link>
                  <button
                    onClick={() => handleDelete(listing.id, listing.title)}
                    disabled={deletingId === listing.id}
                    className="px-3 py-1.5 rounded-lg text-xs text-red-400 hover:text-red-300 hover:bg-red-500/10 disabled:opacity-50 transition-colors"
                  >
                    {deletingId === listing.id ? 'Deleting...' : 'Delete'}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
