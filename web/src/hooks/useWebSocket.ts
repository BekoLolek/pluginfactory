import { useEffect, useRef } from 'react';
import { useBuild } from './useBuilds';
import type { BuildSession } from '@/types';

/**
 * Polling-based "WebSocket" hook.
 * Refetches the build session every 2 seconds and calls onMessage
 * when the build data changes (status or phase transition).
 *
 * A real STOMP WebSocket implementation can replace this later.
 */
export function useBuildProgress(
  sessionId: string,
  onMessage: (event: BuildSession) => void,
) {
  const { data: build } = useBuild(sessionId);
  const prevRef = useRef<string | null>(null);

  useEffect(() => {
    if (!build) return;

    const key = `${build.status}:${build.currentPhase}`;
    if (prevRef.current !== null && prevRef.current !== key) {
      onMessage(build);
    }
    prevRef.current = key;
  }, [build, onMessage]);
}
