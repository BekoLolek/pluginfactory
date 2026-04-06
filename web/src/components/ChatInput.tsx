import { useState, useRef, useCallback, useEffect } from 'react';
import type { KeyboardEvent, ChangeEvent } from 'react';

interface ChatInputProps {
  onSend: (content: string) => void;
  disabled: boolean;
  placeholder?: string;
}

const MAX_CHARS = 4000;
const MIN_ROWS = 1;
const MAX_ROWS = 6;
const LINE_HEIGHT = 24;

export default function ChatInput({
  onSend,
  disabled,
  placeholder = 'Describe your plugin idea...',
}: ChatInputProps) {
  const [value, setValue] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const adjustHeight = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    textarea.style.height = 'auto';
    const scrollHeight = textarea.scrollHeight;
    const maxHeight = MAX_ROWS * LINE_HEIGHT;
    const minHeight = MIN_ROWS * LINE_HEIGHT;
    textarea.style.height = `${Math.min(Math.max(scrollHeight, minHeight), maxHeight)}px`;
  }, []);

  useEffect(() => {
    adjustHeight();
  }, [value, adjustHeight]);

  const handleSend = useCallback(() => {
    const trimmed = value.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setValue('');
  }, [value, disabled, onSend]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent<HTMLTextAreaElement>) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  const handleChange = useCallback(
    (e: ChangeEvent<HTMLTextAreaElement>) => {
      const newValue = e.target.value;
      if (newValue.length <= MAX_CHARS) {
        setValue(newValue);
      }
    },
    [],
  );

  const charCount = value.length;
  const charWarning = charCount > MAX_CHARS * 0.9;

  return (
    <div className="border-t border-slate-700 bg-slate-900 px-4 py-3">
      <div className="flex items-end gap-3">
        <div className="flex-1 relative">
          <textarea
            ref={textareaRef}
            value={value}
            onChange={handleChange}
            onKeyDown={handleKeyDown}
            placeholder={placeholder}
            disabled={disabled}
            rows={MIN_ROWS}
            className="w-full resize-none rounded-xl bg-slate-800 border border-slate-700 px-4 py-3 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-blue-500 focus:ring-1 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            style={{ lineHeight: `${LINE_HEIGHT}px` }}
          />
          <div
            className={`absolute bottom-1.5 right-3 text-[10px] ${
              charWarning ? 'text-amber-400' : 'text-slate-600'
            }`}
          >
            {charCount > 0 && `${charCount} / ${MAX_CHARS}`}
          </div>
        </div>

        <button
          onClick={handleSend}
          disabled={disabled || !value.trim()}
          className="shrink-0 flex items-center justify-center w-10 h-10 rounded-xl bg-blue-600 hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed text-white transition-colors"
          title="Send message"
          aria-label={disabled ? 'Sending message' : 'Send message'}
        >
          {disabled ? (
            <svg
              className="w-5 h-5 animate-spin"
              fill="none"
              viewBox="0 0 24 24"
            >
              <circle
                className="opacity-25"
                cx="12"
                cy="12"
                r="10"
                stroke="currentColor"
                strokeWidth="4"
              />
              <path
                className="opacity-75"
                fill="currentColor"
                d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
              />
            </svg>
          ) : (
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
                d="M12 19V5m0 0l-7 7m7-7l7 7"
              />
            </svg>
          )}
        </button>
      </div>
    </div>
  );
}
