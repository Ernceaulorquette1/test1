import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

// Pruebas de integración frontend/backend: requieren el backend en marcha (API_URL, por defecto http://localhost:8000/api/v1).
export default defineConfig({
  plugins: [react()],
  test: {
    env: { VITE_API_URL: process.env.API_URL ?? "http://localhost:8000/api/v1" }, environment: "jsdom", globals: true, setupFiles: ["./src/test/setup.ts"], include: ["src/test/integration.test.tsx"], testTimeout: 30000 },
});
