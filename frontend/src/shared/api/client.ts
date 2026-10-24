import type { CsrfToken } from "./contracts";
import { parseCsrf } from "./contracts";
import { ApiError, httpError, isRecord } from "./errors";

type HttpMethod = "GET" | "POST" | "PUT" | "DELETE";
type MutationMethod = "POST" | "PUT" | "DELETE";
type ResponseKind = "json" | "empty" | "binary";
type BodyKind = "none" | "json" | "multipart";
interface OperationContract {
  requestBody: BodyKind;
  responseType: ResponseKind;
  expectedStatus: 200 | 201 | 202 | 204;
  query?: "version";
}

const operations: Readonly<
  Record<string, Readonly<Partial<Record<HttpMethod, OperationContract>>>>
> = {
  "/api/v1/auth/csrf": {
    GET: { requestBody: "none", responseType: "json", expectedStatus: 200 },
  },
  "/api/v1/auth/me": {
    GET: { requestBody: "none", responseType: "json", expectedStatus: 200 },
  },
  "/api/v1/auth/login": {
    POST: { requestBody: "json", responseType: "empty", expectedStatus: 204 },
  },
  "/api/v1/auth/logout": {
    POST: { requestBody: "none", responseType: "empty", expectedStatus: 204 },
  },
  "/api/v1/me/branch-context": {
    GET: { requestBody: "none", responseType: "json", expectedStatus: 200 },
  },
  "/api/v1/me/profile": {
    GET: { requestBody: "none", responseType: "json", expectedStatus: 200 },
    PUT: { requestBody: "json", responseType: "json", expectedStatus: 200 },
  },
  "/api/v1/me/profile/photo": {
    GET: { requestBody: "none", responseType: "binary", expectedStatus: 200 },
    PUT: {
      requestBody: "multipart",
      responseType: "json",
      expectedStatus: 200,
    },
    DELETE: {
      requestBody: "none",
      responseType: "json",
      expectedStatus: 200,
      query: "version",
    },
  },
  "/api/v1/me/profile/password": {
    POST: { requestBody: "json", responseType: "json", expectedStatus: 200 },
  },
  "/api/v1/auth/password-recovery-requests": {
    POST: { requestBody: "json", responseType: "json", expectedStatus: 202 },
  },
  "/api/v1/auth/password-recovery/complete": {
    POST: { requestBody: "json", responseType: "empty", expectedStatus: 204 },
  },
  "/api/v1/staff-invitations/inspect": {
    POST: { requestBody: "json", responseType: "json", expectedStatus: 200 },
  },
  "/api/v1/staff-invitations/accept": {
    POST: { requestBody: "json", responseType: "json", expectedStatus: 201 },
  },
};
type JsonResponse<T> = {
  responseType: "json";
  expectedStatus: 200 | 201 | 202;
  parse: (body: unknown) => T;
};
type EmptyResponse<T> = {
  responseType: "empty";
  expectedStatus: 204;
  parse: (body: undefined) => T;
};
type BinaryResponse<T> = {
  responseType: "binary";
  expectedStatus: 200;
  parse: (body: Blob) => T;
};
type RequestBase = {
  path: string;
  signal?: AbortSignal;
};
type RequestOptions<T> =
  | (RequestBase & {
      method: "GET";
      query?: never;
      body?: never;
      bodyType?: never;
    } & (JsonResponse<T> | BinaryResponse<T>))
  | (RequestBase & {
      method: "POST";
      query?: never;
      bodyType: "json";
      body: object;
    } & (JsonResponse<T> | EmptyResponse<T>))
  | (RequestBase & {
      method: "POST";
      query?: never;
      bodyType: "none";
      body?: never;
    } & (JsonResponse<T> | EmptyResponse<T>))
  | (RequestBase & {
      method: "PUT";
      query?: never;
      bodyType: "json";
      body: object;
    } & JsonResponse<T>)
  | (RequestBase & {
      method: "PUT";
      query?: never;
      bodyType: "multipart";
      body: FormData;
    } & JsonResponse<T>)
  | (RequestBase & {
      method: "DELETE";
      query: Readonly<{ version: number }>;
      bodyType: "none";
      body?: never;
    } & JsonResponse<T>);

const maximumPhotoBytes = 5 * 1024 * 1024;
const photoContentTypes = new Set(["image/jpeg", "image/png", "image/webp"]);

function abortable<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  return new Promise((resolve, reject) => {
    const clear = () => signal.removeEventListener("abort", abort);
    const abort = () => {
      clear();
      reject(new ApiError("cancelled"));
    };
    promise.then(
      (value) => {
        clear();
        resolve(value);
      },
      (error: unknown) => {
        clear();
        reject(error);
      },
    );
    if (signal.aborted) {
      abort();
      return;
    }
    signal.addEventListener("abort", abort, { once: true });
  });
}

function isMutation(method: string): method is MutationMethod {
  return method === "POST" || method === "PUT" || method === "DELETE";
}

function validateRequestShape<T>(options: RequestOptions<T>): void {
  const contract = operations[options.path]?.[options.method];
  if (
    !contract ||
    options.responseType !== contract.responseType ||
    options.expectedStatus !== contract.expectedStatus
  )
    throw new ApiError("invalid-request");
  const bodyType: unknown =
    "bodyType" in options ? options.bodyType : undefined;
  const body = "body" in options ? options.body : undefined;
  const hasBody = body !== undefined;
  const expectedBodyType =
    contract.requestBody === "none" && options.method === "GET"
      ? undefined
      : contract.requestBody;
  if (bodyType !== expectedBodyType) throw new ApiError("invalid-request");
  if (contract.requestBody === "none") {
    if (hasBody) throw new ApiError("invalid-request");
    return;
  }
  if (!hasBody) throw new ApiError("invalid-request");
  if (contract.requestBody === "multipart") {
    if (
      options.method !== "PUT" ||
      typeof FormData === "undefined" ||
      !(body instanceof FormData)
    )
      throw new ApiError("invalid-request");
    return;
  }
  if (
    contract.requestBody !== "json" ||
    !isRecord(body) ||
    Object.getPrototypeOf(body) !== Object.prototype
  )
    throw new ApiError("invalid-request");
}

function contentType(response: Response): string | undefined {
  return response.headers
    .get("Content-Type")
    ?.split(";")[0]
    ?.trim()
    .toLowerCase();
}

function isJsonContentType(value: string | undefined): boolean {
  return value === "application/json" || value?.endsWith("+json") === true;
}

async function readErrorBody(
  response: Response,
  signal: AbortSignal,
): Promise<unknown> {
  if (!isJsonContentType(contentType(response))) return undefined;
  const raw = await abortable(response.text(), signal);
  try {
    return JSON.parse(raw) as unknown;
  } catch {
    return undefined;
  }
}

async function readBoundedPhoto(
  response: Response,
  mimeType: string,
  signal: AbortSignal,
): Promise<Blob> {
  const declaredLength = response.headers.get("Content-Length");
  if (declaredLength !== null) {
    if (!/^\d{1,12}$/.test(declaredLength)) {
      await response.body?.cancel().catch(() => undefined);
      throw new ApiError("invalid-response");
    }
    if (Number(declaredLength) > maximumPhotoBytes) {
      await response.body?.cancel().catch(() => undefined);
      throw new ApiError("invalid-response");
    }
  }
  const reader = response.body?.getReader();
  if (!reader) throw new ApiError("invalid-response");
  const chunks: Uint8Array[] = [];
  let size = 0;
  try {
    while (true) {
      const result = await abortable(reader.read(), signal);
      if (result.done) break;
      size += result.value.byteLength;
      if (size > maximumPhotoBytes) {
        await reader.cancel().catch(() => undefined);
        throw new ApiError("invalid-response");
      }
      chunks.push(result.value);
    }
  } finally {
    reader.releaseLock();
  }
  if (size === 0) throw new ApiError("invalid-response");
  const bytes = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new Blob([bytes.buffer], { type: mimeType });
}

export function createApiClient({
  timeoutMs = 15_000,
}: { timeoutMs?: number } = {}) {
  if (
    !Number.isSafeInteger(timeoutMs) ||
    timeoutMs <= 0 ||
    timeoutMs > 2_147_483_647
  )
    throw new ApiError("invalid-request");
  const active = new Set<AbortController>();
  let epoch = 0;
  let csrfVersion = 0;
  let csrf: CsrfToken | undefined;
  let pendingCsrf: Promise<CsrfToken> | undefined;

  async function run<T>(
    work: (signal: AbortSignal) => Promise<T>,
    signal?: AbortSignal,
  ): Promise<T> {
    const controller = new AbortController();
    const startedAtEpoch = epoch;
    let timedOut = false;
    const abort = () => controller.abort();
    const timer = setTimeout(() => {
      timedOut = true;
      controller.abort();
    }, timeoutMs);
    active.add(controller);
    signal?.addEventListener("abort", abort, { once: true });
    if (signal?.aborted) controller.abort();
    try {
      if (controller.signal.aborted) throw new ApiError("cancelled");
      const result = await abortable(
        work(controller.signal),
        controller.signal,
      );
      if (controller.signal.aborted || startedAtEpoch !== epoch)
        throw new ApiError("cancelled");
      return result;
    } catch (error) {
      if (controller.signal.aborted)
        throw new ApiError(timedOut ? "timeout" : "cancelled");
      if (error instanceof ApiError) throw error;
      throw new ApiError("network");
    } finally {
      clearTimeout(timer);
      active.delete(controller);
      signal?.removeEventListener("abort", abort);
    }
  }

  function checkedUrl(
    path: string,
    method: HttpMethod,
    query?: Readonly<{ version: number }>,
  ): URL {
    const contract = operations[path]?.[method];
    if (!contract) throw new ApiError("invalid-request");
    const versionedPhotoDelete = contract.query === "version";
    if (versionedPhotoDelete) {
      if (
        !isRecord(query) ||
        Object.keys(query).length !== 1 ||
        !Object.hasOwn(query, "version") ||
        typeof query.version !== "number" ||
        !Number.isSafeInteger(query.version) ||
        query.version < 0
      )
        throw new ApiError("invalid-request");
    } else if (query !== undefined) {
      throw new ApiError("invalid-request");
    }
    const origin = globalThis.location.origin;
    const url = new URL(path, origin);
    if (
      !/^https?:$/.test(url.protocol) ||
      url.origin !== origin ||
      url.pathname !== path ||
      url.search ||
      url.hash
    ) {
      throw new ApiError("invalid-request");
    }
    if (versionedPhotoDelete)
      url.searchParams.set("version", String(query?.version));
    return url;
  }

  async function send<T>(
    options: RequestOptions<T>,
    signal: AbortSignal,
    token?: CsrfToken,
  ): Promise<T> {
    const url = checkedUrl(
      options.path,
      options.method,
      "query" in options ? options.query : undefined,
    );
    const responseType = options.responseType;
    const headers = new Headers({
      Accept:
        responseType === "binary"
          ? "image/jpeg, image/png, image/webp, application/problem+json"
          : "application/json, application/problem+json",
    });
    let requestBody: BodyInit | undefined;
    const bodyType: unknown =
      "bodyType" in options ? options.bodyType : undefined;
    if ("body" in options && options.body !== undefined) {
      if (bodyType === "multipart") {
        if (!(options.body instanceof FormData))
          throw new ApiError("invalid-request");
        requestBody = options.body;
      } else {
        if (
          bodyType === "none" ||
          !isRecord(options.body) ||
          Object.getPrototypeOf(options.body) !== Object.prototype
        )
          throw new ApiError("invalid-request");
        try {
          requestBody = JSON.stringify(options.body);
        } catch {
          throw new ApiError("invalid-request");
        }
        headers.set("Content-Type", "application/json");
      }
    } else if (bodyType === "multipart") {
      throw new ApiError("invalid-request");
    }
    if (token) headers.set(token.headerName, token.token);
    const response = await abortable(
      fetch(url, {
        method: options.method,
        headers,
        ...(requestBody !== undefined ? { body: requestBody } : {}),
        credentials: "same-origin",
        mode: "same-origin",
        redirect: "error",
        cache: "no-store",
        signal,
      }),
      signal,
    );
    if (signal.aborted) throw new ApiError("cancelled");

    if (!response.ok) {
      const data = await readErrorBody(response, signal);
      throw httpError(response, data);
    }
    if (response.status !== options.expectedStatus) {
      await response.body?.cancel().catch(() => undefined);
      throw new ApiError("invalid-response");
    }
    if (options.responseType === "empty") return options.parse(undefined);
    if (options.responseType === "binary") {
      const mimeType = contentType(response);
      if (!mimeType || !photoContentTypes.has(mimeType)) {
        await response.body?.cancel().catch(() => undefined);
        throw new ApiError("invalid-response");
      }
      return options.parse(await readBoundedPhoto(response, mimeType, signal));
    }
    if (!isJsonContentType(contentType(response))) {
      await response.body?.cancel().catch(() => undefined);
      throw new ApiError("invalid-response");
    }
    const raw = await abortable(response.text(), signal);
    try {
      if (options.responseType !== "json")
        throw new ApiError("invalid-response");
      return options.parse(JSON.parse(raw) as unknown);
    } catch (error) {
      if (error instanceof ApiError) throw error;
      throw new ApiError("invalid-response");
    }
  }

  function invalidateCsrf() {
    csrfVersion++;
    csrf = undefined;
    pendingCsrf = undefined;
  }

  function csrfBootstrap(): Promise<CsrfToken> {
    if (csrf) return Promise.resolve(csrf);
    if (pendingCsrf) return pendingCsrf;
    const version = csrfVersion;
    const pending = run(async (signal) => {
      const token = await send(
        {
          path: "/api/v1/auth/csrf",
          method: "GET",
          responseType: "json",
          expectedStatus: 200,
          parse: parseCsrf,
        },
        signal,
      );
      if (version !== csrfVersion) throw new ApiError("cancelled");
      csrf = Object.freeze(token);
      return token;
    });
    pendingCsrf = pending;
    const clear = () => {
      if (pendingCsrf === pending) pendingCsrf = undefined;
    };
    void pending.then(clear, clear);
    return pending;
  }

  function getCsrf(signal?: AbortSignal): Promise<CsrfToken> {
    return run((ownSignal) => abortable(csrfBootstrap(), ownSignal), signal);
  }

  async function request<T>(options: RequestOptions<T>): Promise<T> {
    validateRequestShape(options);
    checkedUrl(
      options.path,
      options.method,
      "query" in options ? options.query : undefined,
    );
    return run(async (signal) => {
      const mutation = isMutation(options.method);
      const token = mutation
        ? await abortable(csrfBootstrap(), signal)
        : undefined;
      if (signal.aborted) throw new ApiError("cancelled");
      try {
        const result = await send(options, signal, token);
        if (mutation) invalidateCsrf();
        return result;
      } catch (error) {
        if (
          mutation &&
          error instanceof ApiError &&
          error.code === "CSRF_TOKEN_INVALID"
        ) {
          if (csrf === token) invalidateCsrf();
          // Prepare a fresh token, but NEVER replay the original mutation.
          try {
            await abortable(csrfBootstrap(), signal);
          } catch {
            // Preserve the original rejection; a manual retry can bootstrap again.
          }
        } else if (mutation) {
          // Never reuse a token after a possibly uncertain session transition.
          invalidateCsrf();
        }
        throw error;
      }
    }, options.signal);
  }

  function reset() {
    epoch++;
    invalidateCsrf();
    for (const controller of active) controller.abort();
  }

  return { request, getCsrf, invalidateCsrf, reset };
}

export type ApiClient = ReturnType<typeof createApiClient>;
