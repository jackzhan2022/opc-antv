import {test,expect,Page} from '@playwright/test';
const providers=[{id:'feishu',name:'飞书',enabled:false},{id:'wecom',name:'企业微信',enabled:false}];
const applications=[{id:'creator-platform',name:'创作者管理',description:'创作者档案与作品资料',url:'/creators/'}];
async function api(page:Page,loggedIn=false,apps:typeof applications=[]){
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/auth/providers')return route.fulfill({json:providers});
  if(path==='/api/me')return route.fulfill({status:loggedIn?200:401,json:loggedIn?{id:'member-1',displayName:'林言',role:'MEMBER'}:{code:'unauthenticated'}});
  if(path==='/api/applications')return route.fulfill({json:apps});
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

test('workspace application entry follows its configured URL using the keyboard',async({page})=>{
 await api(page,true,applications);
 await page.route('**/creators/',route=>route.fulfill({contentType:'text/html; charset=utf-8',body:'<h1>创作者应用</h1>'}));
 await page.goto('/workspace');
 const entry=page.getByRole('link',{name:'进入创作者管理'});
 await expect(entry).toBeVisible();
 await expect(entry).toHaveAttribute('href','/creators/');
 await expect(page.getByText('暂无可用应用')).toHaveCount(0);
 await page.screenshot({path:'test-results/workspace-portal-desktop.png',fullPage:true});
 await entry.focus();await page.keyboard.press('Enter');
 await expect(page).toHaveURL('http://localhost:8000/creators/');
 await expect(page.getByRole('heading',{name:'创作者应用'})).toBeVisible();
});

test('workspace application and navigation remain usable on narrow screens',async({page})=>{
 await api(page,true,applications);
 for(const width of [320,390,768]) {
  await page.setViewportSize({width,height:844});await page.goto('/workspace');
  await expect(page.getByRole('link',{name:'进入创作者管理'})).toBeVisible();
  await expect(page.getByRole('button',{name:'退出登录'})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  if(width===390)await page.screenshot({path:'test-results/workspace-portal-mobile.png',fullPage:true});
 }
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
