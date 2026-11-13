export type Provider = { id: 'feishu' | 'wecom'; name: string; enabled: boolean };
export type Member = { id: string; displayName: string; role: 'ADMIN' | 'MEMBER' };
export type Application = { id: string; name: string; description: string; url: string };
export type Identity = {id:string;provider:'feishu'|'wecom';tenantId:string;subjectId:string;displayName:string;linkedAt:string};
export type ManagedMember = Member & {enabled:boolean;createdAt:string;lastLoginAt:string|null;identities:Identity[]};
export type MemberInput = {displayName:string;role:Member['role'];enabled:boolean};
export type PendingIdentity = Omit<Identity,'linkedAt'> & {firstSeenAt:string;lastSeenAt:string};
export type AuditEntry = {id:string;actorName:string;action:string;targetName:string;detail:string;createdAt:string};
export type PageResult<T> = {items:T[];total:number;page:number;pageSize:number};
const businessErrors:Record<string,string>={
 forbidden:'暂无管理权限',invalid_request:'请检查输入内容',member_not_found:'成员不存在，请刷新列表',
 pending_not_found:'该账号已处理，请刷新列表',identity_not_found:'该关联已变更，请刷新列表',
 member_disabled:'成员已停用，请先启用后再关联',provider_already_linked:'该成员已有关联的同渠道账号，请先核对并解除原关联',
 identity_already_linked:'该账号已被关联，请刷新后核对',last_administrator:'请保留至少一位已启用且可登录的管理员',
 tenant_not_allowed:'该账号所属企业与当前配置不一致',
};
export class ApiError extends Error {
 constructor(public status: number,public code='') {
  super(status===401?'登录已失效，请重新登录':Object.hasOwn(businessErrors,code)?businessErrors[code]:status===403?'暂无管理权限':'暂时无法连接服务，请稍后重试');
 }
}
async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
 const response = await fetch('/api' + path, { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(15000), ...init });
 if (!response.ok) {
  const body=await response.json().catch(()=>({}));
  throw new ApiError(response.status,typeof body?.code==='string'?body.code:'');
 }
 return response.status === 204 ? undefined as T : response.json();
}
export const getProviders = () => request<Provider[]>('/auth/providers');
export async function getMember(): Promise<Member | null> {
 try { return await request<Member>('/me'); }
 catch(error) { if(error instanceof ApiError && error.status === 401) return null; throw error; }
}
export const getApplications = () => request<Application[]>('/applications');
async function mutate<T>(path:string,method:string,body?:unknown):Promise<T> {
 const csrf=await request<{token:string;headerName:string}>('/auth/csrf');
 return request<T>(path,{method,headers:{[csrf.headerName]:csrf.token,...(body===undefined?{}:{'Content-Type':'application/json'})},body:body===undefined?undefined:JSON.stringify(body)});
}
export const listMembers=(q='',page=1,enabledOnly=false)=>request<PageResult<ManagedMember>>('/admin/members?'+new URLSearchParams({q,page:String(page),enabledOnly:String(enabledOnly)}));
export const createMember=(input:MemberInput)=>mutate<ManagedMember>('/admin/members','POST',input);
export const updateMember=(id:string,input:MemberInput)=>mutate<ManagedMember>('/admin/members/'+encodeURIComponent(id),'PATCH',input);
export const listPending=(page=1)=>request<PageResult<PendingIdentity>>('/admin/pending-identities?page='+page);
export const bindIdentity=(pendingId:string,memberId:string)=>mutate<void>('/admin/pending-identities/'+encodeURIComponent(pendingId)+'/bind','POST',{memberId});
export const unlinkIdentity=(memberId:string,identityId:string)=>mutate<void>('/admin/members/'+encodeURIComponent(memberId)+'/identities/'+encodeURIComponent(identityId),'DELETE');
export const listAudit=(page=1)=>request<PageResult<AuditEntry>>('/admin/audit?page='+page);
export async function logout() {
 const csrf = await request<{token:string;headerName:string}>('/auth/csrf');
 await request<void>('/auth/logout', { method: 'POST', headers: { [csrf.headerName]: csrf.token } });
}
