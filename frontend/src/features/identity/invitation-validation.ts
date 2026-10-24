import {
  normalizeProfileName,
  validateProfileNames,
} from "../profile/profile-validation";
import {
  MAX_PASSWORD_LENGTH,
  MIN_NEW_PASSWORD_LENGTH,
} from "../auth/password-validation";

export interface InvitationAcceptanceValues {
  firstName: string;
  lastName: string;
  password: string;
  passwordConfirmation: string;
}

export type InvitationAcceptanceField = keyof InvitationAcceptanceValues;
export type InvitationAcceptanceErrors = Partial<
  Record<InvitationAcceptanceField, string>
>;

export interface InvitationAcceptanceValidation {
  values: InvitationAcceptanceValues;
  errors: InvitationAcceptanceErrors;
  valid: boolean;
}

export function validateInvitationAcceptance(
  values: InvitationAcceptanceValues,
): InvitationAcceptanceValidation {
  const names = validateProfileNames(values);
  const errors: InvitationAcceptanceErrors = { ...names.errors };

  if (values.password.length === 0) {
    errors.password = "Escribe una contraseña.";
  } else if (values.password.length < MIN_NEW_PASSWORD_LENGTH) {
    errors.password = "Usa al menos 12 caracteres.";
  } else if (values.password.length > MAX_PASSWORD_LENGTH) {
    errors.password = "La contraseña no puede superar 256 caracteres.";
  }

  if (values.passwordConfirmation.length === 0) {
    errors.passwordConfirmation = "Confirma la contraseña.";
  } else if (values.passwordConfirmation.length > MAX_PASSWORD_LENGTH) {
    errors.passwordConfirmation =
      "La confirmación no puede superar 256 caracteres.";
  } else if (values.passwordConfirmation !== values.password) {
    errors.passwordConfirmation = "Las contraseñas no coinciden.";
  }

  return {
    values: {
      ...names.values,
      // Passwords are opaque credentials; preserve their exact input.
      password: values.password,
      passwordConfirmation: values.passwordConfirmation,
    },
    errors,
    valid: Object.keys(errors).length === 0,
  };
}

export function normalizeInvitationName(value: string): string {
  return normalizeProfileName(value);
}
