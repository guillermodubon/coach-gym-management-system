import { useState } from "react";
import type { PropsWithChildren } from "react";
import { QueryClientProvider } from "@tanstack/react-query";
import { AuthProvider } from "../features/auth/AuthProvider";
import { createQueryClient } from "../shared/api/query-client";
import { ThemeProvider } from "../shared/theme/ThemeProvider";

export function AppProviders({ children }: PropsWithChildren) {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider>
        <AuthProvider queryClient={queryClient}>{children}</AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}
