import type { ChatMessage as ChatMessageType } from '@/types';

interface ChatMessageProps {
  message: ChatMessageType;
}

function formatTime(iso: string): string {
  const date = new Date(iso);
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function formatTokens(count: number): string {
  if (count >= 1000) {
    return `${(count / 1000).toFixed(1)}K`;
  }
  return String(count);
}

export default function ChatMessage({ message }: ChatMessageProps) {
  const isUser = message.role === 'user';

  return (
    <div
      className={`flex ${isUser ? 'justify-end' : 'justify-start'} animate-[fadeIn_0.3s_ease-in-out]`}
    >
      <div
        className={`max-w-[80%] rounded-2xl px-4 py-3 ${
          isUser
            ? 'bg-blue-600 text-white rounded-br-md'
            : 'bg-slate-700 text-gray-100 rounded-bl-md'
        }`}
      >
        <div className="whitespace-pre-wrap text-sm leading-relaxed break-words">
          {message.content}
        </div>

        <div
          className={`flex items-center gap-2 mt-2 text-xs ${
            isUser ? 'text-blue-200' : 'text-slate-400'
          }`}
        >
          <span>{formatTime(message.createdAt)}</span>
          {!isUser && message.modelUsed && (
            <>
              <span className="inline-block w-1 h-1 rounded-full bg-current opacity-50" />
              <span className="px-1.5 py-0.5 rounded bg-slate-600 text-slate-300 font-mono text-[10px]">
                {message.modelUsed}
              </span>
            </>
          )}
          {!isUser && message.tokensConsumed > 0 && (
            <>
              <span className="inline-block w-1 h-1 rounded-full bg-current opacity-50" />
              <span>{formatTokens(message.tokensConsumed)} tokens</span>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
