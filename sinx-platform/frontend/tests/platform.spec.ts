import {test,expect,Page} from '@playwright/test';
const providers=[{id:'feishu',name:'飞书',enabled:false},{id:'wecom',name:'企业微信',enabled:false}];
async function api(page:Page,loggedIn=false){
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/auth/providers')return route.fulfill({json:providers});
  if(path==='/api/me')return route.fulfill({status:loggedIn?200:401,json:loggedIn?{id:'member-1',displayName:'林言',role:'MEMBER'}:{code:'unauthenticated'}});
  if(path==='/api/applications')return route.fulfill({json:[]});
  if(path==='/api/auth/csrf')return route.fulfill({json:{token:'csrf-test-token',headerName:'X-CSRF-TOKEN'}});
  if(path==='/api/auth/logout'){
   expect(route.request().headers()['x-csrf-token']).toBe('csrf-test-token');loggedIn=false;return route.fulfill({status:204});
  }
  return route.fulfill({status:404,json:{}});
 });
}
test('login displays both providers and honest unavailable status',async({page})=>{
 await api(page);await page.goto('/login');
 await expect(page.getByRole('heading',{name:'登录信计 X 平台'})).toBeVisible();
 await expect(page.getByRole('button',{name:/飞书登录/})).toBeDisabled();
 await expect(page.getByRole('button',{name:/企业微信登录/})).toBeDisabled();
 await expect(page.getByText('暂未开通')).toHaveCount(2);
});
test('anonymous workspace redirects to login',async({page})=>{
 await api(page);await page.goto('/workspace');await expect(page).toHaveURL('http://localhost:8000/login');
});
test('workspace shows real empty state and logout sends csrf',async({page})=>{
 await api(page,true);await page.goto('/workspace');
 await expect(page.getByRole('heading',{name:'工作台'})).toBeVisible();
 await expect(page.getByText('暂无可用应用')).toBeVisible();
 await expect(page.locator('aside')).toHaveCount(0);
 await page.getByRole('button',{name:'退出登录'}).click();
 await expect(page).toHaveURL('http://localhost:8000/login?logged_out=1');
});
test('login provider errors are understandable and do not echo input',async({page})=>{
 await api(page);await page.goto('/login?error=invalid_state');
 await expect(page.getByRole('alert')).toContainText('登录已过期，请重新发起登录');
 await page.goto('/login?error=untrusted-input');await expect(page.getByText('untrusted-input')).toHaveCount(0);
});

for (const code of ['__proto__','constructor']) {
 test(`login safely handles inherited error key ${code}`,async({page})=>{
  const runtimeErrors:string[]=[];
  page.on('pageerror',error=>runtimeErrors.push(error.message));
  await api(page);await page.goto('/login?error='+encodeURIComponent(code));
  await expect(page.getByRole('heading',{name:'登录信计 X 平台'})).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('登录失败，请稍后重试');
  expect(runtimeErrors).toEqual([]);
 });
}
test('network failures offer a retry instead of an empty page',async({page})=>{
 await page.route('**/api/**',route=>route.fulfill({status:503,json:{}}));
 await page.goto('/login');await expect(page.getByRole('button',{name:'重试'})).toBeVisible();
});
test('mobile login does not overflow',async({page})=>{
 await api(page);await page.setViewportSize({width:390,height:844});await page.goto('/login');
 await expect(page.getByRole('heading',{name:'登录信计 X 平台'})).toBeVisible();
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
