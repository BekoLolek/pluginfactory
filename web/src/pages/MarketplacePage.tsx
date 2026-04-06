import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useSearchPlugins } from '@/hooks/useMarketplace';
import PluginCard from '@/components/PluginCard';
import EmptyState from '@/components/EmptyState';

const CATEGORIES = ['ALL', 'UTILITY', 'GAME', 'ADMIN', 'ECONOMY', 'CHAT', 'OTHER'] as const;
const SORT_OPTIONS = [
  { value: 'rating', label: 'Rating' },
  { value: 'downloads', label: 'Downloads' },
  { value: 'newest', label: 'Newest' },
  { value: 'price', label: 'Price' },
] as const;
const PAGE_SIZE = 12;

export default function MarketplacePage() {
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState<string>('ALL');
  const [sort, setSort] = useState('newest');
  const [version, setVersion] = useState('');
  const [page, setPage] = useState(0);

  const params = {
    ...(search ? { search } : {}),
    ...(category !== 'ALL' ? { category } : {}),
    ...(version ? { version } : {}),
    sort,
    page,
    size: PAGE_SIZE,
  };

  const { data, isLoading } = useSearchPlugins(params);
  const plugins = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div>
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-white">Marketplace</h1>
          <p className="mt-1 text-slate-400">
            Browse and share community-built plugins.
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Link
            to="/dashboard/marketplace/my-purchases"
            className="px-4 py-2.5 rounded-xl border border-slate-700 text-slate-300 hover:text-white hover:border-slate-600 text-sm font-medium transition-colors"
          >
            My Purchases
          </Link>
          <Link
            to="/dashboard/marketplace/my-listings"
            className="px-4 py-2.5 rounded-xl border border-slate-700 text-slate-300 hover:text-white hover:border-slate-600 text-sm font-medium transition-colors"
          >
            My Listings
          </Link>
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
      </div>

      {/* Search Bar */}
      <div className="mb-5">
        <div className="relative">
          <svg
            className="absolute left-3.5 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z"
            />
          </svg>
          <input
            type="text"
            placeholder="Search plugins..."
            value={search}
            onChange={(e) => {
              setSearch(e.target.value);
              setPage(0);
            }}
            className="w-full pl-10 pr-4 py-2.5 rounded-xl bg-slate-900 border border-slate-800 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500/50 focus:ring-1 focus:ring-blue-500/30 transition-colors"
          />
        </div>
      </div>

      {/* Filter Row */}
      <div className="flex flex-wrap items-center gap-3 mb-6">
        {/* Category Tabs */}
        <div className="flex items-center gap-1 bg-slate-900 border border-slate-800 rounded-xl p-1">
          {CATEGORIES.map((cat) => (
            <button
              key={cat}
              onClick={() => {
                setCategory(cat);
                setPage(0);
              }}
              className={`px-3 py-1.5 rounded-lg text-xs font-medium transition-colors ${
                category === cat
                  ? 'bg-blue-600 text-white'
                  : 'text-slate-400 hover:text-white hover:bg-slate-800'
              }`}
            >
              {cat === 'ALL' ? 'All' : cat.charAt(0) + cat.slice(1).toLowerCase()}
            </button>
          ))}
        </div>

        {/* Minecraft Version */}
        <input
          type="text"
          placeholder="MC version (e.g. 1.20)"
          value={version}
          onChange={(e) => {
            setVersion(e.target.value);
            setPage(0);
          }}
          className="px-3 py-2 rounded-xl bg-slate-900 border border-slate-800 text-white placeholder-slate-500 text-xs w-44 focus:outline-none focus:border-blue-500/50 transition-colors"
        />

        {/* Sort */}
        <select
          value={sort}
          onChange={(e) => {
            setSort(e.target.value);
            setPage(0);
          }}
          className="px-3 py-2 rounded-xl bg-slate-900 border border-slate-800 text-white text-xs focus:outline-none focus:border-blue-500/50 transition-colors appearance-none cursor-pointer"
        >
          {SORT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              Sort: {opt.label}
            </option>
          ))}
        </select>
      </div>

      {/* Plugin Grid */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {Array.from({ length: 6 }, (_, i) => (
            <div
              key={i}
              className="bg-slate-800 border border-slate-700/50 rounded-xl p-5 animate-pulse"
            >
              <div className="flex items-start justify-between mb-3">
                <div className="h-5 bg-slate-700 rounded w-2/3" />
                <div className="h-5 bg-slate-700 rounded w-16" />
              </div>
              <div className="space-y-2 mb-4">
                <div className="h-3 bg-slate-700 rounded w-full" />
                <div className="h-3 bg-slate-700 rounded w-4/5" />
              </div>
              <div className="h-5 bg-slate-700 rounded w-20 mb-3" />
              <div className="flex justify-between">
                <div className="h-4 bg-slate-700 rounded w-24" />
                <div className="h-4 bg-slate-700 rounded w-16" />
              </div>
            </div>
          ))}
        </div>
      ) : plugins.length === 0 ? (
        <EmptyState
          title="No plugins found"
          description="Try adjusting your search or filters. Or be the first to publish a plugin!"
          actionLabel="Publish a Plugin"
          onAction={() => {
            window.location.href = '/dashboard/marketplace/publish';
          }}
        />
      ) : (
        <>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 mb-6">
            {plugins.map((plugin) => (
              <PluginCard key={plugin.id} listing={plugin} />
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-center gap-2">
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                className="px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              <span className="text-sm text-slate-500">
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1.5 rounded-lg text-sm text-slate-400 hover:text-white hover:bg-slate-800 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
