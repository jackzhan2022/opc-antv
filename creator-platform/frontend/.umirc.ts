import { defineConfig } from "umi";
export default defineConfig({
  title: "创作者 · 信计 X",
  base: "/creators",
  publicPath: "/creators/",
  routes: [
    { path: "/", component: "@/pages/Registry" },
    { path: "/new", component: "@/pages/Editor" },
    { path: "/:id/edit", component: "@/pages/Editor" },
    { path: "/auth-error", component: "@/pages/AuthError" },
    { path: "/occupations", component: "@/pages/Occupations" },
    { path: "/tags", component: "@/pages/Tags" },
    { path: "/roles", component: "@/pages/Roles" },
    { path: "/:id", component: "@/pages/Detail" },
    { path: "*", component: "@/pages/NotFound" },
  ],
  proxy: {
    "/creator-api": { target: "http://localhost:8081", changeOrigin: false },
  },
  define: {
    "process.env.SINX_PLATFORM_URL": process.env.SINX_PLATFORM_URL || "/",
  },
  npmClient: "npm",
  hash: true,
  metas: [{ name: "viewport", content: "width=device-width, initial-scale=1" }],
});
