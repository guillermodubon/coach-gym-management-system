import { ApiError } from "../../shared/api/errors";

export function safePublicIdentityError(error: unknown): ApiError {
  return error instanceof ApiError ? error : new ApiError("network");
}

export function recoveryRequestErrorMessage(error: ApiError): string {
  if (error.status === 400) {
    return "Revisa el formato del correo e inténtalo de nuevo.";
  }
  if (error.code === "CSRF_TOKEN_INVALID") {
    return "No se pudo validar la solicitud. Actualiza la página e inténtalo de nuevo.";
  }
  if (error.status === 429) {
    return "Hay demasiadas solicitudes. Espera antes de volver a intentarlo.";
  }
  if (error.kind === "network" || error.kind === "timeout") {
    return "No pudimos confirmar si la solicitud se procesó. Solo se repetirá si decides enviarla de nuevo.";
  }
  return "No se pudo completar la solicitud. Inténtalo de nuevo más tarde.";
}

export function recoveryCompletionErrorMessage(error: ApiError): string {
  if (error.status === 409) {
    return "El enlace de recuperación no está disponible. Solicita uno nuevo.";
  }
  if (error.status === 400) {
    return "Revisa la contraseña nueva y su confirmación.";
  }
  if (error.code === "CSRF_TOKEN_INVALID") {
    return "No se pudo validar la solicitud. Puedes volver a escribir la contraseña e intentarlo de nuevo.";
  }
  if (error.status === 429) {
    return "Hay demasiados intentos. Espera antes de volver a intentarlo.";
  }
  if (
    error.kind === "network" ||
    error.kind === "timeout" ||
    error.kind === "invalid-response" ||
    (error.status !== undefined && error.status >= 500)
  ) {
    return "No pudimos confirmar el cambio. Inicia sesión con la contraseña nueva si la recuerdas; si no, solicita otro enlace.";
  }
  return "No se pudo completar el cambio. Solicita un enlace nuevo si el problema continúa.";
}
