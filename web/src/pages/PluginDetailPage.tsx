import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  usePlugin,
  usePluginReviews,
  useSubmitReview,
  usePurchasePlugin,
} from '@/hooks/useMarketplace';
import { useAuthStore } from '@/stores/authStore';
import { useToastStore } from '@/stores/toastStore';
import StarRating from '@/components/StarRating';
import LoadingSkeleton from '@/components/LoadingSkeleton';

const categoryColors: Record<string, string> = {
  UTILITY: 'bg-blue-500/15 text-blue-400',
  GAME: 'bg-green-500/15 text-green-400',
  ADMIN: 'bg-red-500/15 text-red-400',
  ECONOMY: 'bg-yellow-500/15 text-yellow-400',
  CHAT: 'bg-purple-500/15 text-purple-400',
  OTHER: 'bg-slate-500/15 text-slate-400',
};

function formatPrice(priceCents: number): string {
  if (priceCents === 0) return 'Free';
  return `$${(priceCents / 100).toFixed(2)}`;
}

export default function PluginDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated());
  const addToast = useToastStore((s) => s.addToast);

  const { data: plugin, isLoading: pluginLoading } = usePlugin(id ?? '');
  const { data: reviews, isLoading: reviewsLoading } = usePluginReviews(
    id ?? '',
  );

  const purchaseMutation = usePurchasePlugin(id ?? '');
  const reviewMutation = useSubmitReview(id ?? '');

  const [reviewRating, setReviewRating] = useState(5);
  const [reviewComment, setReviewComment] = useState('');

  const handlePurchase = () => {
    purchaseMutation.mutate(undefined, {
      onSuccess: () => {
        addToast(
          'success',
          plugin?.priceCents === 0
            ? 'Plugin downloaded successfully!'
            : 'Plugin purchased successfully!',
        );
      },
      onError: () => {
        addToast('error', 'Failed to process. Please try again.');
      },
    });
  };

  const handleSubmitReview = (e: React.FormEvent) => {
    e.preventDefault();
    if (!reviewComment.trim()) return;

    reviewMutation.mutate(
      { rating: reviewRating, comment: reviewComment.trim() },
      {
        onSuccess: () => {
          addToast('success', 'Review submitted!');
          setReviewComment('');
          setReviewRating(5);
        },
        onError: () => {
          addToast('error', 'Failed to submit review.');
        },
      },
    );
  };

  if (pluginLoading) {
    return (
      <div>
        <LoadingSkeleton variant="text-block" count={2} />
      </div>
    );
  }

  if (!plugin) {
    return (
      <div className="text-center py-12">
        <h2 className="text-lg font-semibold text-white mb-2">
          Plugin not found
        </h2>
        <p className="text-sm text-slate-400 mb-4">
          The plugin you are looking for does not exist or has been removed.
        </p>
        <button
          onClick={() => navigate('/dashboard/marketplace')}
          className="px-4 py-2 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
        >
          Back to Marketplace
        </button>
      </div>
    );
  }

  const colorClass =
    categoryColors[plugin.category] ?? categoryColors['OTHER'];

  return (
    <div>
      {/* Back Button */}
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

      {/* Plugin Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6 mb-6">
        <div className="flex flex-col lg:flex-row lg:items-start lg:justify-between gap-4">
          <div className="flex-1">
            <div className="flex items-center gap-3 mb-2">
              <h1 className="text-2xl font-bold text-white">{plugin.title}</h1>
              <span
                className={`px-2.5 py-0.5 rounded-md text-xs font-medium ${colorClass}`}
              >
                {plugin.category}
              </span>
            </div>

            {plugin.sellerName && (
              <p className="text-sm text-slate-400 mb-3">
                by{' '}
                <span className="text-slate-300 font-medium">
                  {plugin.sellerName}
                </span>
              </p>
            )}

            <div className="flex flex-wrap items-center gap-4 mb-4">
              <div className="flex items-center gap-1.5">
                <StarRating rating={plugin.averageRating} size="sm" />
                <span className="text-sm text-slate-400">
                  {plugin.averageRating.toFixed(1)} ({plugin.reviewCount}{' '}
                  review{plugin.reviewCount !== 1 ? 's' : ''})
                </span>
              </div>
              <span className="flex items-center gap-1.5 text-sm text-slate-400">
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
                    d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
                  />
                </svg>
                {plugin.downloadCount} downloads
              </span>
              <span className="inline-block px-2 py-0.5 rounded-md bg-slate-800 text-xs text-slate-300 font-medium">
                MC {plugin.minecraftVersion}
              </span>
            </div>

            <p className="text-sm text-slate-300 leading-relaxed whitespace-pre-wrap">
              {plugin.description}
            </p>
          </div>

          {/* Price / Action */}
          <div className="lg:w-64 shrink-0 bg-slate-800/50 border border-slate-700/50 rounded-xl p-5">
            <div className="text-center mb-4">
              <span
                className={`text-2xl font-bold ${
                  plugin.priceCents === 0 ? 'text-green-400' : 'text-white'
                }`}
              >
                {formatPrice(plugin.priceCents)}
              </span>
            </div>
            <button
              onClick={handlePurchase}
              disabled={purchaseMutation.isPending}
              className="w-full px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
            >
              {purchaseMutation.isPending
                ? 'Processing...'
                : plugin.priceCents === 0
                  ? 'Download'
                  : 'Purchase'}
            </button>
            <p className="text-xs text-slate-500 text-center mt-3">
              Published{' '}
              {new Date(plugin.createdAt).toLocaleDateString()}
            </p>
          </div>
        </div>
      </div>

      {/* Reviews Section */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-6">
        <h2 className="text-lg font-semibold text-white mb-5">Reviews</h2>

        {/* Submit Review Form */}
        {isAuthenticated && (
          <form
            onSubmit={handleSubmitReview}
            className="mb-6 p-4 bg-slate-800/50 border border-slate-700/50 rounded-xl"
          >
            <h3 className="text-sm font-medium text-white mb-3">
              Write a Review
            </h3>
            <div className="mb-3">
              <label className="block text-xs text-slate-400 mb-1.5">
                Rating
              </label>
              <StarRating
                rating={reviewRating}
                onChange={setReviewRating}
                size="md"
              />
            </div>
            <div className="mb-3">
              <label className="block text-xs text-slate-400 mb-1.5">
                Comment
              </label>
              <textarea
                value={reviewComment}
                onChange={(e) => setReviewComment(e.target.value)}
                placeholder="Share your experience with this plugin..."
                rows={3}
                maxLength={5000}
                className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-white placeholder-slate-500 text-sm resize-none focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/30 transition-colors"
              />
            </div>
            <button
              type="submit"
              disabled={
                reviewMutation.isPending || !reviewComment.trim()
              }
              className="px-4 py-2 rounded-lg bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
            >
              {reviewMutation.isPending ? 'Submitting...' : 'Submit Review'}
            </button>
          </form>
        )}

        {/* Reviews List */}
        {reviewsLoading ? (
          <LoadingSkeleton variant="text-block" count={3} />
        ) : !reviews || reviews.length === 0 ? (
          <p className="text-sm text-slate-500 text-center py-6">
            No reviews yet. Be the first to review this plugin!
          </p>
        ) : (
          <div className="space-y-4">
            {reviews.map((review) => (
              <div
                key={review.id}
                className="p-4 bg-slate-800/30 border border-slate-700/30 rounded-lg"
              >
                <div className="flex items-center justify-between mb-2">
                  <div className="flex items-center gap-3">
                    <div className="w-7 h-7 rounded-full bg-blue-600/20 flex items-center justify-center text-xs text-blue-400 font-medium">
                      {(review.reviewerName ?? 'U').charAt(0).toUpperCase()}
                    </div>
                    <div>
                      <span className="text-sm font-medium text-white">
                        {review.reviewerName ?? 'Anonymous'}
                      </span>
                      <p className="text-xs text-slate-500">
                        {new Date(review.createdAt).toLocaleDateString()}
                      </p>
                    </div>
                  </div>
                  <StarRating rating={review.rating} size="sm" />
                </div>
                <p className="text-sm text-slate-300 leading-relaxed">
                  {review.comment}
                </p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
