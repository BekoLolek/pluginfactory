import client from './client';
import type { User, UsageStats, ApiKeyDto } from '@/types';

export async function getMe(): Promise<User> {
  const { data } = await client.get<User>('/api/v1/users/me');
  return data;
}

export async function updateProfile(displayName: string): Promise<User> {
  const { data } = await client.patch<User>('/api/v1/users/me', {
    displayName,
  });
  return data;
}

export async function getUsage(): Promise<UsageStats> {
  const { data } = await client.get<UsageStats>('/api/v1/users/me/usage');
  return data;
}

export async function getApiKeys(): Promise<ApiKeyDto[]> {
  const { data } = await client.get<ApiKeyDto[]>('/api/v1/users/me/api-keys');
  return data;
}

interface CreateApiKeyResponse {
  id: string;
  name: string;
  key: string;
  lastFour: string;
  createdAt: string;
}

export async function createApiKey(
  name: string,
): Promise<CreateApiKeyResponse> {
  const { data } = await client.post<CreateApiKeyResponse>(
    '/api/v1/users/me/api-keys',
    { name },
  );
  return data;
}

export async function revokeApiKey(keyId: string): Promise<void> {
  await client.delete(`/api/v1/users/me/api-keys/${keyId}`);
}
