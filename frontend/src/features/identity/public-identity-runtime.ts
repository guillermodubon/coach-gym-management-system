export type PublicIdentityPurpose = "password-recovery" | "invitation";

export type PublicIdentityTokenSnapshot =
  | { readonly kind: "empty" }
  | {
      readonly kind: "available";
      readonly purpose: PublicIdentityPurpose;
      readonly token: string;
    }
  | {
      readonly kind: "invalid";
      readonly purpose: PublicIdentityPurpose;
      readonly reason: "query-token" | "malformed";
    };

export interface PublicIdentityRuntime {
  getSnapshot(): PublicIdentityTokenSnapshot;
  subscribe(listener: () => void): () => void;
  clear(): void;
}

const emptySnapshot: PublicIdentityTokenSnapshot = Object.freeze({
  kind: "empty",
});

const purposeByPath: Readonly<Record<string, PublicIdentityPurpose>> = {
  "/recover-password": "password-recovery",
  "/accept-invitation": "invitation",
};

export function createPublicIdentityRuntime(
  initial: PublicIdentityTokenSnapshot = emptySnapshot,
): PublicIdentityRuntime {
  let snapshot = initial;
  const listeners = new Set<() => void>();

  return {
    getSnapshot: () => snapshot,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    clear() {
      if (snapshot.kind === "empty") return;
      snapshot = emptySnapshot;
      for (const listener of listeners) listener();
    },
  };
}

export const emptyPublicIdentityRuntime = createPublicIdentityRuntime();

/**
 * Reads only the backend's opaque URL-safe token shape, then removes fragments
 * and query credentials from the current history entry before returning.
 */
export function capturePublicIdentityRuntime(
  location: Pick<Location, "pathname" | "search" | "hash">,
  history: Pick<History, "state" | "replaceState">,
): PublicIdentityRuntime {
  const purpose = purposeByPath[location.pathname];
  const searchParams = new URLSearchParams(location.search);
  const queryTokenNames = [...searchParams.keys()].filter(
    (name) => name.toLowerCase() === "token",
  );
  const queryTokenPresent = queryTokenNames.length > 0;
  const rawHash = location.hash;

  for (const name of queryTokenNames) searchParams.delete(name);

  const cleanedSearch = searchParams.toString();
  const safeUrl = `${location.pathname}${cleanedSearch ? `?${cleanedSearch}` : ""}`;
  if (rawHash || queryTokenPresent) {
    history.replaceState(history.state, "", safeUrl);
  }

  if (!purpose) return createPublicIdentityRuntime();
  if (queryTokenPresent) {
    return createPublicIdentityRuntime({
      kind: "invalid",
      purpose,
      reason: "query-token",
    });
  }
  if (!rawHash) return createPublicIdentityRuntime();

  const match = /^#token=([A-Za-z0-9_-]{43})$/.exec(rawHash);
  if (!match?.[1]) {
    return createPublicIdentityRuntime({
      kind: "invalid",
      purpose,
      reason: "malformed",
    });
  }

  return createPublicIdentityRuntime({
    kind: "available",
    purpose,
    token: match[1],
  });
}
