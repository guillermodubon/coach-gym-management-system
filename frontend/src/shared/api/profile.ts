import type { ApiClient } from "./client";
import { ApiError } from "./errors";
import type {
  StaffProfilePhotoMetadata,
  StaffSelfProfile,
  StaffSelfProfileUpdate,
} from "./profile-contracts";
import {
  parseStaffProfilePhotoMetadata,
  parseStaffSelfProfile,
} from "./profile-contracts";
import type { RequestControl } from "./identity";

const maximumPhotoBytes = 5 * 1024 * 1024;
const allowedPhotoTypes = new Set(["image/jpeg", "image/png", "image/webp"]);

function assertVersion(version: number): void {
  if (!Number.isSafeInteger(version) || version < 0) {
    throw new ApiError("invalid-request");
  }
}

function validatePhoto(file: File): void {
  if (
    !(file instanceof File) ||
    file.size < 1 ||
    file.size > maximumPhotoBytes ||
    !allowedPhotoTypes.has(file.type.toLowerCase())
  ) {
    throw new ApiError("invalid-request");
  }
}

/** Profile credentials and files stay in direct request closures, outside query caches. */
export function createProfileApi(client: ApiClient) {
  return {
    getProfile: (control: RequestControl = {}): Promise<StaffSelfProfile> =>
      client.request({
        path: "/api/v1/me/profile",
        method: "GET",
        responseType: "json",
        expectedStatus: 200,
        parse: parseStaffSelfProfile,
        ...control,
      }),
    updateProfile: (
      request: StaffSelfProfileUpdate,
      control: RequestControl = {},
    ): Promise<StaffSelfProfile> => {
      assertVersion(request.version);
      return client.request({
        path: "/api/v1/me/profile",
        method: "PUT",
        responseType: "json",
        expectedStatus: 200,
        bodyType: "json",
        body: {
          firstName: request.firstName,
          lastName: request.lastName,
          version: request.version,
        },
        parse: parseStaffSelfProfile,
        ...control,
      });
    },
    uploadPhoto: (
      file: File,
      version: number,
      control: RequestControl = {},
    ): Promise<StaffProfilePhotoMetadata> => {
      validatePhoto(file);
      assertVersion(version);
      const form = new FormData();
      form.append("file", file, "profile-photo");
      form.append("version", String(version));
      return client.request({
        path: "/api/v1/me/profile/photo",
        method: "PUT",
        bodyType: "multipart",
        body: form,
        responseType: "json",
        expectedStatus: 200,
        parse: parseStaffProfilePhotoMetadata,
        ...control,
      });
    },
    downloadPhoto: (control: RequestControl = {}): Promise<Blob> =>
      client.request({
        path: "/api/v1/me/profile/photo",
        method: "GET",
        responseType: "binary",
        expectedStatus: 200,
        parse: (blob) => blob,
        ...control,
      }),
    removePhoto: (
      version: number,
      control: RequestControl = {},
    ): Promise<StaffSelfProfile> => {
      assertVersion(version);
      return client.request({
        path: "/api/v1/me/profile/photo",
        method: "DELETE",
        query: { version },
        bodyType: "none",
        responseType: "json",
        expectedStatus: 200,
        parse: parseStaffSelfProfile,
        ...control,
      });
    },
  };
}
