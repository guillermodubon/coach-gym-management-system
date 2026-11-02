import { createContext, useContext, useState } from "react";
import type { PropsWithChildren } from "react";
import {
  createContextChangeGuard,
  type ContextChangeGuard,
} from "./context-change-guard";

const ContextChangeGuardContext = createContext<ContextChangeGuard | null>(
  null,
);

export function ContextChangeGuardProvider({ children }: PropsWithChildren) {
  const [guard] = useState(createContextChangeGuard);
  return (
    <ContextChangeGuardContext.Provider value={guard}>
      {children}
    </ContextChangeGuardContext.Provider>
  );
}

export function useContextChangeGuard(): ContextChangeGuard {
  const guard = useContext(ContextChangeGuardContext);
  if (!guard) {
    throw new Error("Context changes require ContextChangeGuardProvider.");
  }
  return guard;
}
