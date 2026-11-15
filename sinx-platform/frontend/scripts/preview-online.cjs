// Local frontend over the real origin, so production OAuth and Secure cookies work unchanged.
const { chromium } = require('@playwright/test');
const fs = require('node:fs/promises');
const path = require('node:path');

const origin = 'https://x.aixmax.cn';
const local = 'http://localhost:8000';
const directory = path.resolve(__dirname, '../../.local/online-preview');
const statusPath = path.join(directory, 'status.json');

async function main() {
  const health = await fetch(local + '/api/health', { signal: AbortSignal.timeout(10000) });
  if (!health.ok || (await health.json()).status !== 'UP') throw new Error('Start dev:online first');
  await fs.mkdir(directory, { recursive: true });
  const context = await chromium.launchPersistentContext(path.join(directory, 'browser-profile'), {
    channel: 'msedge', headless: false, viewport: { width: 1440, height: 900 }, serviceWorkers: 'block',
  });
  await context.route(origin + '/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    if (request.method() !== 'GET' || url.pathname.startsWith('/api/') || url.pathname.startsWith('/creator-api/') || url.pathname === '/creators' || url.pathname.startsWith('/creators/')) {
      await route.continue();
      return;
    }
    try {
      // A fresh fetch forwards only public asset requests, never the browser's cookies or authorization headers.
      const response = await fetch(new URL(url.pathname + url.search, local), {
        headers: { accept: request.headers().accept || '*/*' },
        redirect: 'manual', signal: AbortSignal.timeout(30000),
      });
      await route.fulfill({
        status: response.status, body: Buffer.from(await response.arrayBuffer()),
        headers: { 'content-type': response.headers.get('content-type') || 'application/octet-stream', 'cache-control': 'no-store', 'x-sinx-local-frontend': '1' },
      });
    } catch {
      await route.abort('failed').catch(() => {});
      console.error('Local frontend asset unavailable; check dev:online.');
    }
  });
  const page = context.pages()[0] || await context.newPage();
  let writing = false;
  let captured = false;
  async function update() {
    if (writing || page.isClosed()) return;
    writing = true;
    try {
      const url = new URL(page.url());
      const state = { running: true, origin: url.origin, pathname: url.pathname, localPortal: false, authenticated: false, applications: 0 };
      if (url.origin === origin) {
        state.localPortal = await page.locator('.workspace-portal').count() > 0;
        state.authenticated = await page.locator('.account .member-name').count() > 0;
        state.applications = await page.locator('.workspace-portal .app-card').count();
      }
      await fs.writeFile(statusPath, JSON.stringify(state), 'utf8');
      if (!captured && state.localPortal && state.authenticated && await page.locator('.workspace-loading').count() === 0) {
        await page.screenshot({ path: path.join(directory, 'workspace.png'), fullPage: true });
        captured = true;
      }
    } catch {
      // Do not log exception messages: navigation errors can contain OAuth codes or session data.
    } finally { writing = false; }
  }
  page.on('domcontentloaded', update);
  const timer = setInterval(update, 2000);
  context.on('close', () => {
    clearInterval(timer);
    fs.writeFile(statusPath, JSON.stringify({ running: false }), 'utf8').finally(() => process.exit(0));
  });
  await page.goto(origin + '/workspace');
  await update();
  console.log('Local frontend preview opened. Sign in with your real company account if prompted.');
  console.log('Frontend: localhost:8000; API and OAuth: x.aixmax.cn.');
}

main().catch(error => { console.error('Preview could not start (' + error.name + '). Check the local server and Edge installation.'); process.exit(1); });
