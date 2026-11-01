import { defineConfig } from "vite";
export default defineConfig({
  base: "./",
  plugins: [
    {
      name: "legacy-webview-script",
      enforce: "post",
      transformIndexHtml: (html) =>
        html.replace(/type="module" crossorigin/g, "defer"),
    },
  ],
  publicDir: false,
  build: {
    outDir: "app/src/main/assets",
    emptyOutDir: false,
    target: "chrome58",
    // One local IIFE includes the offline vector pack and works on API 26's
    // original WebView without ES modules or network chunk loading.
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        format: "iife",
        name: "EL110",
        inlineDynamicImports: true,
        entryFileNames: "bundles/game.js",
        chunkFileNames: "bundles/[name]-[hash].js",
        assetFileNames: "bundles/[name][extname]",
      },
    },
  },
});
