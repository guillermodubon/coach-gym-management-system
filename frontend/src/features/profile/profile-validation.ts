export type ProfileNameField = "firstName" | "lastName";

export interface ProfileNameValues {
  firstName: string;
  lastName: string;
}

export type ProfileNameErrors = Partial<Record<ProfileNameField, string>>;

export interface ProfileNameValidation {
  values: ProfileNameValues;
  errors: ProfileNameErrors;
  valid: boolean;
}

export function normalizeProfileName(value: string): string {
  return value.replace(/\s+/gu, " ").trim();
}

function validateName(
  value: string,
  label: "nombre" | "apellido",
): string | undefined {
  if (containsControlCharacter(value)) {
    return `${label} contiene caracteres no permitidos.`;
  }

  const normalized = normalizeProfileName(value);
  if (!normalized)
    return `Escribe ${label === "nombre" ? "un nombre" : "un apellido"}.`;
  if (normalized.length > 100) {
    return `Usa como máximo 100 caracteres para el ${label}.`;
  }
  return undefined;
}

function containsControlCharacter(value: string): boolean {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code <= 0x1f || (code >= 0x7f && code <= 0x9f)) return true;
  }
  return false;
}

export function validateProfileNames(
  values: ProfileNameValues,
): ProfileNameValidation {
  const normalizedValues = {
    firstName: normalizeProfileName(values.firstName),
    lastName: normalizeProfileName(values.lastName),
  };
  const firstNameError = validateName(values.firstName, "nombre");
  const lastNameError = validateName(values.lastName, "apellido");
  const errors: ProfileNameErrors = {
    ...(firstNameError ? { firstName: firstNameError } : {}),
    ...(lastNameError ? { lastName: lastNameError } : {}),
  };
  return {
    values: normalizedValues,
    errors,
    valid: Object.keys(errors).length === 0,
  };
}
