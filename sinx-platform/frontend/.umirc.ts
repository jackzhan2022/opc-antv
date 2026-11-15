import { defineConfig } from 'umi';
const onlineApi = process.env.SINX_ONLINE_API === '1';
export default defineConfig({
 title: '信计 X 平台 · SinX Platform',
 routes: [
  { path: '/', redirect: '/workspace' },
  { path: '/login', component: '@/pages/Login' },
  { path: '/workspace', component: '@/pages/Workspace' },
  { path: '/members', component: '@/pages/Members' },
  { path: '/applications', component: '@/pages/Applications' },
  { path: '*', component: '@/pages/NotFound' },
 ],
 proxy: { '/api': { target: onlineApi ? 'https://x.aixmax.cn' : 'http://localhost:8080', changeOrigin: onlineApi, secure: true } },
 npmClient: 'npm',
 hash: true,
 favicons: ['/favicon.svg'],
 metas: [{ name: 'viewport', content: 'width=device-width, initial-scale=1' }],
});
