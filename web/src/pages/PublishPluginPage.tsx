import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useBuilds } from '@/hooks/useBuilds';
import { useCreateListing } from '@/hooks/useMarketplace';
import { useToastStore } from '@/stores/toastStore';
import { getArtifacts } from '@/api/builds';
import type { Artifact } from '@/types';
import { useQuery } from '@tanstack/react-query';

const CATEGORIES = ['UTILITY', 'GAME', 'ADMIN', 'ECONOMY', 'CHAT', 'OTHER'] as const;

function useCompletedBuildArtifacts() {
  const { data: buildsData } = useBuilds(0, 100);
  const completedBuilds = (buildsData?.content ?? []).filter(
    (b) => b.status === 'COMPLETED',
  );

  return useQuery({
    queryKey: ['all-artifacts', completedBuilds.map((b) => b.id)],
    queryFn: async () => {
      const allArtifacts: Array<Artifact & { sessionId: string }> = [];
      for (const build of completedBuilds) {
        const artifacts = await getArtifacts(build.id);
        for (const a of artifacts) {
          allArtifacts.push({ ...a, sessionId: build.id });
        }
      }
      return allArtifacts;
    },
    enabled: completedBuilds.length > 0,
  });
}

export default function PublishPluginPage() {
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const createListing = useCreateListing();
  const { data: artifacts, isLoading: artifactsLoading } =
    useCompletedBuildArtifacts();

  const [artifactId, setArtifactId] = useState('');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [shortDescription, setShortDescription] = useState('');
  const [category, setCategory] = useState<string>('UTILITY');
  const [minecraftVersion, setMinecraftVersion] = useState('1.20');
  const [priceText, setPriceText] = useState('0');
  const [publishedId, setPublishedId] = useState<string | null>(null);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!artifactId || !title.trim() || !shortDescription.trim() || !description.trim()) {
      addToast('warning', 'Please fill in all required fields.');
      return;
    }

    const priceCents = Math.round(parseFloat(priceText || '0') * 100);

    createListing.mutate(
      {
        artifactId,
        title: title.trim(),
        description: description.trim(),
        shortDescription: shortDescription.trim(),
        category,
        minecraftVersion,
        priceCents: Math.max(0, priceCents),
      },
      {
        onSuccess: (listing) => {
          addToast('success', 'Plugin published successfully!');
          setPublishedId(listing.id);
        },
        onError: () => {
          addToast('error', 'Failed to publish plugin. Please try again.');
        },
      },
    );
  };

  if (publishedId) {
    return (
      <div className="max-w-xl mx-auto">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-8 text-center">
          <div className="w-14 h-14 rounded-2xl bg-green-600/10 flex items-center justify-center mx-auto mb-4">
            <svg
              className="w-7 h-7 text-green-400"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M5 13l4 4L19 7"
              />
            </svg>
          </div>
          <h2 className="text-lg font-semibold text-white mb-2">
            Plugin Published!
          </h2>
          <p className="text-sm text-slate-400 mb-6">
            Your plugin is now live on the marketplace.
          </p>
          <div className="flex items-center justify-center gap-3">
            <Link
              to={`/dashboard/marketplace/${publishedId}`}
              className="px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium transition-colors"
            >
              View Listing
            </Link>
            <Link
              to="/dashboard/marketplace"
              className="px-5 py-2.5 rounded-xl border border-slate-700 text-slate-300 hover:text-white hover:border-slate-600 text-sm font-medium transition-colors"
            >
              Browse Marketplace
            </Link>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto">
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

      <h1 className="text-2xl font-bold text-white mb-1">Publish a Plugin</h1>
      <p className="text-slate-400 text-sm mb-6">
        Share your plugin with the community by filling in the details below.
      </p>

      <form
        onSubmit={handleSubmit}
        className="bg-slate-900 border border-slate-800 rounded-xl p-6 space-y-5"
      >
        {/* Artifact Selection */}
        <div>
          <label className="block text-sm font-medium text-slate-300 mb-1.5">
            Select Artifact *
          </label>
          {artifactsLoading ? (
            <div className="h-10 bg-slate-800 rounded-lg animate-pulse" />
          ) : !artifacts || artifacts.length === 0 ? (
            <p className="text-sm text-slate-500">
              No completed build artifacts found. Complete a build first.
            </p>
          ) : (
            <select
              value={artifactId}
              onChange={(e) => setArtifactId(e.target.value)}
              className="w-full px-3 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm focus:outline-none focus:border-blue-500/50 transition-colors cursor-pointer"
            >
              <option value="">Choose an artifact...</option>
              {artifacts.map((a) => (
                <option key={a.id} value={a.id}>
                  Build {a.sessionId.slice(0, 8)} - v{a.pluginVersion} (
                  {(a.fileSizeBytes / 1024).toFixed(1)} KB)
                </option>
              ))}
            </select>
          )}
        </div>

        {/* Title */}
        <div>
          <label className="block text-sm font-medium text-slate-300 mb-1.5">
            Title *
          </label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="My Awesome Plugin"
            maxLength={100}
            className="w-full px-3 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500/50 transition-colors"
          />
        </div>

        {/* Short Description */}
        <div>
          <label className="block text-sm font-medium text-slate-300 mb-1.5">
            Short Description *
          </label>
          <input
            type="text"
            value={shortDescription}
            onChange={(e) => setShortDescription(e.target.value)}
            placeholder="A brief summary shown in plugin cards"
            maxLength={200}
            className="w-full px-3 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500/50 transition-colors"
          />
        </div>

        {/* Full Description */}
        <div>
          <label className="block text-sm font-medium text-slate-300 mb-1.5">
            Description *
          </label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="Detailed description of what your plugin does, features, usage instructions..."
            rows={5}
            className="w-full px-3 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white placeholder-slate-500 text-sm resize-none focus:outline-none focus:border-blue-500/50 transition-colors"
          />
        </div>

        {/* Category + Version Row */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">
              Category
            </label>
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value)}
              className="w-full px-3 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm focus:outline-none focus:border-blue-500/50 transition-colors cursor-pointer"
            >
              {CATEGORIES.map((cat) => (
                <option key={cat} value={cat}>
                  {cat.charAt(0) + cat.slice(1).toLowerCase()}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1.5">
              Minecraft Version
            </label>
            <input
              type="text"
              value={minecraftVersion}
              onChange={(e) => setMinecraftVersion(e.target.value)}
              placeholder="1.20"
              className="w-full px-3 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white placeholder-slate-500 text-sm focus:outline-none focus:border-blue-500/50 transition-colors"
            />
          </div>
        </div>

        {/* Price */}
        <div>
          <label className="block text-sm font-medium text-slate-300 mb-1.5">
            Price (USD) - Enter 0 for free
          </label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-slate-500 text-sm">
              $
            </span>
            <input
              type="number"
              min="0"
              step="0.01"
              value={priceText}
              onChange={(e) => setPriceText(e.target.value)}
              className="w-full pl-7 pr-3 py-2.5 rounded-lg bg-slate-800 border border-slate-700 text-white text-sm focus:outline-none focus:border-blue-500/50 transition-colors"
            />
          </div>
        </div>

        {/* Submit */}
        <button
          type="submit"
          disabled={createListing.isPending}
          className="w-full px-5 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
        >
          {createListing.isPending ? 'Publishing...' : 'Publish Plugin'}
        </button>
      </form>
    </div>
  );
}
