import { test, expect, Page } from '@playwright/test';

const creator={id:'creator-platform',code:'creator-platform',name:'创作者管理',description:'创作者档案与作品资料',url:'https://x.example.test/creators/',integrationType:'SSO',icon:'users',sortOrder:0,enabled:true,connected:true,version:1,accessVersion:1,grantedMembers:0};
async function mock(page:Page,role:'ADMIN'|'MEMBER'|null='ADMIN',granted=false) {
 let apps=[structuredClone(creator)];
 let access=[{id:'member',displayName:'运营成员',role:'MEMBER',enabled:true,granted,version:granted?1:0}];
 apps[0].grantedMembers=granted?1:0;
 const writes:{path:string;body:any}[]=[];const adminReads:string[]=[];
 await page.route('**/api/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname,method=req.method();
  if(path==='/api/me')return route.fulfill({status:role?200:401,json:role?{id:'admin',displayName:'测试管理员',role}:{code:'unauthenticated'}});
  if(path==='/api/auth/providers')return route.fulfill({json:[]});
  if(path==='/api/applications')return route.fulfill({json:access[0].granted&&apps[0].enabled?[apps[0]]:[]});
  if(path==='/api/auth/csrf')return route.fulfill({json:{token:'application-csrf',headerName:'X-CSRF-TOKEN'}});
  if(path.startsWith('/api/admin/')) {
   if(method==='GET')adminReads.push(path);
   if(role!=='ADMIN')return route.fulfill({status:role?403:401,json:{code:role?'forbidden':'unauthenticated'}});
   if(method!=='GET') {expect(req.headers()['x-csrf-token']).toBe('application-csrf');writes.push({path,body:req.postDataJSON()});}
  }
  if(path==='/api/admin/applications'&&method==='GET')return route.fulfill({json:apps});
  if(path==='/api/admin/applications'&&method==='POST') {
   const created={...creator,...req.postDataJSON(),id:'resource-hub',integrationType:'LINK',grantedMembers:0,version:1};apps.push(created);
   return route.fulfill({status:201,json:created});
  }
  if(path==='/api/admin/applications/creator-platform'&&method==='PATCH') {
   apps[0]={...apps[0],...req.postDataJSON(),version:apps[0].version+1};return route.fulfill({json:apps[0]});
  }
  if(path==='/api/admin/applications/creator-platform/members'&&method==='GET') {
   const q=url.searchParams.get('q')||'',filter=url.searchParams.get('filter')||'all';
   const items=access.filter(member=>member.displayName.includes(q)&&(filter==='all'||(filter==='granted'?member.granted:!member.granted)));
   return route.fulfill({json:{items,total:items.length,page:1,pageSize:20}});
  }
  if(path==='/api/admin/applications/creator-platform/members/member'&&method==='PATCH') {
   const body=req.postDataJSON();expect(body.version).toBe(access[0].version);
   access=[{...access[0],granted:body.granted,version:access[0].version+1}];apps[0].grantedMembers=body.granted?1:0;
   return route.fulfill({json:access[0]});
  }
  if(path==='/api/admin/applications/audit')return route.fulfill({json:{items:[{id:'audit',actorName:'测试管理员',action:'ACCESS_GRANTED',applicationName:'创作者管理',memberName:'运营成员',detail:'分配应用访问权限',createdAt:'2026-11-14T01:00:00Z'}],total:1,page:1,pageSize:20}});
  return route.fulfill({status:404,json:{}});
 });
 return {writes,adminReads};
}

test('administrator registers an application and managed SSO addresses remain readonly',async({page})=>{
 const {writes}=await mock(page);await page.goto('/applications');
 await page.getByRole('button',{name:'登记应用',exact:true}).click();
 const dialog=page.getByRole('dialog');
 await expect(dialog).toContainText('目标系统的访问权限由目标系统管理');
 await dialog.getByLabel('应用名称',{exact:true}).fill('资源中心');
 await dialog.getByLabel('应用标识',{exact:true}).fill('resource-hub');
 await dialog.getByLabel('应用地址',{exact:true}).fill('https://apps.example.test/');
 await dialog.getByRole('button',{name:'保存',exact:true}).click();
 await expect(page.getByRole('row').filter({hasText:'资源中心'})).toContainText('链接入口');
 await page.getByRole('row').filter({hasText:'创作者管理'}).getByRole('button',{name:'编辑',exact:true}).click();
 await expect(dialog.getByLabel('应用标识',{exact:true})).toBeDisabled();
 await expect(dialog.getByLabel('应用地址',{exact:true})).toBeDisabled();
 await dialog.getByLabel('应用名称',{exact:true}).fill('创作者资源');
 await dialog.getByRole('button',{name:'保存',exact:true}).click();
 await expect(page.getByRole('row').filter({hasText:'创作者资源'})).toBeVisible();
 expect(writes.map(write=>write.body.code)).toEqual(['resource-hub','creator-platform']);
 await page.screenshot({path:'test-results/applications-desktop.png',fullPage:true});
});

test('administrator grants and revokes with confirmation and current grant version',async({page})=>{
 const {writes}=await mock(page);await page.goto('/applications');
 await page.getByRole('tab',{name:'应用授权',exact:true}).click();
 await page.getByRole('button',{name:'分配授权',exact:true}).click();
 const dialog=page.getByRole('dialog');await expect(dialog).toContainText('运营成员');
 await dialog.getByRole('button',{name:'确认授权',exact:true}).click();
 await expect(page.getByRole('button',{name:'撤销授权',exact:true})).toBeVisible();
 await page.getByRole('button',{name:'撤销授权',exact:true}).click();
 await dialog.getByRole('button',{name:'确认撤销',exact:true}).click();
 await expect(page.getByRole('button',{name:'分配授权',exact:true})).toBeVisible();
 expect(writes.map(write=>write.body)).toEqual([{granted:true,version:0},{granted:false,version:1}]);
});

test('disabling application hides its workspace entry and keeps audit readable',async({page})=>{
 await mock(page,'ADMIN',true);await page.goto('/workspace');
 await expect(page.getByRole('link',{name:'进入创作者管理'})).toBeVisible();
 await page.goto('/applications');
 await page.getByRole('button',{name:'编辑',exact:true}).click();
 const dialog=page.getByRole('dialog');await dialog.getByRole('switch').click();
 await dialog.getByRole('button',{name:'保存',exact:true}).click();
 await expect(page.getByRole('row').filter({hasText:'creator-platform'})).toContainText('停用');
 await page.getByRole('tab',{name:'操作记录',exact:true}).click();
 await expect(page.getByRole('cell',{name:'分配应用访问权限',exact:true})).toBeVisible();
 await page.goto('/workspace');await expect(page.getByText('暂无可用应用')).toBeVisible();
});

test('ordinary members have no management entry and direct management route is denied',async({page})=>{
 const {adminReads}=await mock(page,'MEMBER');await page.goto('/workspace');
 await expect(page.getByRole('link',{name:'应用管理',exact:true})).toHaveCount(0);
 await page.goto('/applications');await expect(page.getByText('暂无管理权限')).toBeVisible();
 await expect(page.getByRole('button',{name:'登记应用',exact:true})).toHaveCount(0);
 expect(adminReads).toEqual([]);
});

test('stale authorization error remains reviewable without hiding the member',async({page})=>{
 await mock(page);
 await page.route('**/api/admin/applications/creator-platform/members/member',route=>route.fulfill({status:409,json:{code:'application_conflict'}}));
 await page.goto('/applications');await page.getByRole('tab',{name:'应用授权',exact:true}).click();
 await page.getByRole('button',{name:'分配授权',exact:true}).click();
 const dialog=page.getByRole('dialog');await dialog.getByRole('button',{name:'确认授权',exact:true}).click();
 await expect(dialog.getByRole('alert')).toContainText('状态已变更');
 await expect(dialog).toContainText('运营成员');
 await expect(dialog.getByRole('button',{name:'确认授权',exact:true})).toBeDisabled();
 await dialog.getByRole('button',{name:'关闭并刷新',exact:true}).click();
 await expect(dialog).toHaveCount(0);
});

test('application management and authorization dialog fit mobile screens',async({page})=>{
 await mock(page);await page.emulateMedia({reducedMotion:'reduce'});
 for(const width of [320,390]) {
  await page.setViewportSize({width,height:844});await page.goto('/applications');
  await expect(page.getByRole('heading',{name:'应用管理',exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.getByRole('tab',{name:'应用授权',exact:true}).click();
  await expect(page.getByRole('button',{name:'分配授权',exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  if(width===390)await page.screenshot({path:'test-results/application-grants-mobile.png',fullPage:true});
  await page.getByRole('button',{name:'分配授权',exact:true}).click();
  await expect(page.getByRole('dialog').getByRole('button',{name:'确认授权',exact:true})).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCSS('opacity','1');
  if(width===390)await page.screenshot({path:'test-results/application-grant-confirm-mobile.png',fullPage:true});
  await page.getByRole('dialog').getByRole('button',{name:'取消',exact:true}).click();
 }
});

test('application loading errors offer a working retry',async({page})=>{
 await mock(page);let failed=true;
 await page.route('**/api/admin/applications',async route=>{if(failed)return route.fulfill({status:503,json:{}});return route.fallback();});
 await page.goto('/applications');await expect(page.getByRole('alert')).toBeVisible();
 failed=false;await page.getByRole('button',{name:'重试',exact:true}).click();
 await expect(page.getByRole('row').filter({hasText:'creator-platform'})).toBeVisible();
});
