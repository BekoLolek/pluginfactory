import { create } from 'zustand';

/**
 * Global flag for "the backend isn't reachable at all" conditions.
 *
 * Flipped to {@code true} by the axios response interceptor when a request
 * fails with a network error (no response) or a gateway-level status
 * (502/503/504). When set, {@link ServiceUnavailablePage} renders as a
 * full-screen overlay above the normal app shell.
 *
 * We deliberately do NOT trip this on 500s — those can be per-endpoint
 * bugs that shouldn't take down the whole UI. Only truly-unreachable or
 * gateway-reported outages flip the global switch.
 */
interface ServiceStatusStore {
  isUnavailable: boolean;
  setUnavailable: (unavailable: boolean) => void;
}

export const useServiceStatusStore = create<ServiceStatusStore>((set) => ({
  isUnavailable: false,
  setUnavailable: (unavailable) => set({ isUnavailable: unavailable }),
}));
