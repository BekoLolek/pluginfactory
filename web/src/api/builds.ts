import client from './client';
import type {
  BuildSession,
  ChatMessage,
  TokenBudget,
  PlanDocument,
  BuildIteration,
  Artifact,
} from '@/types';

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function createBuild(): Promise<BuildSession> {
  const { data } = await client.post<BuildSession>('/api/v1/builds');
  return data;
}

export async function getBuilds(
  page = 0,
  size = 10,
): Promise<PageResponse<BuildSession>> {
  const { data } = await client.get<PageResponse<BuildSession>>(
    '/api/v1/builds',
    { params: { page, size } },
  );
  return data;
}

export async function getBuild(id: string): Promise<BuildSession> {
  const { data } = await client.get<BuildSession>(`/api/v1/builds/${id}`);
  return data;
}

export async function getMessages(sessionId: string): Promise<ChatMessage[]> {
  const { data } = await client.get<ChatMessage[]>(
    `/api/v1/builds/${sessionId}/messages`,
  );
  return data;
}

export async function sendMessage(
  sessionId: string,
  content: string,
): Promise<ChatMessage> {
  const { data } = await client.post<ChatMessage>(
    `/api/v1/builds/${sessionId}/messages`,
    { content },
  );
  return data;
}

export async function getBudget(sessionId: string): Promise<TokenBudget> {
  const { data } = await client.get<TokenBudget>(
    `/api/v1/builds/${sessionId}/budget`,
  );
  return data;
}

export async function getPlan(sessionId: string): Promise<PlanDocument> {
  const { data } = await client.get<PlanDocument>(
    `/api/v1/builds/${sessionId}/plan`,
  );
  return data;
}

export async function approvePlan(sessionId: string): Promise<void> {
  await client.post(`/api/v1/builds/${sessionId}/plan/approve`);
}

export async function revisePlan(
  sessionId: string,
  feedback: string,
): Promise<void> {
  await client.post(`/api/v1/builds/${sessionId}/plan/revise`, { feedback });
}

export async function getIterations(
  sessionId: string,
): Promise<BuildIteration[]> {
  const { data } = await client.get<BuildIteration[]>(
    `/api/v1/builds/${sessionId}/iterations`,
  );
  return data;
}

export async function requestIteration(
  sessionId: string,
  feedback: string,
): Promise<BuildIteration> {
  const { data } = await client.post<BuildIteration>(
    `/api/v1/builds/${sessionId}/iterate`,
    { feedback },
  );
  return data;
}

export async function deleteBuild(sessionId: string): Promise<void> {
  await client.delete(`/api/v1/builds/${sessionId}`);
}

export async function recoverBuild(
  sessionId: string,
): Promise<BuildIteration> {
  const { data } = await client.post<BuildIteration>(
    `/api/v1/builds/${sessionId}/recover`,
  );
  return data;
}

export async function getArtifacts(sessionId: string): Promise<Artifact[]> {
  const { data } = await client.get<Artifact[]>(
    `/api/v1/builds/${sessionId}/artifacts`,
  );
  return data;
}

export async function downloadArtifact(artifactId: string): Promise<Blob> {
  const { data } = await client.get<Blob>(
    `/api/v1/artifacts/${artifactId}/download`,
    { responseType: 'blob' },
  );
  return data;
}
