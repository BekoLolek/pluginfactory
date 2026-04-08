import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthResponse } from '@/types';

interface AuthUser {
  id: string;
  email: string;
  displayName: string;
  discordId: string;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  user: AuthUser | null;
  isAuthenticated: () => boolean;
  login: (response: AuthResponse) => void;
  logout: () => void;
  refresh: (response: AuthResponse) => void;
  updateUser: (partial: Partial<AuthUser>) => void;
}

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return payload.exp * 1000 < Date.now();
  } catch {
    return true;
  }
}

// Store tokens in localStorage so users stay logged in across browser restarts.
// The 7-day refresh token bounds the persistence window; logout clears it.
function getStoredTokens(): { accessToken: string | null; refreshToken: string | null } {
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

function setStoredTokens(accessToken: string | null, refreshToken: string | null) {
  if (accessToken && refreshToken) {
    localStorage.setItem('auth-tokens', JSON.stringify({ accessToken, refreshToken }));
  } else {
    localStorage.removeItem('auth-tokens');
  }
}

const initialTokens = getStoredTokens();

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: initialTokens.accessToken,
      refreshToken: initialTokens.refreshToken,
      user: null,

      isAuthenticated: () => {
        const token = get().accessToken;
        return token !== null && get().user !== null && !isTokenExpired(token);
      },

      login: (response: AuthResponse) => {
        setStoredTokens(response.accessToken, response.refreshToken);
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          user: response.user,
        });
      },

      logout: () => {
        setStoredTokens(null, null);
        set({
          accessToken: null,
          refreshToken: null,
          user: null,
        });
      },

      refresh: (response: AuthResponse) => {
        setStoredTokens(response.accessToken, response.refreshToken);
        set({
          accessToken: response.accessToken,
          refreshToken: response.refreshToken,
          user: response.user,
        });
      },

      updateUser: (partial: Partial<AuthUser>) => {
        set((state) => ({
          user: state.user ? { ...state.user, ...partial } : null,
        }));
      },
    }),
    {
      name: 'auth-storage',
      partialize: (state) => ({
        // Only persist user info to localStorage, NOT tokens
        user: state.user,
      }),
    },
  ),
);
