export interface VisibilityEvents {
  addFocusListener(listener: () => void): void;
  removeFocusListener(listener: () => void): void;
  addVisibilityListener(listener: () => void): void;
  removeVisibilityListener(listener: () => void): void;
  isVisible(): boolean;
}

export interface SessionEvents {
  subscribe(listener: () => void): () => void;
  publish(): void;
}

export function createBrowserVisibilityEvents(): VisibilityEvents {
  return {
    addFocusListener: (listener) => window.addEventListener("focus", listener),
    removeFocusListener: (listener) =>
      window.removeEventListener("focus", listener),
    addVisibilityListener: (listener) =>
      document.addEventListener("visibilitychange", listener),
    removeVisibilityListener: (listener) =>
      document.removeEventListener("visibilitychange", listener),
    isVisible: () => document.visibilityState === "visible",
  };
}

export function createBrowserSessionEvents(): SessionEvents {
  let channel: BroadcastChannel | undefined;

  return {
    subscribe(listener) {
      if (typeof BroadcastChannel === "undefined") return () => undefined;

      channel = new BroadcastChannel("coach-gym.auth-events");
      const receive = (event: MessageEvent<unknown>) => {
        const value = event.data;
        if (
          typeof value === "object" &&
          value !== null &&
          "type" in value &&
          value.type === "session-changed"
        ) {
          listener();
        }
      };
      channel.addEventListener("message", receive);
      return () => {
        channel?.removeEventListener("message", receive);
        channel?.close();
        channel = undefined;
      };
    },
    publish() {
      channel?.postMessage({ type: "session-changed" });
    },
  };
}
