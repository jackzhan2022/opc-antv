import { test, expect, Page } from '@playwright/test';

const identity={id:'identity-1',provider:'wecom',tenantId:'corp',subjectId:'user-1',displayName:'林言',linkedAt:'2026-11-13T06:00:00Z'};
const initial=[
 {id:'admin',displayName:'管理员',role:'ADMIN',enabled:true,createdAt:'2026-11-13T06:00:00Z',lastLoginAt:'2026-11-13T06:00:00Z',identities:[{...identity,id:'admin-identity',subjectId:'admin'}]},
 {id:'member',displayName:'林言',role:'MEMBER',enabled:true,createdAt:'2026-11-13T06:00:00Z',lastLoginAt:null,identities:[]},
];
async function mock(page:Page,role:'ADMIN'|'MEMBER'|null='ADMIN') {
 let members=structuredClone(initial);
 let pending=[{id:'pending-1',provider:'wecom',tenantId:'corp',subjectId:'user-1',displayName:'林言',firstSeenAt:'2026-11-13T06:00:00Z',lastSeenAt:'2026-11-13T06:30:00Z'}];
 const writes:{method:string;path:string;body:any}[]=[];
 await page.route('**/api/**',async route=>{
  const req=route.request();const url=new URL(req.url());const path=url.pathname;const method=req.method();
  if(path==='/api/me')return route.fulfill({status:role?200:401,json:role?{id:role==='ADMIN'?'admin':'member',displayName:role==='ADMIN'?'管理员':'林言',role}:{code:'unauthenticated'}});
  if(path==='/api/auth/providers')return route.fulfill({json:[]});
  if(path==='/api/applications')return route.fulfill({json:[]});
  if(path==='/api/auth/csrf')return route.fulfill({json:{token:'test-csrf',headerName:'X-CSRF-TOKEN'}});
  if(path.startsWith('/api/admin/') && role!=='ADMIN')return route.fulfill({status:role?403:401,json:{code:role?'forbidden':'unauthenticated'}});
  if(method!=='GET') {
   expect(req.headers()['x-csrf-token']).toBe('test-csrf');
   writes.push({method,path,body:req.postData()?req.postDataJSON():null});
  }
  if(path==='/api/admin/members' && method==='GET') {
   const q=url.searchParams.get('q')||'';
   const items=members.filter(m=>m.displayName.includes(q) && (url.searchParams.get('enabledOnly')!=='true'||m.enabled));
   return route.fulfill({json:{items,total:items.length,page:1,pageSize:20}});
  }
  if(path==='/api/admin/members' && method==='POST') {
   const created={...initial[1],...req.postDataJSON(),id:'new-member',identities:[]};members.push(created);
   return route.fulfill({status:201,json:created});
  }
  if(path==='/api/admin/members/member' && method==='PATCH') {
   members=members.map(m=>m.id==='member'?{...m,...req.postDataJSON()}:m);
   return route.fulfill({json:members.find(m=>m.id==='member')});
  }
  if(path==='/api/admin/pending-identities' && method==='GET')return route.fulfill({json:{items:pending,total:pending.length,page:1,pageSize:20}});
  if(path==='/api/admin/pending-identities/pending-1/bind') {
   expect(req.postDataJSON()).toEqual({memberId:'member'});pending=[];members[1].identities=[identity];
   return route.fulfill({status:204});
  }
  if(path==='/api/admin/members/member/identities/identity-1' && method==='DELETE') {
   members[1].identities=[];return route.fulfill({status:204});
  }
  if(path==='/api/admin/audit')return route.fulfill({json:{items:[{id:'audit-1',actorName:'管理员',action:'IDENTITY_LINKED',targetName:'林言',detail:'企业微信账号已关联',createdAt:'2026-11-13T06:00:00Z'}],total:1,page:1,pageSize:20}});
  return route.fulfill({status:404,json:{}});
 });
 return writes;
}

test('unlinked identity explains that administrator linking is required',async({page})=>{
 await mock(page,null);await page.goto('/login?error=identity_unlinked');
 await expect(page.getByRole('alert')).toContainText('账号尚未关联，请联系管理员');
});
test('ordinary member has no administration entry and cannot open management page',async({page})=>{
 await mock(page,'MEMBER');await page.goto('/workspace');
 await expect(page.getByRole('heading',{name:'工作台'})).toBeVisible();
 await expect(page.getByRole('link',{name:'成员管理'})).toHaveCount(0);
 await page.goto('/members');await expect(page.getByText('暂无管理权限')).toBeVisible();
 await expect(page.getByRole('button',{name:'新增成员'})).toHaveCount(0);
});
test('anonymous management page redirects to login',async({page})=>{
 await mock(page,null);await page.goto('/members');await expect(page).toHaveURL(/\/login$/);
});
test('administrator creates and edits members with csrf',async({page})=>{
 const writes=await mock(page);await page.goto('/members');
 await expect(page.getByRole('heading',{name:'成员管理'})).toBeVisible();
 await expect(page.locator('aside')).toHaveCount(0);
 await page.getByRole('button',{name:'新增成员'}).click();
 const dialog=page.getByRole('dialog');
 await dialog.getByLabel('姓名',{exact:true}).fill('新员工');
 await dialog.getByRole('button',{name:'保存',exact:true}).click();
 await expect(page.getByRole('cell',{name:'新员工',exact:true})).toBeVisible();
 await page.getByRole('row').filter({has:page.getByRole('cell',{name:'林言',exact:true})}).getByRole('button',{name:'编辑',exact:true}).click();
 await dialog.getByLabel('姓名',{exact:true}).fill('林言新姓名');
 await dialog.getByRole('button',{name:'保存',exact:true}).click();
 await expect(page.getByRole('cell',{name:'林言新姓名',exact:true})).toBeVisible();
 await page.screenshot({path:'test-results/member-management-desktop.png',fullPage:true});
 expect(writes.map(w=>w.method)).toEqual(['POST','PATCH']);
});
test('administrator links a pending identity and can unlink it explicitly',async({page})=>{
 const writes=await mock(page);await page.goto('/members');
 await page.getByRole('tab',{name:/待关联账号/}).click();
 await page.getByRole('button',{name:'关联成员',exact:true}).click();
 const dialog=page.getByRole('dialog');
 await expect(dialog).toContainText('user-1');
 await page.screenshot({path:'test-results/member-binding-desktop.png',fullPage:true});
 await dialog.getByRole('combobox').click();
 await page.getByRole('option',{name:/林言/}).click();
 await dialog.getByRole('button',{name:'确认关联',exact:true}).click();
 await expect(page.getByText('暂无待关联账号')).toBeVisible();
 await page.getByRole('tab',{name:'成员',exact:true}).click();
 await page.getByRole('row').filter({has:page.getByRole('cell',{name:'林言',exact:true})}).getByRole('button',{name:'登录账号',exact:true}).click();
 await dialog.getByRole('button',{name:'解除关联',exact:true}).click();
 await page.getByRole('button',{name:'确认解除',exact:true}).click();
 await expect(dialog.getByText('尚未关联登录账号')).toBeVisible();
 expect(writes.map(w=>w.method)).toEqual(['POST','DELETE']);
});
test('business errors are shown and pending binding remains reviewable',async({page})=>{
 await mock(page);
 await page.route('**/api/admin/pending-identities/*/bind',route=>route.fulfill({status:409,json:{code:'provider_already_linked'}}));
 await page.goto('/members');await page.getByRole('tab',{name:/待关联账号/}).click();
 await page.getByRole('button',{name:'关联成员',exact:true}).click();
 const dialog=page.getByRole('dialog');await dialog.getByRole('combobox').click();
 await page.getByRole('option',{name:/林言/}).click();await dialog.getByRole('button',{name:'确认关联',exact:true}).click();
 await expect(dialog.getByRole('alert')).toContainText('已有关联的同渠道账号');
 await expect(dialog).toBeVisible();
});
test('audit is visible and mobile management avoids document overflow',async({page})=>{
 await page.emulateMedia({reducedMotion:'reduce'});
 await mock(page);await page.setViewportSize({width:390,height:844});await page.goto('/members');
 await page.getByRole('tab',{name:'操作记录',exact:true}).click();
 await expect(page.getByText('企业微信账号已关联')).toBeVisible();
 await expect(page.locator('.ant-spin-spinning')).toHaveCount(0);
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
 await page.screenshot({path:'test-results/member-management-mobile.png',fullPage:true});
});
