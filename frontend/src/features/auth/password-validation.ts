export const MIN_NEW_PASSWORD_LENGTH = 12;
export const MAX_PASSWORD_LENGTH = 256;

export interface PasswordChangeValues {
  currentPassword: string;
  newPassword: string;
  newPasswordConfirmation: string;
}

export type PasswordChangeField = keyof PasswordChangeValues;
export type PasswordChangeFieldErrors = Partial<
  Record<PasswordChangeField, string>
>;

export function validatePasswordChange(
  values: PasswordChangeValues,
): PasswordChangeFieldErrors {
  const errors: PasswordChangeFieldErrors = {};
  const { currentPassword, newPassword, newPasswordConfirmation } = values;

  if (currentPassword.length === 0) {
    errors.currentPassword = "Escribe tu contraseña actual.";
  } else if (currentPassword.length > MAX_PASSWORD_LENGTH) {
    errors.currentPassword =
      "La contraseña actual no puede superar 256 caracteres.";
  }

  if (newPassword.length === 0) {
    errors.newPassword = "Escribe una contraseña nueva.";
  } else if (newPassword.length < MIN_NEW_PASSWORD_LENGTH) {
    errors.newPassword = "Usa al menos 12 caracteres.";
  } else if (newPassword.length > MAX_PASSWORD_LENGTH) {
    errors.newPassword = "La contraseña nueva no puede superar 256 caracteres.";
  } else if (newPassword === currentPassword) {
    errors.newPassword = "La contraseña nueva debe ser distinta de la actual.";
  }

  if (newPasswordConfirmation.length === 0) {
    errors.newPasswordConfirmation = "Confirma la contraseña nueva.";
  } else if (newPasswordConfirmation.length > MAX_PASSWORD_LENGTH) {
    errors.newPasswordConfirmation =
      "La confirmación no puede superar 256 caracteres.";
  } else if (newPasswordConfirmation !== newPassword) {
    errors.newPasswordConfirmation = "Las contraseñas no coinciden.";
  }

  return errors;
}

export function validatePasswordChangeField(
  field: PasswordChangeField,
  values: PasswordChangeValues,
): string | undefined {
  return validatePasswordChange(values)[field];
}
