import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

function backendOrigin(): string {
  const configuredOrigin = process.env.COACH_GYM_BACKEND_ORIGIN?.trim();
  const origin = new URL(configuredOrigin || "http://localhost:8080");

  if (
    !["http:", "https:"].includes(origin.protocol) ||
    origin.username ||
    origin.password ||
    origin.pathname !== "/" ||
    origin.search ||
    origin.hash
  ) {
    throw new Error(
      "COACH_GYM_BACKEND_ORIGIN debe contener solo el origen HTTP(S) del backend.",
    );
  }

  return origin.origin;
}

const apiTarget = backendOrigin();

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": { target: apiTarget },
      "/v3/api-docs": { target: apiTarget },
    },
  },
  test: {
    environment: "jsdom",
    include: ["src/**/*.test.{ts,tsx}"],
    setupFiles: ["./src/test/setup.ts"],
    clearMocks: true,
    restoreMocks: true,
  },
});
