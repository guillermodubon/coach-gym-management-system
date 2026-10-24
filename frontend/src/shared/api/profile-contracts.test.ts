import { describe, expect, it } from "vitest";
import { ApiError } from "./errors";
import {
  parseStaffProfilePhotoMetadata,
  parseStaffSelfProfile,
} from "./profile-contracts";

const profile = {
  userId: "550e8400-e29b-41d4-a716-446655440000",
  username: "operador-prueba",
  email: "operator@example.test",
  firstName: "Ana",
  lastName: "Ríos",
  displayName: "Ana Ríos",
  roles: ["ADMIN"],
  status: "ACTIVE",
  photoPresent: false,
  photoUrl: null,
  version: 0,
};

describe("staff profile contract parsers", () => {
  it("parses a complete profile and retains the nullable photo URL", () => {
    expect(parseStaffSelfProfile(profile)).toEqual(profile);
    expect(
      parseStaffSelfProfile({
        ...profile,
        photoPresent: true,
        photoUrl: "/api/v1/me/profile/photo",
      }).photoPresent,
    ).toBe(true);
  });

  it.each([
    { ...profile, userId: "not-a-uuid" },
    { ...profile, roles: ["MAINTENANCE"] },
    { ...profile, status: "UNKNOWN" },
    { ...profile, photoPresent: true, photoUrl: null },
    {
      ...profile,
      photoPresent: true,
      photoUrl: "https://images.invalid/a.png",
    },
    { ...profile, version: Number.MAX_SAFE_INTEGER + 1 },
  ])("rejects a malformed or over-privileged profile response", (body) => {
    expect(() => parseStaffSelfProfile(body)).toThrow(ApiError);
  });

  it("accepts only bounded JPEG, PNG or WebP metadata and valid timestamps", () => {
    const metadata = {
      photoId: "550e8400-e29b-41d4-a716-446655440001",
      contentType: "image/webp",
      sizeBytes: 1,
      updatedAt: "2026-10-20T12:00:00Z",
      version: 8,
    };
    expect(parseStaffProfilePhotoMetadata(metadata)).toEqual(metadata);
    expect(() =>
      parseStaffProfilePhotoMetadata({
        ...metadata,
        contentType: "image/svg+xml",
      }),
    ).toThrow(ApiError);
    expect(() =>
      parseStaffProfilePhotoMetadata({
        ...metadata,
        sizeBytes: 5 * 1024 * 1024 + 1,
      }),
    ).toThrow(ApiError);
    expect(() =>
      parseStaffProfilePhotoMetadata({ ...metadata, updatedAt: "yesterday" }),
    ).toThrow(ApiError);
  });
});
