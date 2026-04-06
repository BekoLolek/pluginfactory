import client from './client';
import type { AuthResponse } from '@/types';

export async function getDiscordUrl(): Promise<string> {
  const { data } = await client.get<{ url: string }>('/api/v1/auth/discord');
  return data.url;
}

export async function handleCallback(code: string, state: string): Promise<AuthResponse> {
  const { data } = await client.get<AuthResponse>(
    `/api/v1/auth/discord/callback?code=${encodeURIComponent(code)}&state=${encodeURIComponent(state)}`,
  );
  return data;
}

export async function refreshToken(token: string): Promise<AuthResponse> {
  const { data } = await client.post<AuthResponse>('/api/v1/auth/refresh', {
    refreshToken: token,
  });
  return data;
}

export async function logout(): Promise<void> {
  await client.post('/api/v1/auth/logout');
}
