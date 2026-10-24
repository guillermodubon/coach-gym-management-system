export type ApiErrorKind =
  | "http"
  | "network"
  | "timeout"
  | "cancelled"
  | "invalid-response"
  | "invalid-request";

const problems = {
  VALIDATION_FAILED: [400, "Revisa los campos indicados."],
  MALFORMED_REQUEST: [400, "No se pudo procesar la solicitud."],
  CURRENT_PASSWORD_INVALID: [400, "No se pudo verificar la contraseña actual."],
  STAFF_PROFILE_VALIDATION_FAILED: [
    400,
    "Revisa los datos del perfil o los requisitos de contraseña.",
  ],
  STAFF_IDENTITY_REQUEST_INVALID: [
    400,
    "Revisa los datos e inténtalo de nuevo.",
  ],
  INVALID_CREDENTIALS: [401, "No se pudo iniciar sesión con esos datos."],
  AUTHENTICATION_REQUIRED: [401, "Debes iniciar sesión para continuar."],
  CSRF_TOKEN_INVALID: [
    403,
    "Actualiza la protección de la solicitud e inténtalo de nuevo.",
  ],
  ACCESS_DENIED: [403, "No tienes acceso a esta operación."],
  ACTIVE_BRANCH_UNAVAILABLE: [
    409,
    "El contexto de sucursal no está disponible.",
  ],
  STAFF_PROFILE_VERSION_CONFLICT: [
    409,
    "El perfil cambió. Actualiza su estado antes de continuar.",
  ],
  PASSWORD_RECOVERY_NOT_AVAILABLE: [
    409,
    "El enlace de recuperación ya no está disponible.",
  ],
  INVITATION_NOT_AVAILABLE: [
    409,
    "El enlace de invitación ya no está disponible.",
  ],
  STAFF_PROFILE_NOT_FOUND: [404, "El perfil no está disponible."],
  STAFF_PROFILE_PHOTO_NOT_FOUND: [404, "No hay una foto disponible."],
  STAFF_PROFILE_PHOTO_TOO_LARGE: [413, "La foto supera el límite permitido."],
  STAFF_IDENTITY_OPERATION_FORBIDDEN: [
    403,
    "No se pudo completar esta operación de identidad.",
  ],
  STAFF_PROFILE_STORAGE_FAILED: [
    500,
    "No se pudo confirmar el cambio de foto. Comprueba el perfil antes de repetirlo.",
  ],
  STAFF_PROFILE_DATA_ACCESS_FAILED: [
    500,
    "No se pudo cargar o actualizar el perfil.",
  ],
  STAFF_IDENTITY_OPERATION_FAILED: [
    500,
    "No se pudo confirmar la operación. Comprueba su estado antes de repetirla.",
  ],
  AUTHENTICATION_RATE_LIMITED: [
    429,
    "Hay demasiados intentos. Espera antes de volver a intentarlo.",
  ],
} as const;

export type ProblemCode = keyof typeof problems;
export type FormField =
  | "identifier"
  | "firstName"
  | "lastName"
  | "email"
  | "password"
  | "currentPassword"
  | "newPassword"
  | "newPasswordConfirmation"
  | "passwordConfirmation"
  | "token"
  | "file"
  | "version";

const fields: readonly FormField[] = [
  "identifier",
  "firstName",
  "lastName",
  "email",
  "password",
  "currentPassword",
  "newPassword",
  "newPasswordConfirmation",
  "passwordConfirmation",
  "token",
  "file",
  "version",
];

const messages: Record<Exclude<ApiErrorKind, "http">, string> = {
  network: "No se pudo conectar con el servicio. Comprueba la conexión.",
  timeout:
    "El servicio no respondió a tiempo. El resultado de una operación enviada puede ser incierto.",
  cancelled: "La solicitud se canceló.",
  "invalid-response": "El servicio devolvió una respuesta inesperada.",
  "invalid-request": "La solicitud no está permitida.",
};

interface ErrorDetails {
  status?: number;
  code?: ProblemCode;
  clientReason?: "unsupported-authority";
  correlationId?: string;
  retryAfterSeconds?: number;
  fieldErrors?: Partial<Record<FormField, string>>;
}

/** Only safe, bounded metadata is retained; never a Response, body or cause. */
export class ApiError extends Error {
  readonly kind: ApiErrorKind;
  readonly status: number | undefined;
  readonly code: ProblemCode | undefined;
  readonly clientReason: "unsupported-authority" | undefined;
  readonly correlationId: string | undefined;
  readonly retryAfterSeconds: number | undefined;
  readonly fieldErrors: Readonly<Partial<Record<FormField, string>>>;

  constructor(kind: ApiErrorKind, details: ErrorDetails = {}) {
    super(
      kind === "http"
        ? details.code
          ? problems[details.code][1]
          : "No se pudo completar la solicitud. Inténtalo de nuevo de forma explícita."
        : messages[kind],
    );
    this.name = "ApiError";
    this.kind = kind;
    this.status = details.status;
    this.code = details.code;
    this.clientReason = details.clientReason;
    this.correlationId = details.correlationId;
    this.retryAfterSeconds = details.retryAfterSeconds;
    this.fieldErrors = Object.freeze({ ...details.fieldErrors });
  }
}

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function safeCorrelation(value: unknown): string | undefined {
  return typeof value === "string" &&
    /^[A-Za-z0-9][A-Za-z0-9._:-]{0,63}$/.test(value)
    ? value
    : undefined;
}

function retryAfter(value: string | null): number | undefined {
  if (!value) return undefined;
  if (/^\d{1,10}$/.test(value)) {
    const seconds = Number(value);
    return seconds <= 2_147_483 ? seconds : undefined;
  }
  if (
    !/^(Mon|Tue|Wed|Thu|Fri|Sat|Sun), \d{2} [A-Za-z]{3} \d{4} \d{2}:\d{2}:\d{2} GMT$/.test(
      value,
    )
  ) {
    return undefined;
  }
  const seconds = Math.ceil((Date.parse(value) - Date.now()) / 1000);
  return Number.isFinite(seconds) && seconds >= 0 && seconds <= 2_147_483
    ? seconds
    : undefined;
}

export function httpError(response: Response, body: unknown): ApiError {
  const problem =
    isRecord(body) && body.status === response.status ? body : undefined;
  const candidate = problem?.code;
  const code =
    typeof candidate === "string" &&
    Object.hasOwn(problems, candidate) &&
    problems[candidate as ProblemCode][0] === response.status
      ? (candidate as ProblemCode)
      : undefined;
  const fieldErrors: Partial<Record<FormField, string>> = {};
  if (code === "VALIDATION_FAILED" && isRecord(problem?.fieldErrors)) {
    for (const field of fields) {
      if (typeof problem.fieldErrors[field] === "string") {
        fieldErrors[field] = "Revisa este campo.";
      }
    }
  }
  const correlationId =
    safeCorrelation(response.headers.get("X-Correlation-ID")) ??
    safeCorrelation(problem?.correlationId);
  const wait =
    response.status === 429
      ? retryAfter(response.headers.get("Retry-After"))
      : undefined;
  return new ApiError("http", {
    status: response.status,
    ...(code ? { code } : {}),
    ...(correlationId ? { correlationId } : {}),
    ...(wait !== undefined ? { retryAfterSeconds: wait } : {}),
    fieldErrors,
  });
}
