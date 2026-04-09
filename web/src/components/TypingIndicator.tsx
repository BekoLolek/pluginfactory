/**
 * Assistant-side "typing…" bubble shown while a chat request is in flight.
 * Mirrors the styling of an assistant ChatMessage so the placeholder feels
 * like the next message materializing rather than a separate UI element.
 */
export default function TypingIndicator() {
  return (
    <div
      className="flex justify-start animate-[fadeIn_0.2s_ease-in-out]"
      aria-live="polite"
      aria-label="Assistant is typing"
    >
      <div className="max-w-[80%] rounded-2xl rounded-bl-md bg-slate-700 px-4 py-3">
        <div className="flex items-center gap-1.5 h-5">
          <span
            className="w-2 h-2 rounded-full bg-slate-300"
            style={{
              animation: 'typingDot 1.2s infinite ease-in-out',
              animationDelay: '0ms',
            }}
          />
          <span
            className="w-2 h-2 rounded-full bg-slate-300"
            style={{
              animation: 'typingDot 1.2s infinite ease-in-out',
              animationDelay: '160ms',
            }}
          />
          <span
            className="w-2 h-2 rounded-full bg-slate-300"
            style={{
              animation: 'typingDot 1.2s infinite ease-in-out',
              animationDelay: '320ms',
            }}
          />
        </div>
      </div>
    </div>
  );
}
