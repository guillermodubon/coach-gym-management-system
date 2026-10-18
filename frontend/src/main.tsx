import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";
import { AppProviders } from "./app/AppProviders";
import "./styles.css";
import { applyInitialTheme } from "./shared/theme/theme-preference";
import { BrowserRouter } from "react-router";

applyInitialTheme();

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("No se encontró el contenedor de la aplicación.");
}

createRoot(rootElement).render(
  <StrictMode>
    <BrowserRouter>
      <AppProviders>
        <App />
      </AppProviders>
    </BrowserRouter>
  </StrictMode>,
);
