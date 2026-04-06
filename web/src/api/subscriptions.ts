import client from './client';
import type { TierInfo } from '@/types';

interface SubscriptionResponse {
  tier: string;
  status: string;
  currentPeriodEnd: string;
}

interface CheckoutResponse {
  url: string;
}

interface PortalResponse {
  url: string;
}

export async function getTiers(): Promise<TierInfo[]> {
  const { data } = await client.get<TierInfo[]>('/api/v1/subscriptions/tiers');
  return data;
}

export async function getCurrentSubscription(): Promise<SubscriptionResponse> {
  const { data } = await client.get<SubscriptionResponse>(
    '/api/v1/subscriptions/current',
  );
  return data;
}

export async function createCheckout(tier: string): Promise<CheckoutResponse> {
  const { data } = await client.post<CheckoutResponse>(
    '/api/v1/subscriptions/checkout',
    { tier },
  );
  return data;
}

export async function createPortal(): Promise<PortalResponse> {
  const { data } = await client.post<PortalResponse>(
    '/api/v1/subscriptions/portal',
  );
  return data;
}
