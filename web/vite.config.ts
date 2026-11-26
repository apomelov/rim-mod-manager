import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// In development the Ktor server runs separately; API calls are proxied to it.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { "/api": "http://127.0.0.1:8765" },
  },
});
