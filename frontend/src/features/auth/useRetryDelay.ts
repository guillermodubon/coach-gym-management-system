import { useEffect, useState } from "react";
import type { ApiError } from "../../shared/api/errors";

export function useRetryDelay(error: ApiError | null): number {
  const [secondsRemaining, setSecondsRemaining] = useState(0);

  useEffect(() => {
    if (error?.status !== 429 || error.retryAfterSeconds === undefined) {
      setSecondsRemaining(0);
      return;
    }

    const retryAt = Date.now() + error.retryAfterSeconds * 1000;
    const update = () =>
      setSecondsRemaining(
        Math.max(0, Math.ceil((retryAt - Date.now()) / 1000)),
      );
    update();
    const timer = window.setInterval(update, 250);
    return () => window.clearInterval(timer);
  }, [error]);

  return secondsRemaining;
}
