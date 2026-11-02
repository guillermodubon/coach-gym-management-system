import { useState } from "react";
import type { PropsWithChildren } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../features/auth/AuthProvider";
import { ApiServicesProvider } from "../shared/api/ApiServicesProvider";
import { createQueryClient } from "../shared/api/query-client";
import { ThemeProvider } from "../shared/theme/ThemeProvider";
import { PublicIdentityProvider } from "../features/identity/PublicIdentityProvider";
import { ContextChangeGuardProvider } from "../features/branch-context/ContextChangeGuardProvider";
import {
  emptyPublicIdentityRuntime,
  type PublicIdentityRuntime,
} from "../features/identity/public-identity-runtime";

export function AppProviders({
  children,
  publicIdentityRuntime,
}: PropsWithChildren<{ publicIdentityRuntime?: PublicIdentityRuntime }>) {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <ApiServicesProvider>
        <ThemeProvider>
          <PublicIdentityProvider
            runtime={publicIdentityRuntime ?? emptyPublicIdentityRuntime}
          >
            <ContextChangeGuardProvider>
              <AuthProvider queryClient={queryClient}>{children}</AuthProvider>
            </ContextChangeGuardProvider>
          </PublicIdentityProvider>
        </ThemeProvider>
      </ApiServicesProvider>
    </QueryClientProvider>
  );
}
