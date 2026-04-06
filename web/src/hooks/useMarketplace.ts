import {
  useQuery,
  useMutation,
  useQueryClient,
} from '@tanstack/react-query';
import {
  searchPlugins,
  getPlugin,
  getPluginReviews,
  createListing,
  submitReview,
  purchasePlugin,
  getMyListings,
  getMyPurchases,
  deleteListing,
  updateListing,
} from '@/api/marketplace';
import type { SearchParams, CreateListingData, UpdateListingData } from '@/api/marketplace';

export function useSearchPlugins(params: SearchParams) {
  return useQuery({
    queryKey: ['marketplace', 'search', params],
    queryFn: () => searchPlugins(params),
  });
}

export function usePlugin(id: string) {
  return useQuery({
    queryKey: ['marketplace', 'plugin', id],
    queryFn: () => getPlugin(id),
    enabled: !!id,
  });
}

export function usePluginReviews(id: string) {
  return useQuery({
    queryKey: ['marketplace', 'reviews', id],
    queryFn: () => getPluginReviews(id),
    enabled: !!id,
  });
}

export function useCreateListing() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateListingData) => createListing(data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['marketplace'] });
    },
  });
}

export function useUpdateListing(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: UpdateListingData) => updateListing(id, data),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['marketplace'] });
    },
  });
}

export function useDeleteListing() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => deleteListing(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['marketplace'] });
    },
  });
}

export function useSubmitReview(listingId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: { rating: number; comment: string }) =>
      submitReview(listingId, data.rating, data.comment),
    onSuccess: () => {
      void queryClient.invalidateQueries({
        queryKey: ['marketplace', 'reviews', listingId],
      });
      void queryClient.invalidateQueries({
        queryKey: ['marketplace', 'plugin', listingId],
      });
    },
  });
}

export function usePurchasePlugin(listingId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => purchasePlugin(listingId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['marketplace'] });
    },
  });
}

export function useMyListings() {
  return useQuery({
    queryKey: ['marketplace', 'my-listings'],
    queryFn: getMyListings,
  });
}

export function useMyPurchases() {
  return useQuery({
    queryKey: ['marketplace', 'my-purchases'],
    queryFn: getMyPurchases,
  });
}
