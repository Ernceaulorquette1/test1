/// <reference types="vitest" />
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";

export default defineConfig({
  plugins: [react()],
  build: { rollupOptions: { output: { manualChunks: { charts: ["recharts"], vendor: ["react", "react-dom", "react-router-dom", "@tanstack/react-query", "axios"] } } }, chunkSizeWarningLimit: 700 },
  server: { port: 5173, host: true },
  test: { environment: "jsdom", globals: true, setupFiles: ["./src/test/setup.ts"], css: false,
          exclude: ["node_modules", "src/test/integration.test.tsx"] },
});
