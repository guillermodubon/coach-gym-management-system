export const maxProfilePhotoBytes = 5 * 1024 * 1024;

const allowedProfilePhotoTypes = new Set([
  "image/jpeg",
  "image/png",
  "image/webp",
]);

export type ProfilePhotoValidationFailure =
  "empty" | "too-large" | "unsupported-type";

export function validateProfilePhotoFile(
  file: Pick<File, "size" | "type">,
): ProfilePhotoValidationFailure | null {
  if (file.size < 1) return "empty";
  if (file.size > maxProfilePhotoBytes) return "too-large";
  if (!allowedProfilePhotoTypes.has(file.type.trim().toLowerCase()))
    return "unsupported-type";
  return null;
}
