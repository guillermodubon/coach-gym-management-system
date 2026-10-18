import type { AuthNotice } from "./auth-model";
import type { ApiError } from "../../shared/api/errors";

export function authNoticeMessage(notice: AuthNotice): string | undefined {
  switch (notice) {
    case "session-ended":
      return "La sesión terminó. Inicia sesión de nuevo.";
    case "login-not-established":
      return "No se pudo establecer una sesión vigente. Inicia sesión de nuevo.";
    case "login-uncertain":
      return "No pudimos comprobar si la sesión se inició.";
    case "logout-complete":
      return "Sesión cerrada.";
    case "logout-already-ended":
      return "La sesión ya no está activa.";
    case "logout-uncertain":
      return "No pudimos confirmar el cierre de sesión.";
    case "password-changed-login-again":
      return "Contraseña actualizada. Inicia sesión de nuevo.";
    default:
      return undefined;
  }
}

export function authErrorMessage(error: ApiError): string {
  if (error.code === "INVALID_CREDENTIALS") {
    return "Usuario o contraseña incorrectos.";
  }
  if (error.code === "CSRF_TOKEN_INVALID") {
    return "La protección de la solicitud cambió. Inténtalo de nuevo.";
  }
  if (error.status === 429) {
    return "Hay demasiados intentos. Espera antes de volver a intentarlo.";
  }
  return error.message;
}

export function passwordChangeErrorMessage(error: ApiError): string {
  if (error.code === "CURRENT_PASSWORD_INVALID") {
    return "No se pudo verificar la contraseña actual. Revísala e inténtalo de nuevo.";
  }
  if (error.code === "STAFF_PROFILE_VALIDATION_FAILED") {
    return "La contraseña nueva no cumple los requisitos o la confirmación no coincide.";
  }
  if (error.code === "STAFF_PROFILE_VERSION_CONFLICT") {
    return "Tu cuenta cambió. Comprueba la sesión antes de intentarlo de nuevo.";
  }
  if (error.code === "CSRF_TOKEN_INVALID") {
    return "La protección de la solicitud cambió. Revisa los campos e inténtalo de nuevo.";
  }
  if (error.status === 429) {
    return "Hay demasiados intentos. Espera antes de volver a intentarlo.";
  }
  if (error.status === 400) {
    return "Revisa la contraseña actual, los requisitos y la confirmación.";
  }
  return error.message;
}
