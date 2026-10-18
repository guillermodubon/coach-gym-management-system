import type { CurrentUser } from "../../shared/api/contracts";

export function getAccountDisplayName(user: CurrentUser): string {
  const fullName = user.fullName.trim();
  if (fullName) return fullName;
  const username = user.username.trim();
  return username || "Usuario";
}

export function getAuthorityLabel(user: CurrentUser): string | null {
  if (user.roles.length !== 1) return null;

  const role = user.roles[0];
  if (role === "ADMIN" && user.organizationScope === "ORGANIZATION") {
    return "Administrador · Organización";
  }
  if (role === "ADMIN" && user.organizationScope === "BRANCH") {
    return "Administrador · Sucursales asignadas";
  }
  if (role === "RECEPTIONIST" && user.organizationScope === "BRANCH") {
    return "Recepción · Sucursales asignadas";
  }
  return null;
}
