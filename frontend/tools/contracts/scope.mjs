export const f01Operations = {
  "/api/v1/auth/csrf": "get",
  "/api/v1/auth/login": "post",
  "/api/v1/auth/me": "get",
  "/api/v1/auth/logout": "post",
  "/api/v1/me/profile/password": "post",
  "/api/v1/me/branch-context": "get",
};

export const f02Operations = {
  "/api/v1/me/profile": ["get", "put"],
  "/api/v1/me/profile/photo": ["get", "put", "delete"],
  "/api/v1/me/profile/password": ["post"],
  "/api/v1/auth/password-recovery-requests": ["post"],
  "/api/v1/auth/password-recovery/complete": ["post"],
  "/api/v1/staff-invitations/inspect": ["post"],
  "/api/v1/staff-invitations/accept": ["post"],
};

function projectScope(source, title, description, operations) {
  if (!source?.openapi?.startsWith("3.") || !source.info?.version) {
    throw new Error("Se requiere un documento OpenAPI 3 válido.");
  }
  const result = {
    openapi: source.openapi,
    info: { title, version: source.info.version, description },
    servers: [{ url: "/" }],
    paths: {},
    components: {},
  };
  const visited = new Set();
  function collect(value) {
    if (!value || typeof value !== "object") return;
    if (typeof value.$ref === "string") {
      const reference = value.$ref;
      const match = /^#\/components\/([^/]+)\/([^/]+)$/.exec(reference);
      if (!match)
        throw new Error(
          "El contrato solo admite referencias locales a components.",
        );
      if (!visited.has(reference)) {
        visited.add(reference);
        const [, group, encodedName] = match;
        const name = encodedName.replaceAll("~1", "/").replaceAll("~0", "~");
        const component = source.components?.[group]?.[name];
        if (!component)
          throw new Error("Hay una referencia de contrato sin resolver.");
        result.components[group] ??= {};
        result.components[group][name] = structuredClone(component);
        collect(component);
      }
    }
    for (const child of Object.values(value)) collect(child);
  }
  for (const [path, methods] of Object.entries(operations)) {
    const selectedMethods = Array.isArray(methods) ? methods : [methods];
    const projectedPath = {};
    for (const method of selectedMethods) {
      const operation = source.paths?.[path]?.[method];
      if (!operation?.responses)
        throw new Error(`Falta la operación ${method} ${path}.`);
      projectedPath[method] = structuredClone(operation);
      collect(operation);
      for (const requirement of operation.security ?? []) {
        for (const name of Object.keys(requirement)) {
          const scheme = source.components?.securitySchemes?.[name];
          if (!scheme)
            throw new Error("Falta un esquema de seguridad del contrato.");
          result.components.securitySchemes ??= {};
          result.components.securitySchemes[name] = structuredClone(scheme);
        }
      }
    }
    result.paths[path] = projectedPath;
  }
  return result;
}

/** Project actual Springdoc output without strengthening backend schemas. */
export function projectF01(source) {
  return projectScope(
    source,
    "Coach Gym - F01 HTTP contracts",
    "Proyección de las seis operaciones de F01 desde Springdoc. Los esquemas conservan la forma exportada por el backend.",
    f01Operations,
  );
}

/** Project actual Springdoc output without strengthening backend schemas. */
export function projectF02(source) {
  return projectScope(
    source,
    "Coach Gym - F02 HTTP contracts",
    "Proyección de las diez operaciones H01-H10 de F02 desde Springdoc. El contrato conserva las respuestas reales del backend, incluida la respuesta H09 sin schema declarado.",
    f02Operations,
  );
}

export function validateSnapshot(snapshot, scope = "f01") {
  const projectors = { f01: projectF01, f02: projectF02 };
  const project = projectors[scope];
  if (!project) throw new Error("El scope del contrato debe ser f01 o f02.");
  if (JSON.stringify(project(snapshot)) !== JSON.stringify(snapshot)) {
    throw new Error(
      `La entrada debe contener únicamente la proyección revisada de ${scope.toUpperCase()}.`,
    );
  }
}
