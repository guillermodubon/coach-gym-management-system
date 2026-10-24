import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";
import { AppProviders } from "./app/AppProviders";
import "./styles.css";
import { applyInitialTheme } from "./shared/theme/theme-preference";
import { capturePublicIdentityRuntime } from "./features/identity/public-identity-runtime";

// Capture and remove one-time identity secrets before React or the router starts.
const publicIdentityRuntime = capturePublicIdentityRuntime(
  window.location,
  window.history,
);

applyInitialTheme();

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("No se encontró el contenedor de la aplicación.");
}

createRoot(rootElement).render(
  <StrictMode>
    <AppProviders publicIdentityRuntime={publicIdentityRuntime}>
      <App />
    </AppProviders>
  </StrictMode>,
);
