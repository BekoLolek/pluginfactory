import axios from 'axios';
import type { AuthResponse } from '@/types';
import { useServiceStatusStore } from '@/stores/serviceStatusStore';

const baseURL = import.meta.env.VITE_API_URL || (import.meta.env.PROD ? '' : 'http://localhost:8080');

if (import.meta.env.PROD && !import.meta.env.VITE_API_URL) {
  console.error('VITE_API_URL is not configured for production');
}

const client = axios.create({
  baseURL,
  headers: { 'Content-Type': 'application/json' },
});

let isRefreshing = false;
let failedQueue: Array<{
  resolve: (token: string) => void;
  reject: (error: unknown) => void;
}> = [];

function processQueue(error: unknown, token: string | null) {
  failedQueue.forEach((promise) => {
    if (error) {
      promise.reject(error);
    } else {
      promise.resolve(token!);
    }
  });
  failedQueue = [];
}

function getTokens(): { accessToken: string | null; refreshToken: string | null } {
  try {
    const raw = localStorage.getItem('auth-tokens');
    if (raw) {
      return JSON.parse(raw);
    }
  } catch {
    // ignore
  }
  return { accessToken: null, refreshToken: null };
}

client.interceptors.request.use((config) => {
  const { accessToken } = getTokens();
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`;
  }
  return config;
});

client.interceptors.response.use(
  (response) => {
    // Any successful response proves the backend is reachable — clear
    // the global outage flag so the overlay dismisses itself once the
    // service recovers (e.g. after the user hits "Try again").
    if (useServiceStatusStore.getState().isUnavailable) {
      useServiceStatusStore.getState().setUnavailable(false);
    }
    return response;
  },
  async (error) => {
    const originalRequest = error.config;

    // Detect "the backend is unreachable or gateway is down" conditions
    // and flip the global outage flag. We deliberately exclude 500 —
    // that's often a per-endpoint bug and shouldn't take down the whole
    // app. Network errors (no response at all) and 502/503/504 mean the
    // service itself is the problem.
    const status = error.response?.status;
    const isNetworkError = !error.response;
    const isGatewayError = status === 502 || status === 503 || status === 504;
    if (isNetworkError || isGatewayError) {
      useServiceStatusStore.getState().setUnavailable(true);
    }

    if (error.response?.status !== 401 || originalRequest._retry) {
      return Promise.reject(error);
    }

    const { refreshToken } = getTokens();
    if (!refreshToken) return Promise.reject(error);

    if (isRefreshing) {
      return new Promise<string>((resolve, reject) => {
        failedQueue.push({ resolve, reject });
      }).then((token) => {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return client(originalRequest);
      });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    try {
      const { data } = await axios.post<AuthResponse>(
        `${client.defaults.baseURL}/api/v1/auth/refresh`,
        { refreshToken },
      );

      // Update localStorage with new tokens
      localStorage.setItem(
        'auth-tokens',
        JSON.stringify({
          accessToken: data.accessToken,
          refreshToken: data.refreshToken,
        }),
      );

      // Update localStorage user info
      const storage = JSON.parse(localStorage.getItem('auth-storage') || '{}');
      storage.state = {
        ...storage.state,
        user: data.user,
      };
      localStorage.setItem('auth-storage', JSON.stringify(storage));

      processQueue(null, data.accessToken);

      originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
      return client(originalRequest);
    } catch (refreshError) {
      processQueue(refreshError, null);
      localStorage.removeItem('auth-tokens');
      localStorage.removeItem('auth-storage');
      window.location.href = '/';
      return Promise.reject(refreshError);
    } finally {
      isRefreshing = false;
    }
  },
);

export default client;
