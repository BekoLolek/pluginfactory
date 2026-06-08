import {
  useQuery,
  useMutation,
  useQueryClient,
  useIsMutating,
} from '@tanstack/react-query';
import {
  createBuild,
  getBuilds,
  getBuild,
  getMessages,
  sendMessage,
  getBudget,
  getPlan,
  approvePlan,
  revisePlan,
  requestIteration,
  getArtifacts,
  deleteBuild,
  recoverBuild,
} from '@/api/builds';
import type { BuildSession, ChatMessage } from '@/types';

export function useCreateBuild() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: createBuild,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['builds'] });
    },
  });
}

export function useBuilds(page: number, size = 10) {
  return useQuery({
    queryKey: ['builds', page, size],
    queryFn: () => getBuilds(page, size),
  });
}

export function useBuild(id: string) {
  return useQuery({
    queryKey: ['build', id],
    queryFn: () => getBuild(id),
    enabled: !!id,
  });
}

export function useMessages(sessionId: string) {
  const { data: build } = useBuild(sessionId);
  const isActive = build
    ? !(['COMPLETED', 'FAILED', 'CANCELLED'] as string[]).includes(
        build.status,
      )
    : false;

  // Pause the 5-second refetch while a message is being sent.
  // Without this, the interval fires mid-mutation, fetches stale
  // server data (user message not stored yet), and clobbers the
  // optimistic update — making the user's message disappear until
  // the AI finishes responding.
  const isSending =
    useIsMutating({ mutationKey: ['sendMessage', sessionId] }) > 0;

  return useQuery({
    queryKey: ['messages', sessionId],
    queryFn: () => getMessages(sessionId),
    enabled: !!sessionId,
    refetchInterval: isActive && !isSending ? 5000 : false,
  });
}

export interface SendMessageVars {
  content: string;
  /** Build-form "skip questions" toggle — go straight to plan generation. */
  skipClarification?: boolean;
}

export function useSendMessage(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation<
    ChatMessage,
    Error,
    SendMessageVars,
    { previous: ChatMessage[] | undefined }
  >({
    mutationKey: ['sendMessage', sessionId],
    mutationFn: ({ content, skipClarification }: SendMessageVars) =>
      sendMessage(sessionId, content, skipClarification),
    // Optimistically append the user's message to the cached list so it
    // appears instantly in the UI. The refetch in onSettled will replace
    // this placeholder with the server-of-truth messages (which include
    // the assistant's reply) once the request completes.
    onMutate: async ({ content }) => {
      await queryClient.cancelQueries({
        queryKey: ['messages', sessionId],
      });
      const previous = queryClient.getQueryData<ChatMessage[]>([
        'messages',
        sessionId,
      ]);
      const optimistic: ChatMessage = {
        id: `optimistic-${Date.now()}`,
        role: 'user',
        content,
        modelUsed: null,
        tokensConsumed: 0,
        createdAt: new Date().toISOString(),
      };
      queryClient.setQueryData<ChatMessage[]>(
        ['messages', sessionId],
        (old) => [...(old ?? []), optimistic],
      );
      return { previous };
    },
    onError: (_err, _content, context) => {
      if (context?.previous !== undefined) {
        queryClient.setQueryData(
          ['messages', sessionId],
          context.previous,
        );
      }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({
        queryKey: ['messages', sessionId],
      });
      void queryClient.invalidateQueries({ queryKey: ['build', sessionId] });
      void queryClient.invalidateQueries({ queryKey: ['budget', sessionId] });
    },
  });
}

export function useTokenBudget(sessionId: string) {
  return useQuery({
    queryKey: ['budget', sessionId],
    queryFn: () => getBudget(sessionId),
    enabled: !!sessionId,
  });
}

export function usePlan(sessionId: string) {
  const { data: build } = useBuild(sessionId);
  const planStatuses: string[] = [
    'PLANNING',
    'APPROVED',
    'BUILDING',
    'TESTING',
    'COMPLETED',
  ];
  const enabled = !!build && planStatuses.includes(build.status);

  return useQuery({
    queryKey: ['plan', sessionId],
    queryFn: () => getPlan(sessionId),
    enabled,
    // Plan may not exist yet when the session first enters PLANNING
    // (PlanGenerationAgent is still running). Treat 404 as "not ready"
    // rather than an error so React Query retries silently.
    retry: (failureCount, error) => {
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status === 404) return failureCount < 5;
      return failureCount < 2;
    },
    retryDelay: (attempt) => Math.min(1000 * 2 ** attempt, 8000),
  });
}

export function useApprovePlan(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => approvePlan(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['build', sessionId] });
      void queryClient.invalidateQueries({ queryKey: ['plan', sessionId] });
    },
  });
}

export function useRevisePlan(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (feedback: string) => revisePlan(sessionId, feedback),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['build', sessionId] });
      void queryClient.invalidateQueries({ queryKey: ['plan', sessionId] });
      void queryClient.invalidateQueries({
        queryKey: ['messages', sessionId],
      });
    },
  });
}

export function useDeleteBuild() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (sessionId: string) => deleteBuild(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['builds'] });
    },
  });
}

export function useIterate(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (feedback: string) => requestIteration(sessionId, feedback),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['build', sessionId] });
      void queryClient.invalidateQueries({
        queryKey: ['messages', sessionId],
      });
    },
  });
}

export function useRecoverBuild(sessionId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => recoverBuild(sessionId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['build', sessionId] });
      void queryClient.invalidateQueries({
        queryKey: ['messages', sessionId],
      });
      void queryClient.invalidateQueries({
        queryKey: ['iterations', sessionId],
      });
    },
  });
}

export function useArtifacts(sessionId: string) {
  const { data: build } = useBuild(sessionId);
  const enabled =
    !!build &&
    (['COMPLETED', 'FAILED'] as string[]).includes(build.status);

  return useQuery({
    queryKey: ['artifacts', sessionId],
    queryFn: () => getArtifacts(sessionId),
    enabled,
  });
}

/** Wrapper for polling the build session during active phases */
export function useBuildPolling(sessionId: string) {
  const queryResult = useBuild(sessionId);
  const build = queryResult.data as BuildSession | undefined;
  const isActive = build
    ? (['BUILDING', 'TESTING', 'APPROVED'] as string[]).includes(build.status)
    : false;

  return useQuery({
    queryKey: ['build', sessionId],
    queryFn: () => getBuild(sessionId),
    enabled: !!sessionId,
    refetchInterval: isActive ? 2000 : false,
  });
}
