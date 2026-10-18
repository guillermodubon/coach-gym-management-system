import { QueryClient } from "@tanstack/react-query";
import type { ApiClient } from "./client";

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
        networkMode: "always",
        staleTime: 0,
        gcTime: 0,
        refetchInterval: false,
        refetchOnMount: false,
        refetchOnWindowFocus: false,
        refetchOnReconnect: false,
      },
      mutations: {
        retry: false,
        networkMode: "always",
        gcTime: 0,
      },
    },
  });
}

/** Infrastructure only. Session navigation and when to call it belong to B04. */
export async function clearPrivateData(
  queryClient: QueryClient,
  apiClient: ApiClient,
): Promise<void> {
  apiClient.reset();
  await queryClient.cancelQueries();
  queryClient.clear();
}
