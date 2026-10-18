import { describe, expect, it } from "vitest";
import { identityFixture } from "../../test/identity-fixtures";
import { ApiError } from "./errors";
import { parseCurrentUser } from "./contracts";

describe("current-user contract diagnostics", () => {
  it("classifies an unknown string scope without exposing its value", () => {
    try {
      parseCurrentUser({ ...identityFixture, organizationScope: "REGION" });
      throw new Error("La respuesta debía rechazarse.");
    } catch (error) {
      expect(error).toBeInstanceOf(ApiError);
      expect(error).toMatchObject({
        kind: "invalid-response",
        clientReason: "unsupported-authority",
      });
      expect((error as Error).message).not.toContain("REGION");
    }
  });

  it.each([undefined, null, 1, false])(
    "keeps a missing or non-string scope as a generic invalid response (%s)",
    (organizationScope) => {
      expect(() =>
        parseCurrentUser({ ...identityFixture, organizationScope }),
      ).toThrowError(
        expect.objectContaining({
          kind: "invalid-response",
          clientReason: undefined,
        }),
      );
    },
  );
});
