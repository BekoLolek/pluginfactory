import client from './client';

export interface SystemStatus {
  maintenance: boolean;
  discordUrl: string;
}

/** Public, unauthenticated system status (maintenance flag + Discord link). */
export async function getSystemStatus(): Promise<SystemStatus> {
  const { data } = await client.get<SystemStatus>('/api/v1/system/status');
  return data;
}
