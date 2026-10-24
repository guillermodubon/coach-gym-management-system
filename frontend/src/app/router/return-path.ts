const privatePaths = new Set([
  "/",
  "/mi-perfil",
  "/mi-cuenta",
  "/mi-cuenta/contrasena",
]);

export function safeReturnPath(
  candidate: unknown,
): "/" | "/mi-perfil" | "/mi-cuenta" | "/mi-cuenta/contrasena" {
  if (typeof candidate !== "string" || !privatePaths.has(candidate)) return "/";
  return candidate as
    "/" | "/mi-perfil" | "/mi-cuenta" | "/mi-cuenta/contrasena";
}
