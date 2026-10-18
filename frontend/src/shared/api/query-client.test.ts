import { afterEach, describe, expect, it } from "vitest";
import { MutationObserver, onlineManager } from "@tanstack/react-query";
import { createQueryClient, clearPrivateData } from "./query-client";
import { createApiClient } from "./client";
import { ApiError } from "./errors";

afterEach(() => onlineManager.setOnline(true));

describe("explicit private query policies", () => {
  it.each([401, 403, 409, 429, 500])(
    "does not retry a rejected query with HTTP %s",
    async (status) => {
      const queryClient = createQueryClient();
      let calls = 0;
      await expect(
        queryClient.fetchQuery({
          queryKey: ["synthetic-query"],
          queryFn: () => {
            calls++;
            throw new ApiError("http", { status });
          },
        }),
      ).rejects.toMatchObject({ status });
      expect(calls).toBe(1);
      queryClient.clear();
    },
  );

  it("does not queue or retry a mutation when offline or reconnected", async () => {
    const queryClient = createQueryClient();
    onlineManager.setOnline(false);
    let calls = 0;
    const observer = new MutationObserver(queryClient, {
      mutationFn: async () => {
        calls++;
        throw new ApiError("network");
      },
    });
    await expect(observer.mutate()).rejects.toMatchObject({ kind: "network" });
    onlineManager.setOnline(true);
    expect(calls).toBe(1);
    expect(observer.getCurrentResult().isPaused).toBe(false);
    queryClient.clear();
  });

  it("disables background refetch and polling until identity orchestration is implemented", () => {
    const options = createQueryClient().getDefaultOptions();
    expect(options.queries).toMatchObject({
      gcTime: 0,
      staleTime: 0,
      retry: false,
      refetchInterval: false,
      refetchOnMount: false,
      refetchOnWindowFocus: false,
      refetchOnReconnect: false,
      networkMode: "always",
    });
    expect(options.mutations).toMatchObject({
      retry: false,
      gcTime: 0,
      networkMode: "always",
    });
  });

  it("cancels active queries and clears private data without accepting late completion", async () => {
    const queryClient = createQueryClient();
    const apiClient = createApiClient();
    queryClient.setQueryData(["synthetic-private-cache"], {
      name: "Synthetic cached value",
    });
    let finish: ((value: string) => void) | undefined;
    let aborted = false;
    const pending = queryClient
      .fetchQuery({
        queryKey: ["synthetic-in-flight"],
        queryFn: ({ signal }) => {
          signal.addEventListener("abort", () => {
            aborted = true;
          });
          return new Promise<string>((resolve) => {
            finish = resolve;
          });
        },
      })
      .catch((error: unknown) => error);
    await clearPrivateData(queryClient, apiClient);
    finish?.("Synthetic obsolete result");
    await pending;
    expect(aborted).toBe(true);
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
    expect(queryClient.getMutationCache().getAll()).toHaveLength(0);
  });
});
