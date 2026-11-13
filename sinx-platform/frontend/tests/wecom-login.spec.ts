import {test,expect,Page} from '@playwright/test';

const wecomUA='Mozilla/5.0 (iPhone) AppleWebKit/605.1.15 MicroMessenger/8.0 wxwork/4.1.38';
async function mockLogin(page:Page,{enabled=true,loggedIn=false}={}) {
 let signedIn=loggedIn;
 const starts:string[]=[];
 await page.route('**/api/**',async route=>{
  const path=new URL(route.request().url()).pathname;
  if(path==='/api/auth/providers')return route.fulfill({json:[{id:'wecom',name:'企业微信',enabled}]});
  if(path==='/api/me')return route.fulfill({status:signedIn?200:401,json:signedIn?{id:'member-1',displayName:'林言',role:'MEMBER'}:{code:'unauthenticated'}});
  if(path==='/api/applications')return route.fulfill({json:[]});
  if(path==='/api/auth/csrf')return route.fulfill({json:{token:'test-csrf',headerName:'X-CSRF-TOKEN'}});
  if(path==='/api/auth/logout') {
   expect(route.request().headers()['x-csrf-token']).toBe('test-csrf');
   signedIn=false;return route.fulfill({status:204});
  }
  if(path==='/api/auth/wecom/start') {
   starts.push(route.request().headers()['user-agent']);
   return route.fulfill({contentType:'text/html; charset=utf-8',body:'<h1>企微授权入口</h1>'});
  }
  return route.fulfill({status:404,json:{}});
 });
 return starts;
}

test.describe('企微内打开',()=>{
 test.use({userAgent:wecomUA});
 test('未登录成员自动发起一次企微授权',async({page})=>{
  const starts=await mockLogin(page);await page.goto('/login');
  await expect(page).toHaveURL(/\/api\/auth\/wecom\/start$/);
  await expect(page.getByRole('heading',{name:'企微授权入口'})).toBeVisible();
  expect(starts).toEqual([wecomUA]);
 });
 test('已有会话直接进入工作台',async({page})=>{
  const starts=await mockLogin(page,{loggedIn:true});await page.goto('/login');
  await expect(page.getByRole('heading',{name:'工作台'})).toBeVisible();
  expect(starts).toHaveLength(0);
 });
 test('未开通企微时保持禁用入口',async({page})=>{
  const starts=await mockLogin(page,{enabled:false});await page.goto('/login');
  await expect(page.getByRole('button',{name:'使用企业微信登录'})).toBeDisabled();
  expect(starts).toHaveLength(0);
 });
 for(const [code,message] of [['identity_unlinked','账号尚未关联，请联系管理员'],['authorization_denied','授权未完成，请重新登录']]) {
  test(`授权失败 ${code} 不自动循环，允许手动重试`,async({page})=>{
   const starts=await mockLogin(page);await page.goto('/login?error='+code);
   await expect(page.getByRole('alert')).toContainText(message);
   await expect(page.getByRole('button',{name:'使用企业微信登录'})).toBeEnabled();
   expect(starts).toHaveLength(0);
   await page.getByRole('button',{name:'使用企业微信登录'}).click();
   await expect(page.getByRole('heading',{name:'企微授权入口'})).toBeVisible();
   expect(starts).toHaveLength(1);
  });
 }
 test('主动退出保持登录页且手动登录仍可用',async({page})=>{
  const starts=await mockLogin(page,{loggedIn:true});await page.goto('/workspace');
  await page.getByRole('button',{name:'退出登录'}).click();
  await expect(page).toHaveURL(/\/login\?logged_out=1$/);
  await expect(page.getByRole('button',{name:'使用企业微信登录'})).toBeEnabled();
  await page.reload();
  await expect(page.getByRole('button',{name:'使用企业微信登录'})).toBeEnabled();
  expect(starts).toHaveLength(0);
  await page.getByRole('button',{name:'使用企业微信登录'}).click();
  await expect(page.getByRole('heading',{name:'企微授权入口'})).toBeVisible();
 });
});

test('桌面企微支持大写WXWork标记',async({browser})=>{
 const context=await browser.newContext({userAgent:'Mozilla/5.0 (Windows NT 10.0) WXWork/4.1.38'});
 try {
  const page=await context.newPage();await mockLogin(page);await page.goto('http://localhost:8000/login');
  await expect(page.getByRole('heading',{name:'企微授权入口'})).toBeVisible();
 } finally {await context.close();}
});

for(const ua of ['Mozilla/5.0 Chrome/131.0','Mozilla/5.0 MicroMessenger/8.0','Mozilla/5.0 fakewxwork/1.0']) {
 test(`非企微浏览器 ${ua} 保留手动登录`,async({browser})=>{
  const context=await browser.newContext({userAgent:ua});
  try {
   const page=await context.newPage();const starts=await mockLogin(page);await page.goto('http://localhost:8000/login');
   await expect(page.getByRole('button',{name:'使用企业微信登录'})).toBeEnabled();
   expect(starts).toHaveLength(0);
   await page.getByRole('button',{name:'使用企业微信登录'}).click();
   await expect(page.getByRole('heading',{name:'企微授权入口'})).toBeVisible();
  } finally {await context.close();}
 });
}
