import type { CsrfToken } from "./contracts";
import { parseCsrf } from "./contracts";
import { ApiError, httpError } from "./errors";

const operations: Readonly<Record<string, readonly string[]>> = {
  "/api/v1/auth/csrf": ["GET"],
  "/api/v1/auth/me": ["GET"],
  "/api/v1/auth/login": ["POST"],
  "/api/v1/auth/logout": ["POST"],
  "/api/v1/me/profile/password": ["POST"],
  "/api/v1/me/branch-context": ["GET"],
};

interface RequestOptions<T> {
  path: string;
  method: "GET" | "POST";
  expectedStatus: 200 | 204;
  parse: (body: unknown) => T;
  body?: object;
  signal?: AbortSignal;
}

function abortable<T>(promise: Promise<T>, signal: AbortSignal): Promise<T> {
  return new Promise((resolve, reject) => {
    const clear = () => signal.removeEventListener("abort", abort);
    const abort = () => {
      clear();
      reject(new ApiError("cancelled"));
    };
    // Consume the original promise even if it rejects after cancellation.
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

  function checkedUrl(path: string, method: string): URL {
    if (
      !Object.hasOwn(operations, path) ||
      !operations[path]?.includes(method)
    ) {
      throw new ApiError("invalid-request");
    }
    const origin = globalThis.location.origin;
    const url = new URL(path, origin);
    if (!/^https?:$/.test(url.protocol) || url.origin !== origin) {
      throw new ApiError("invalid-request");
    }
    return url;
  }

  async function send<T>(
    options: RequestOptions<T>,
    signal: AbortSignal,
    token?: CsrfToken,
  ): Promise<T> {
    const url = checkedUrl(options.path, options.method);
    const headers = new Headers({
      Accept: "application/json, application/problem+json",
    });
    let body: string | undefined;
    if (options.body !== undefined) {
      try {
        body = JSON.stringify(options.body);
      } catch {
        throw new ApiError("invalid-request");
      }
      headers.set("Content-Type", "application/json");
    }
    if (token) headers.set(token.headerName, token.token);
    const response = await abortable(
      fetch(url, {
        method: options.method,
        headers,
        ...(body !== undefined ? { body } : {}),
        credentials: "same-origin",
        mode: "same-origin",
        redirect: "error",
        cache: "no-store",
        signal,
      }),
      signal,
    );
    if (signal.aborted) throw new ApiError("cancelled");
    if (response.status === 204 && response.status === options.expectedStatus) {
      return options.parse(undefined);
    }
    const contentType = response.headers
      .get("Content-Type")
      ?.split(";")[0]
      ?.trim()
      .toLowerCase();
    const json =
      contentType === "application/json" || contentType?.endsWith("+json");
    let data: unknown;
    if (json) {
      const raw = await abortable(response.text(), signal);
      try {
        data = JSON.parse(raw);
      } catch {
        if (response.ok) throw new ApiError("invalid-response");
      }
    }
    if (!response.ok) throw httpError(response, data);
    if (!json || response.status !== options.expectedStatus)
      throw new ApiError("invalid-response");
    return options.parse(data);
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
    checkedUrl(options.path, options.method);
    return run(async (signal) => {
      const token =
        options.method === "POST"
          ? await abortable(csrfBootstrap(), signal)
          : undefined;
      if (signal.aborted) throw new ApiError("cancelled");
      try {
        const result = await send(options, signal, token);
        if (options.method === "POST") invalidateCsrf();
        return result;
      } catch (error) {
        if (
          options.method === "POST" &&
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
        } else if (options.method === "POST") {
          // Even an uncertain session transition must not reuse its previous token.
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
