import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// The UI is served by adminConsole from the classpath under /console/. Relative asset paths keep it working when
// the console is mounted below a path prefix.
export default defineConfig({
  base: "./",
  plugins: [react()],
  build: {
    outDir: "target/classes/com/predic8/membrane/core/interceptor/administration/docBase/console",
    emptyOutDir: true,
  },
  server: {
    // npm run dev: talk to a Membrane with an adminConsole on port 9000
    proxy: { "/admin": "http://localhost:9000" },
  },
  test: {
    environment: "jsdom",
    globals: true,
  },
});
