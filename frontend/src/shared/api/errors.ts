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
    "Revisa los requisitos de la contraseña.",
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
    "La cuenta cambió. Actualiza su estado antes de continuar.",
  ],
  AUTHENTICATION_RATE_LIMITED: [
    429,
    "Hay demasiados intentos. Espera antes de volver a intentarlo.",
  ],
} as const;

export type ProblemCode = keyof typeof problems;
export type FormField =
  | "identifier"
  | "password"
  | "currentPassword"
  | "newPassword"
  | "newPasswordConfirmation";

const fields: readonly FormField[] = [
  "identifier",
  "password",
  "currentPassword",
  "newPassword",
  "newPasswordConfirmation",
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
