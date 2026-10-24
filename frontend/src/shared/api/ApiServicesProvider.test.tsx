import type { PropsWithChildren } from "react";
import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ApiServicesProvider, useApiServices } from "./ApiServicesProvider";

describe("shared API services provider", () => {
  it("keeps the same client and API instances across consumer rerenders", () => {
    function wrapper({ children }: PropsWithChildren) {
      return <ApiServicesProvider>{children}</ApiServicesProvider>;
    }
    const { result, rerender } = renderHook(() => useApiServices(), {
      wrapper,
    });
    const first = result.current;
    rerender();
    expect(result.current).toBe(first);
    expect(result.current.apiClient).toBe(first.apiClient);
    expect(result.current.identityApi).toBe(first.identityApi);
    expect(result.current.profileApi).toBe(first.profileApi);
    expect(result.current.publicIdentityApi).toBe(first.publicIdentityApi);
  });
});
