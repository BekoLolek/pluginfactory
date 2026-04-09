import { useEffect, useRef, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  useBuild,
  useMessages,
  useSendMessage,
  useTokenBudget,
  usePlan,
  useBuildPolling,
} from '@/hooks/useBuilds';
import ChatMessage from '@/components/ChatMessage';
import ChatInput from '@/components/ChatInput';
import TokenBudgetBar from '@/components/TokenBudgetBar';
import BuildStatusBadge from '@/components/BuildStatusBadge';
import PlanReviewPanel from '@/components/PlanReviewPanel';
import BuildProgressPanel from '@/components/BuildProgressPanel';
import LoadingSkeleton from '@/components/LoadingSkeleton';
import TypingIndicator from '@/components/TypingIndicator';
import StarterQuestionnaire from '@/components/StarterQuestionnaire';

export default function BuildDetailPage() {
  const { id } = useParams<{ id: string }>();
  const sessionId = id ?? '';

  const { data: build, isLoading, isError } = useBuild(sessionId);
  const { data: messages } = useMessages(sessionId);
  const { data: budget } = useTokenBudget(sessionId);
  const { data: plan } = usePlan(sessionId);
  const sendMessage = useSendMessage(sessionId);

  // Poll build during active build/test phases
  useBuildPolling(sessionId);

  // Auto-scroll chat to bottom on new messages or while the assistant is
  // thinking (so the typing indicator is always visible).
  const chatEndRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sendMessage.isPending]);

  // The starter questionnaire shows on a fresh session until the user
  // either answers it, sends their first message, or explicitly skips it.
  // Skip state is local-only — it resets on page reload, which is fine
  // because once there's a real message in the chat the questionnaire
  // won't reappear anyway.
  const [questionnaireSkipped, setQuestionnaireSkipped] = useState(false);

  if (isLoading) {
    return (
      <div className="space-y-4">
        <LoadingSkeleton variant="text-block" count={1} />
        <LoadingSkeleton variant="card" count={2} />
      </div>
    );
  }

  if (isError || !build) {
    return (
      <div className="text-center py-20">
        <div className="w-12 h-12 rounded-full bg-red-600/10 flex items-center justify-center mx-auto mb-4">
          <svg
            className="w-6 h-6 text-red-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z"
            />
          </svg>
        </div>
        <p className="text-red-400 mb-4">
          {isError ? 'Failed to load build session.' : 'Build not found'}
        </p>
        <Link
          to="/dashboard/builds"
          className="text-sm text-blue-400 hover:text-blue-300 transition-colors"
        >
          Back to builds
        </Link>
      </div>
    );
  }

  const isChatting = build.status === 'CHATTING';
  const isPlanning = build.status === 'PLANNING';
  const isBuilding =
    build.status === 'BUILDING' ||
    build.status === 'TESTING' ||
    build.status === 'APPROVED';
  const isCompleted = build.status === 'COMPLETED';
  const isFailed = build.status === 'FAILED';
  const isCancelled = build.status === 'CANCELLED';
  const showChat = isChatting || isPlanning;

  const handleSend = (content: string) => {
    sendMessage.mutate(content);
  };

  return (
    <div className="h-full flex flex-col">
      {/* Header */}
      <div className="shrink-0 mb-4">
        <div className="flex items-center gap-3 mb-1">
          <Link
            to="/dashboard/builds"
            className="text-slate-400 hover:text-white transition-colors"
          >
            <svg
              className="w-5 h-5"
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M15 19l-7-7 7-7"
              />
            </svg>
          </Link>
          <h1 className="text-2xl font-bold text-white">
            Build {build.id.slice(0, 8)}
          </h1>
          <BuildStatusBadge status={build.status} />
        </div>
        <p className="text-slate-500 text-xs ml-8">
          Created {new Date(build.createdAt).toLocaleString()}
          {build.currentPhase !== 'IDLE' && (
            <span className="ml-3 text-slate-600">
              Phase: {build.currentPhase}
            </span>
          )}
        </p>
      </div>

      {/* Budget bar */}
      {budget && (
        <div className="shrink-0 mb-4">
          <TokenBudgetBar budget={budget} />
        </div>
      )}

      {/* Main content area - based on status */}
      <div className="flex-1 min-h-0 flex flex-col">
        {/* CHATTING state: Chat interface */}
        {showChat && (
          <div className="flex flex-col bg-slate-900 border border-slate-800 rounded-xl overflow-hidden">
            {/*
             * Messages scroll region.
             *
             * We use an explicit viewport-relative max-height instead of
             * `flex-1 min-h-0` because DashboardLayout's inner wrapper
             * isn't a flex column with a bounded height — so `flex-1`
             * would grow to content size and push the outer <main> to
             * scroll, which is what "chat expands all the way down"
             * looked like. Capping here forces the chat area itself to
             * be the scroll container.
             */}
            <div
              className="overflow-y-auto p-4 space-y-4 min-h-[20rem] max-h-[calc(100vh-22rem)]"
              aria-live="polite"
              aria-relevant="additions"
            >
              {(!messages || messages.length === 0) &&
                !sendMessage.isPending &&
                (isChatting && !questionnaireSkipped ? (
                  <div className="flex items-center justify-center min-h-full py-6">
                    <StarterQuestionnaire
                      onSubmit={handleSend}
                      onSkip={() => setQuestionnaireSkipped(true)}
                      disabled={sendMessage.isPending}
                    />
                  </div>
                ) : (
                  <div className="flex items-center justify-center h-full">
                    <div className="text-center">
                      <div className="w-12 h-12 rounded-full bg-blue-600/10 flex items-center justify-center mx-auto mb-3">
                        <svg
                          className="w-6 h-6 text-blue-400"
                          fill="none"
                          stroke="currentColor"
                          viewBox="0 0 24 24"
                        >
                          <path
                            strokeLinecap="round"
                            strokeLinejoin="round"
                            strokeWidth={2}
                            d="M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z"
                          />
                        </svg>
                      </div>
                      <p className="text-slate-400 text-sm">
                        Describe your Minecraft plugin idea to get started.
                      </p>
                    </div>
                  </div>
                ))}
              {messages?.map((msg) => (
                <ChatMessage key={msg.id} message={msg} />
              ))}
              {sendMessage.isPending && <TypingIndicator />}
              <div ref={chatEndRef} />
            </div>

            {/* Chat input */}
            {isChatting && (
              <ChatInput
                onSend={handleSend}
                disabled={sendMessage.isPending}
                placeholder="Describe your plugin idea..."
              />
            )}
          </div>
        )}

        {/* PLANNING state: Show plan review below chat */}
        {isPlanning && plan && (
          <div className="mt-4">
            <PlanReviewPanel plan={plan} sessionId={sessionId} />
          </div>
        )}

        {/* BUILDING / TESTING / APPROVED state: Build progress */}
        {isBuilding && (
          <div className="flex-1">
            <BuildProgressPanel session={build} />
            {plan && (
              <div className="mt-4">
                <PlanReviewPanel
                  plan={plan}
                  sessionId={sessionId}
                  readonly
                />
              </div>
            )}
          </div>
        )}

        {/* COMPLETED state */}
        {isCompleted && (
          <div className="flex-1">
            <BuildProgressPanel session={build} />
            {plan && (
              <div className="mt-4">
                <PlanReviewPanel
                  plan={plan}
                  sessionId={sessionId}
                  readonly
                />
              </div>
            )}
          </div>
        )}

        {/* FAILED state */}
        {isFailed && (
          <div className="flex-1">
            <BuildProgressPanel session={build} />
            {plan && (
              <div className="mt-4">
                <PlanReviewPanel
                  plan={plan}
                  sessionId={sessionId}
                  readonly
                />
              </div>
            )}
          </div>
        )}

        {/* CANCELLED state */}
        {isCancelled && (
          <div className="flex-1">
            <div className="bg-slate-900 border border-slate-800 rounded-xl p-8 text-center">
              <div className="w-12 h-12 rounded-full bg-slate-800 flex items-center justify-center mx-auto mb-4">
                <svg
                  className="w-6 h-6 text-slate-500"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M6 18L18 6M6 6l12 12"
                  />
                </svg>
              </div>
              <p className="text-slate-400 text-sm">
                This build session was cancelled.
              </p>
              <Link
                to="/dashboard/builds/new"
                className="inline-block mt-4 text-sm text-blue-400 hover:text-blue-300 transition-colors"
              >
                Start a new build
              </Link>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
