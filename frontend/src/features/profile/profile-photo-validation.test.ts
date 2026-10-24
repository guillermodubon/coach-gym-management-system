import { describe, expect, it } from "vitest";
import {
  maxProfilePhotoBytes,
  validateProfilePhotoFile,
} from "./profile-photo-validation";

describe("profile photo validation", () => {
  it.each(["image/jpeg", "image/png", "image/webp"])(
    "accepts %s at the exact size limit",
    (type) => {
      expect(
        validateProfilePhotoFile({ size: maxProfilePhotoBytes, type }),
      ).toBeNull();
    },
  );

  it("rejects empty images, oversized files, and active content", () => {
    expect(validateProfilePhotoFile({ size: 0, type: "image/png" })).toBe(
      "empty",
    );
    expect(
      validateProfilePhotoFile({
        size: maxProfilePhotoBytes + 1,
        type: "image/png",
      }),
    ).toBe("too-large");
    expect(validateProfilePhotoFile({ size: 20, type: "image/svg+xml" })).toBe(
      "unsupported-type",
    );
  });
});
