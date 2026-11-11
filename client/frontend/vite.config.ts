import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    host: true, // listen on 0.0.0.0 inside the container
    port: 5173,
    strictPort: true,
    allowedHosts: true, // requests arrive via the Nginx gateway (localhost or LAN IP)
    watch: { usePolling: true, interval: 300 }, // macOS bind mounts need polling
    hmr: { clientPort: 80 }, // browser reaches the HMR websocket through the gateway
  },
});
