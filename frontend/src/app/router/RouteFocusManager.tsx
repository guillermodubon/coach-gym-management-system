import { useEffect } from "react";
import { useLocation } from "react-router";

const titles: Record<string, string> = {
  "/": "Inicio · Coach Gym",
  "/login": "Iniciar sesión · Coach Gym",
  "/cambio-contrasena-obligatorio":
    "Cambio de contraseña requerido · Coach Gym",
  "/acceso-restringido": "Acceso restringido · Coach Gym",
};

export function RouteFocusManager() {
  const location = useLocation();

  useEffect(() => {
    document.title =
      titles[location.pathname] ?? "Página no encontrada · Coach Gym";
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
