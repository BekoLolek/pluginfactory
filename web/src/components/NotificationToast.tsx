import { useToastStore } from '@/stores/toastStore';

const typeStyles = {
  success: {
    bg: 'bg-green-900/80 border-green-700/50',
    icon: 'text-green-400',
    iconPath: 'M5 13l4 4L19 7',
  },
  error: {
    bg: 'bg-red-900/80 border-red-700/50',
    icon: 'text-red-400',
    iconPath: 'M6 18L18 6M6 6l12 12',
  },
  info: {
    bg: 'bg-blue-900/80 border-blue-700/50',
    icon: 'text-blue-400',
    iconPath: 'M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z',
  },
  warning: {
    bg: 'bg-amber-900/80 border-amber-700/50',
    icon: 'text-amber-400',
    iconPath:
      'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z',
  },
};

export default function NotificationToast() {
  const toasts = useToastStore((s) => s.toasts);
  const removeToast = useToastStore((s) => s.removeToast);

  if (toasts.length === 0) return null;

  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm" role="status" aria-live="polite">
      {toasts.map((toast) => {
        const style = typeStyles[toast.type];
        return (
          <div
            key={toast.id}
            className={`flex items-start gap-3 px-4 py-3 rounded-xl border backdrop-blur-sm shadow-lg ${style.bg}`}
            style={{ animation: 'fadeIn 0.2s ease-out' }}
          >
            <svg
              className={`w-5 h-5 shrink-0 mt-0.5 ${style.icon}`}
              fill="none"
              stroke="currentColor"
              viewBox="0 0 24 24"
              aria-hidden="true"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d={style.iconPath}
              />
            </svg>
            <p className="text-sm text-white flex-1">{toast.message}</p>
            <button
              onClick={() => removeToast(toast.id)}
              className="text-slate-400 hover:text-white transition-colors shrink-0"
              aria-label="Dismiss notification"
            >
              <svg
                className="w-4 h-4"
                fill="none"
                stroke="currentColor"
                viewBox="0 0 24 24"
                aria-hidden="true"
              >
                <path
                  strokeLinecap="round"
                  strokeLinejoin="round"
                  strokeWidth={2}
                  d="M6 18L18 6M6 6l12 12"
                />
              </svg>
            </button>
          </div>
        );
      })}
    </div>
  );
}
