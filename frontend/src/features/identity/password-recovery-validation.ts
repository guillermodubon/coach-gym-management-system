import {
  MAX_PASSWORD_LENGTH,
  MIN_NEW_PASSWORD_LENGTH,
} from "../auth/password-validation";

export interface PasswordRecoveryValues {
  newPassword: string;
  passwordConfirmation: string;
}

export type PasswordRecoveryField = keyof PasswordRecoveryValues;
export type PasswordRecoveryFieldErrors = Partial<
  Record<PasswordRecoveryField, string>
>;

export function validatePasswordRecovery(
  values: PasswordRecoveryValues,
): PasswordRecoveryFieldErrors {
  const errors: PasswordRecoveryFieldErrors = {};

  if (values.newPassword.length === 0) {
    errors.newPassword = "Escribe una contraseña nueva.";
  } else if (values.newPassword.length < MIN_NEW_PASSWORD_LENGTH) {
    errors.newPassword = "Usa al menos 12 caracteres.";
  } else if (values.newPassword.length > MAX_PASSWORD_LENGTH) {
    errors.newPassword = "La contraseña no puede superar 256 caracteres.";
  }

  if (values.passwordConfirmation.length === 0) {
    errors.passwordConfirmation = "Confirma la contraseña nueva.";
  } else if (values.passwordConfirmation.length > MAX_PASSWORD_LENGTH) {
    errors.passwordConfirmation =
      "La confirmación no puede superar 256 caracteres.";
  } else if (values.passwordConfirmation !== values.newPassword) {
    errors.passwordConfirmation = "Las contraseñas no coinciden.";
  }

  return errors;
}
