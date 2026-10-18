export const f01Operations = {
  "/api/v1/auth/csrf": "get",
  "/api/v1/auth/login": "post",
  "/api/v1/auth/me": "get",
  "/api/v1/auth/logout": "post",
  "/api/v1/me/profile/password": "post",
  "/api/v1/me/branch-context": "get",
};

/** Project actual Springdoc output; do not infer or strengthen backend schemas. */
export function projectF01(source) {
  if (!source?.openapi?.startsWith("3.") || !source.info?.version) {
    throw new Error("Se requiere un documento OpenAPI 3 válido.");
  }
  const result = {
    openapi: source.openapi,
    info: {
      title: "Coach Gym - F01 HTTP contracts",
      version: source.info.version,
      description:
        "Proyección de las seis operaciones de F01 desde Springdoc. Los esquemas conservan la forma exportada por el backend.",
    },
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
  for (const [path, method] of Object.entries(f01Operations)) {
    const operation = source.paths?.[path]?.[method];
    if (!operation?.responses)
      throw new Error(`Falta la operación ${method} ${path}.`);
    result.paths[path] = { [method]: structuredClone(operation) };
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
  return result;
}

export function validateSnapshot(snapshot) {
  if (JSON.stringify(projectF01(snapshot)) !== JSON.stringify(snapshot)) {
    throw new Error(
      "La entrada debe contener únicamente la proyección revisada de F01.",
    );
  }
}
