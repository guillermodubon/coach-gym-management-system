import { createReadStream } from "node:fs";
import { realpath, stat } from "node:fs/promises";
import { createServer, request as httpRequest } from "node:http";
import { extname, isAbsolute, relative, resolve, sep } from "node:path";

const MIME_TYPES = new Map([
  [".css", "text/css; charset=utf-8"],
  [".html", "text/html; charset=utf-8"],
  [".ico", "image/x-icon"],
  [".js", "text/javascript; charset=utf-8"],
  [".json", "application/json; charset=utf-8"],
  [".png", "image/png"],
  [".svg", "image/svg+xml"],
  [".txt", "text/plain; charset=utf-8"],
  [".webmanifest", "application/manifest+json"],
  [".woff2", "font/woff2"],
]);

const HOP_BY_HOP_HEADERS = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

function writeText(response, statusCode, text) {
  response.writeHead(statusCode, {
    "Cache-Control": "no-store",
    "Content-Length": Buffer.byteLength(text),
    "Content-Type": "text/plain; charset=utf-8",
    "X-Content-Type-Options": "nosniff",
  });
  response.end(text);
}

function decodeSafePath(pathname) {
  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    return null;
  }
  if (
    decoded.includes("\\") ||
    decoded.includes("\0") ||
    decoded.includes("%") ||
    decoded.split("/").some((segment) => segment === "." || segment === "..")
  ) {
    return null;
  }
  return decoded;
}

function isInside(root, candidate) {
  const pathFromRoot = relative(root, candidate);
  return (
    pathFromRoot === "" ||
    (!pathFromRoot.startsWith(`..${sep}`) &&
      pathFromRoot !== ".." &&
      !isAbsolute(pathFromRoot))
  );
}

function proxyApi(request, response, targetPort) {
  const requestUrl = new URL(request.url ?? "/", "http://127.0.0.1:5183");
  const headers = { ...request.headers, host: `127.0.0.1:${targetPort}` };
  for (const name of HOP_BY_HOP_HEADERS) delete headers[name];

  const upstream = httpRequest(
    {
      hostname: "127.0.0.1",
      port: targetPort,
      method: request.method,
      path: `${requestUrl.pathname}${requestUrl.search}`,
      headers,
    },
    (upstreamResponse) => {
      const responseHeaders = { ...upstreamResponse.headers };
      for (const name of HOP_BY_HOP_HEADERS) delete responseHeaders[name];
      responseHeaders["cache-control"] = "no-store";
      response.writeHead(upstreamResponse.statusCode ?? 502, responseHeaders);
      upstreamResponse.pipe(response);
    },
  );

  upstream.on("error", () => {
    if (!response.headersSent)
      writeText(response, 502, "Integration service unavailable.");
    else response.destroy();
  });
  request.on("aborted", () => upstream.destroy());
  request.pipe(upstream);
}

export async function startBuiltServer({
  distDirectory,
  host = "127.0.0.1",
  port = 5183,
  backendPort = 18082,
}) {
  const root = await realpath(distDirectory);
  const indexPath = resolve(root, "index.html");
  const indexInfo = await stat(indexPath).catch(() => null);
  if (!indexInfo?.isFile()) {
    throw new Error(
      "The isolated production build does not contain index.html.",
    );
  }

  const server = createServer(async (request, response) => {
    const parsed = new URL(request.url ?? "/", `http://${host}:${port}`);
    const pathname = decodeSafePath(parsed.pathname);
    if (!pathname) {
      writeText(response, 400, "Invalid request path.");
      return;
    }

    if (
      pathname === "/api/v1" ||
      pathname.startsWith("/api/v1/") ||
      pathname === "/v3/api-docs" ||
      pathname.startsWith("/v3/api-docs/")
    ) {
      proxyApi(request, response, backendPort);
      return;
    }

    if (request.method !== "GET" && request.method !== "HEAD") {
      writeText(response, 405, "Method not allowed.");
      return;
    }

    const candidate = resolve(root, `.${pathname}`);
    if (!isInside(root, candidate)) {
      writeText(response, 404, "Not found.");
      return;
    }

    let filePath = candidate;
    let fileInfo = await stat(filePath).catch(() => null);
    if (!fileInfo?.isFile() && extname(pathname) === "") {
      filePath = indexPath;
      fileInfo = indexInfo;
    }
    if (!fileInfo?.isFile()) {
      writeText(response, 404, "Not found.");
      return;
    }

    const type = MIME_TYPES.get(extname(filePath).toLowerCase());
    if (!type) {
      writeText(response, 404, "Not found.");
      return;
    }
    response.writeHead(200, {
      "Cache-Control": "no-store",
      "Content-Length": fileInfo.size,
      "Content-Type": type,
      "X-Content-Type-Options": "nosniff",
    });
    if (request.method === "HEAD") response.end();
    else createReadStream(filePath).pipe(response);
  });

  await new Promise((resolveListen, rejectListen) => {
    server.once("error", rejectListen);
    server.listen(port, host, () => {
      server.removeListener("error", rejectListen);
      resolveListen();
    });
  });

  return {
    close: () =>
      new Promise((resolveClose, rejectClose) => {
        server.close((error) => (error ? rejectClose(error) : resolveClose()));
      }),
  };
}
