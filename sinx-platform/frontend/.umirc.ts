import { defineConfig } from 'umi';
export default defineConfig({
 title: '信计 X 平台 · SinX Platform',
 routes: [
  { path: '/', redirect: '/workspace' },
  { path: '/login', component: '@/pages/Login' },
  { path: '/workspace', component: '@/pages/Workspace' },
  { path: '/members', component: '@/pages/Members' },
  { path: '*', component: '@/pages/NotFound' },
 ],
 proxy: { '/api': { target: 'http://localhost:8080', changeOrigin: false } },
 npmClient: 'npm',
 hash: true,
 favicons: ['/favicon.svg'],
 metas: [{ name: 'viewport', content: 'width=device-width, initial-scale=1' }],
});
