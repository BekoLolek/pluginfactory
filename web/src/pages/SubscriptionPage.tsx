import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  getTiers,
  getCurrentSubscription,
  createCheckout,
  createPortal,
} from '@/api/subscriptions';
import { useToastStore } from '@/stores/toastStore';
import LoadingSkeleton from '@/components/LoadingSkeleton';

const TIER_ORDER = ['FREE', 'BASIC', 'PRO', 'TEAM'];

const TIER_PRICES: Record<string, string> = {
  FREE: '$0',
  BASIC: '$9.99',
  PRO: '$29.99',
  TEAM: '$79.99',
};

export default function SubscriptionPage() {
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const addToast = useToastStore((s) => s.addToast);

  const {
    data: tiers,
    isLoading: tiersLoading,
    isError: tiersError,
  } = useQuery({
    queryKey: ['tiers'],
    queryFn: getTiers,
  });

  const { data: subscription } = useQuery({
    queryKey: ['subscription'],
    queryFn: getCurrentSubscription,
  });

  const currentTier = subscription?.tier ?? 'FREE';

  const sortedTiers = tiers
    ? [...tiers].sort(
        (a, b) => TIER_ORDER.indexOf(a.name) - TIER_ORDER.indexOf(b.name),
      )
    : [];

  const isTrustedStripeUrl = (url: string) =>
    url.startsWith('https://checkout.stripe.com/') || url.startsWith('https://billing.stripe.com/');

  const handleCheckout = async (tier: string) => {
    setActionLoading(tier);
    try {
      const result = await createCheckout(tier);
      if (!isTrustedStripeUrl(result.url)) {
        addToast('error', 'Received an untrusted redirect URL.');
        return;
      }
      window.location.href = result.url;
    } catch {
      addToast('error', 'Failed to create checkout session. Please try again.');
    } finally {
      setActionLoading(null);
    }
  };

  const handleManage = async () => {
    setActionLoading('portal');
    try {
      const result = await createPortal();
      if (!isTrustedStripeUrl(result.url)) {
        addToast('error', 'Received an untrusted redirect URL.');
        return;
      }
      window.location.href = result.url;
    } catch {
      addToast('error', 'Failed to open billing portal. Please try again.');
    } finally {
      setActionLoading(null);
    }
  };

  const isCurrent = (tierName: string) =>
    tierName.toUpperCase() === currentTier.toUpperCase();

  const isPaid = currentTier !== 'FREE';

  return (
    <div>
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-white">Subscription</h1>
        <p className="mt-1 text-slate-400">
          Choose a plan that fits your plugin development needs.
        </p>
      </div>

      {isPaid && (
        <div className="mb-6">
          <button
            onClick={handleManage}
            disabled={actionLoading === 'portal'}
            className="inline-flex items-center gap-2 px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-sm text-slate-300 font-medium disabled:opacity-50 transition-colors"
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
                d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z"
              />
            </svg>
            {actionLoading === 'portal' ? 'Opening...' : 'Manage Billing'}
          </button>
        </div>
      )}

      {tiersLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
          <LoadingSkeleton variant="card" count={4} />
        </div>
      ) : tiersError || sortedTiers.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-12 text-center">
          <p className="text-slate-400">
            Subscription tiers are not available at this time.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-6">
          {sortedTiers.map((tier) => {
            const current = isCurrent(tier.name);
            return (
              <div
                key={tier.name}
                className={`bg-slate-900 border rounded-xl p-6 flex flex-col ${
                  current
                    ? 'border-blue-500 ring-2 ring-blue-500/20'
                    : 'border-slate-800'
                }`}
              >
                {/* Tier name + price */}
                <div className="mb-5">
                  <div className="flex items-center gap-2 mb-1">
                    <h3 className="text-lg font-semibold text-white capitalize">
                      {tier.name.toLowerCase()}
                    </h3>
                    {current && (
                      <span className="text-xs px-2 py-0.5 rounded-full bg-blue-600/20 text-blue-400 font-medium">
                        Current
                      </span>
                    )}
                  </div>
                  <div className="flex items-baseline gap-1">
                    <span className="text-3xl font-bold text-white">
                      {TIER_PRICES[tier.name] ?? '$?'}
                    </span>
                    <span className="text-sm text-slate-500">/mo</span>
                  </div>
                </div>

                {/* Feature list */}
                <div className="space-y-3 text-sm flex-1">
                  <FeatureRow
                    label="Builds / month"
                    value={String(tier.maxBuilds)}
                  />
                  <FeatureRow
                    label="Tokens / month"
                    value={tier.tokenBudget.toLocaleString()}
                  />
                  <FeatureRow
                    label="Max commands"
                    value={String(tier.maxCommands)}
                  />
                  <FeatureRow
                    label="Max event listeners"
                    value={String(tier.maxEventListeners)}
                  />
                  <FeatureRow
                    label="Iterations"
                    value={String(tier.maxIterations)}
                  />
                  <FeatureRow
                    label="Marketplace slots"
                    value={String(tier.marketplaceSlots)}
                  />
                  <FeatureBool
                    label="Source code access"
                    value={tier.sourceCodeAccess}
                  />
                  <FeatureRow
                    label="JAR retention"
                    value={`${tier.jarRetentionDays} days`}
                  />
                </div>

                {/* Action button */}
                <div className="mt-6">
                  {current ? (
                    <div className="w-full py-2.5 rounded-xl bg-slate-800 text-slate-400 text-sm font-medium text-center">
                      Current Plan
                    </div>
                  ) : (
                    <button
                      onClick={() => handleCheckout(tier.name)}
                      disabled={actionLoading === tier.name}
                      className="w-full py-2.5 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-medium transition-colors"
                    >
                      {actionLoading === tier.name
                        ? 'Redirecting...'
                        : 'Upgrade'}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function FeatureRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-400">{label}</span>
      <span className="text-white font-medium">{value}</span>
    </div>
  );
}

function FeatureBool({ label, value }: { label: string; value: boolean }) {
  return (
    <div className="flex justify-between">
      <span className="text-slate-400">{label}</span>
      {value ? (
        <svg
          className="w-5 h-5 text-green-400"
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
      ) : (
        <svg
          className="w-5 h-5 text-slate-600"
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M6 18L18 6M6 6l12 12"
          />
        </svg>
      )}
    </div>
  );
}
