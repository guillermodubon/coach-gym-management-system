type MediaListener = (event: MediaQueryListEvent) => void;

class MockMediaQueryList {
  readonly media: string;
  matches: boolean;
  onchange: MediaListener | null = null;
  private readonly listeners = new Set<MediaListener>();
  private readonly eventListeners = new Map<
    EventListenerOrEventListenerObject,
    MediaListener
  >();

  constructor(query: string, matches: boolean) {
    this.media = query;
    this.matches = matches;
  }

  addListener(listener: MediaListener | null): void {
    if (listener) this.listeners.add(listener);
  }

  removeListener(listener: MediaListener | null): void {
    if (listener) this.listeners.delete(listener);
  }

  addEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject | null,
  ): void {
    if (type === "change" && listener) {
      const wrapped = (event: MediaQueryListEvent) => {
        if (typeof listener === "function") listener(event);
        else listener.handleEvent(event);
      };
      this.eventListeners.set(listener, wrapped);
      this.listeners.add(wrapped);
    }
  }

  removeEventListener(
    type: string,
    listener: EventListenerOrEventListenerObject | null,
  ): void {
    if (type !== "change" || !listener) return;
    const wrapped = this.eventListeners.get(listener);
    if (wrapped) this.listeners.delete(wrapped);
    this.eventListeners.delete(listener);
  }

  dispatchEvent(event: Event): boolean {
    return event.type === "change";
  }

  change(matches: boolean): void {
    this.matches = matches;
    const event = Object.assign(new Event("change"), {
      matches,
      media: this.media,
    }) as MediaQueryListEvent;
    this.onchange?.call(this as unknown as MediaQueryList, event);
    for (const listener of this.listeners)
      listener.call(this as unknown as MediaQueryList, event);
  }
}

const mediaQueries = new Map<string, MockMediaQueryList>();

function defaultMatch(query: string): boolean {
  if (query.includes("prefers-color-scheme: dark")) return false;
  if (query.includes("prefers-reduced-motion: reduce")) return false;

  const minimum = /min-width:\s*(\d+)px/.exec(query);
  if (minimum) return window.innerWidth >= Number(minimum[1]);
  const maximum = /max-width:\s*(\d+)px/.exec(query);
  if (maximum) return window.innerWidth <= Number(maximum[1]);
  return false;
}

Object.defineProperty(window, "matchMedia", {
  configurable: true,
  value(query: string): MediaQueryList {
    let media = mediaQueries.get(query);
    if (!media) {
      media = new MockMediaQueryList(query, defaultMatch(query));
      mediaQueries.set(query, media);
    }
    return media as unknown as MediaQueryList;
  },
});

export function setMockMediaQuery(query: string, matches: boolean): void {
  let media = mediaQueries.get(query);
  if (!media) {
    media = new MockMediaQueryList(query, matches);
    mediaQueries.set(query, media);
    return;
  }
  media.change(matches);
}

class MockResizeObserver implements ResizeObserver {
  observe(): void {}
  unobserve(): void {}
  disconnect(): void {}
}

Object.defineProperty(globalThis, "ResizeObserver", {
  configurable: true,
  value: MockResizeObserver,
});
