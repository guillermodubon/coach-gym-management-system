import type { components } from "./generated/f02-schema";
import { ApiError, isRecord } from "./errors";

type Schemas = components["schemas"];
type RequiredFields<T, K extends keyof T> = Required<Pick<T, K>>;
type StaffRole = NonNullable<Schemas["StaffSelfProfile"]["roles"]>[number];
type StaffStatus = NonNullable<Schemas["StaffSelfProfile"]["status"]>;

export type StaffSelfProfile = RequiredFields<
  Schemas["StaffSelfProfile"],
  | "userId"
  | "username"
  | "email"
  | "firstName"
  | "lastName"
  | "displayName"
  | "roles"
  | "status"
  | "photoPresent"
  | "version"
> & { photoUrl: string | null };

export type StaffSelfProfileUpdate = Schemas["UpdateStaffSelfProfileRequest"];
export type StaffProfilePhotoMetadata = RequiredFields<
  Schemas["StaffProfilePhotoDetails"],
  "photoId" | "contentType" | "sizeBytes" | "updatedAt" | "version"
>;

const profilePhotoPath = "/api/v1/me/profile/photo";
const imageTypes = new Set(["image/jpeg", "image/png", "image/webp"]);

function isStaffRole(value: unknown): value is StaffRole {
  return value === "ADMIN" || value === "RECEPTIONIST";
}

function isStaffStatus(value: unknown): value is StaffStatus {
  return (
    value === "ACTIVE" ||
    value === "INACTIVE" ||
    value === "SUSPENDED" ||
    value === "DEACTIVATED"
  );
}

function invalid(): never {
  throw new ApiError("invalid-response");
}

function text(value: unknown, maximum = 512): string {
  if (typeof value !== "string" || value.length === 0 || value.length > maximum)
    return invalid();
  return value;
}

function uuid(value: unknown): string {
  const result = text(value);
  return /^[\da-f]{8}-(?:[\da-f]{4}-){3}[\da-f]{12}$/i.test(result)
    ? result
    : invalid();
}

function nonNegativeInteger(value: unknown): number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0
    ? value
    : invalid();
}

function timestamp(value: unknown): string {
  const result = text(value);
  return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/.test(
    result,
  ) && Number.isFinite(Date.parse(result))
    ? result
    : invalid();
}

function staffRoles(value: unknown): StaffSelfProfile["roles"] {
  if (!Array.isArray(value) || value.length === 0 || !value.every(isStaffRole))
    return invalid();
  return value;
}

export function parseStaffSelfProfile(value: unknown): StaffSelfProfile {
  if (!isRecord(value)) return invalid();
  const photoPresent = value.photoPresent;
  if (typeof photoPresent !== "boolean") return invalid();
  const photoUrl = value.photoUrl;
  if (
    !(photoUrl === null || photoUrl === profilePhotoPath) ||
    photoPresent !== (photoUrl === profilePhotoPath)
  )
    return invalid();
  const status = value.status;
  if (!isStaffStatus(status)) return invalid();
  return {
    userId: uuid(value.userId),
    username: text(value.username),
    email: text(value.email, 254),
    firstName: text(value.firstName, 100),
    lastName: text(value.lastName, 100),
    displayName: text(value.displayName, 201),
    roles: staffRoles(value.roles),
    status,
    photoPresent,
    photoUrl,
    version: nonNegativeInteger(value.version),
  };
}

export function parseStaffProfilePhotoMetadata(
  value: unknown,
): StaffProfilePhotoMetadata {
  if (!isRecord(value)) return invalid();
  const contentType = value.contentType;
  const sizeBytes = nonNegativeInteger(value.sizeBytes);
  if (
    typeof contentType !== "string" ||
    !imageTypes.has(contentType) ||
    sizeBytes < 1 ||
    sizeBytes > 5 * 1024 * 1024
  )
    return invalid();
  return {
    photoId: uuid(value.photoId),
    contentType,
    sizeBytes,
    updatedAt: timestamp(value.updatedAt),
    version: nonNegativeInteger(value.version),
  };
}
