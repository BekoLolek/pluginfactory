import { Link } from 'react-router-dom';
import type { MarketplaceListing } from '@/types';
import StarRating from '@/components/StarRating';

interface PluginCardProps {
  listing: MarketplaceListing;
}

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

export default function PluginCard({ listing }: PluginCardProps) {
  const colorClass =
    categoryColors[listing.category] ?? categoryColors['OTHER'];

  return (
    <Link
      to={`/dashboard/marketplace/${listing.id}`}
      className="block bg-slate-800 border border-slate-700/50 rounded-xl p-5 hover:bg-slate-800/80 hover:border-slate-600 transition-colors"
    >
      <div className="flex items-start justify-between gap-3 mb-3">
        <h3 className="text-base font-semibold text-white leading-snug line-clamp-1">
          {listing.title}
        </h3>
        <span
          className={`shrink-0 px-2 py-0.5 rounded-md text-xs font-medium ${colorClass}`}
        >
          {listing.category}
        </span>
      </div>

      <p className="text-sm text-slate-400 mb-4 line-clamp-2 leading-relaxed">
        {listing.shortDescription}
      </p>

      <div className="flex items-center gap-2 mb-3">
        <span className="inline-block px-2 py-0.5 rounded-md bg-slate-700/50 text-xs text-slate-300 font-medium">
          MC {listing.minecraftVersion}
        </span>
      </div>

      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <StarRating rating={listing.averageRating} size="sm" />
          <span className="text-xs text-slate-500">
            ({listing.reviewCount})
          </span>
        </div>
        <div className="flex items-center gap-3">
          <span className="flex items-center gap-1 text-xs text-slate-500">
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
          <span
            className={`text-sm font-semibold ${
              listing.priceCents === 0 ? 'text-green-400' : 'text-white'
            }`}
          >
            {formatPrice(listing.priceCents)}
          </span>
        </div>
      </div>
    </Link>
  );
}
