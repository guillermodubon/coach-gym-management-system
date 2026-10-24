import { createContext, useContext, useSyncExternalStore } from "react";
import type { PropsWithChildren } from "react";
import {
  emptyPublicIdentityRuntime,
  type PublicIdentityRuntime,
} from "./public-identity-runtime";

const PublicIdentityRuntimeContext = createContext<PublicIdentityRuntime>(
  emptyPublicIdentityRuntime,
);

export function PublicIdentityProvider({
  children,
  runtime = emptyPublicIdentityRuntime,
}: PropsWithChildren<{ runtime?: PublicIdentityRuntime }>) {
  return (
    <PublicIdentityRuntimeContext.Provider value={runtime}>
      {children}
    </PublicIdentityRuntimeContext.Provider>
  );
}

export function usePublicIdentityRuntime(): {
  runtime: PublicIdentityRuntime;
  snapshot: ReturnType<PublicIdentityRuntime["getSnapshot"]>;
} {
  const runtime = useContext(PublicIdentityRuntimeContext);
  const snapshot = useSyncExternalStore(
    runtime.subscribe,
    runtime.getSnapshot,
    runtime.getSnapshot,
  );
  return { runtime, snapshot };
}
