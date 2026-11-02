import { useEffect } from "react";
import { useLocation } from "react-router";

const titles: Record<string, string> = {
  "/": "Inicio · Coach Gym",
  "/login": "Iniciar sesión · Coach Gym",
  "/accept-invitation": "Aceptar invitación · Coach Gym",
  "/cambio-contrasena-obligatorio":
    "Cambio de contraseña requerido · Coach Gym",
  "/acceso-restringido": "Acceso restringido · Coach Gym",
  "/mi-perfil": "Mi perfil · Coach Gym",
  "/mi-cuenta": "Ajustes de mi cuenta · Coach Gym",
  "/mi-cuenta/contrasena": "Cambiar contraseña · Coach Gym",
  "/organizacion": "Organización · Coach Gym",
  "/sucursales/nueva": "Nueva sucursal · Coach Gym",
  "/sucursales": "Sucursales · Coach Gym",
};

export function RouteFocusManager() {
  const location = useLocation();

  useEffect(() => {
    document.title =
      titles[location.pathname] ??
      (location.pathname.endsWith("/editar")
        ? "Editar sucursal · Coach Gym"
        : location.pathname.startsWith("/sucursales/")
          ? "Detalle de sucursal · Coach Gym"
          : "Página no encontrada · Coach Gym");
    const frame = window.requestAnimationFrame(() => {
      const target =
        document.getElementById("main-heading") ??
        document.getElementById("main-content");
      target?.focus({ preventScroll: true });
    });
    return () => window.cancelAnimationFrame(frame);
  }, [location.key, location.pathname]);

  return null;
}
