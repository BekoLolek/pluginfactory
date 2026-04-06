import client from './client';
import type { MarketplaceListing, ReviewDto, PurchaseDto } from '@/types';

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface SearchParams {
  category?: string;
  version?: string;
  search?: string;
  sort?: string;
  page?: number;
  size?: number;
}

export interface CreateListingData {
  artifactId: string;
  title: string;
  description: string;
  shortDescription: string;
  category: string;
  minecraftVersion: string;
  priceCents: number;
}

export interface UpdateListingData {
  title?: string;
  description?: string;
  shortDescription?: string;
  category?: string;
  minecraftVersion?: string;
  priceCents?: number;
}

export async function searchPlugins(
  params: SearchParams,
): Promise<PageResponse<MarketplaceListing>> {
  const { data } = await client.get<PageResponse<MarketplaceListing>>(
    '/api/v1/marketplace/plugins',
    { params },
  );
  return data;
}

export async function getPlugin(id: string): Promise<MarketplaceListing> {
  const { data } = await client.get<MarketplaceListing>(
    `/api/v1/marketplace/plugins/${id}`,
  );
  return data;
}

export async function getPluginReviews(id: string): Promise<ReviewDto[]> {
  const { data } = await client.get<ReviewDto[]>(
    `/api/v1/marketplace/plugins/${id}/reviews`,
  );
  return data;
}

export async function createListing(
  listingData: CreateListingData,
): Promise<MarketplaceListing> {
  const { data } = await client.post<MarketplaceListing>(
    '/api/v1/marketplace/plugins',
    listingData,
  );
  return data;
}

export async function updateListing(
  id: string,
  listingData: UpdateListingData,
): Promise<MarketplaceListing> {
  const { data } = await client.put<MarketplaceListing>(
    `/api/v1/marketplace/plugins/${id}`,
    listingData,
  );
  return data;
}

export async function deleteListing(id: string): Promise<void> {
  await client.delete(`/api/v1/marketplace/plugins/${id}`);
}

export async function submitReview(
  listingId: string,
  rating: number,
  comment: string,
): Promise<ReviewDto> {
  const { data } = await client.post<ReviewDto>(
    `/api/v1/marketplace/plugins/${listingId}/reviews`,
    { rating, comment },
  );
  return data;
}

export async function purchasePlugin(
  listingId: string,
): Promise<PurchaseDto> {
  const { data } = await client.post<PurchaseDto>(
    `/api/v1/marketplace/plugins/${listingId}/purchase`,
  );
  return data;
}

export async function getMyListings(): Promise<MarketplaceListing[]> {
  const { data } = await client.get<MarketplaceListing[]>(
    '/api/v1/marketplace/my/listings',
  );
  return data;
}

export async function getMyPurchases(): Promise<PurchaseDto[]> {
  const { data } = await client.get<PurchaseDto[]>(
    '/api/v1/marketplace/my/purchases',
  );
  return data;
}
