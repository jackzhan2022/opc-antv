import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Spin } from 'antd';
import { history } from 'umi';
import { Brand } from '@/components/Brand';
import { getMember, getProviders, type Provider } from '@/services/api';
const errors: Record<string,string> = {
 invalid_state: '登录已过期，请重新发起登录',
 authorization_denied: '授权未完成，请重新登录',
 tenant_not_allowed: '请使用本公司账号登录',
 member_disabled: '账号已停用，请联系平台管理员',
 identity_unlinked: '账号尚未关联，请联系管理员',
 provider_unavailable: '该登录方式暂未开通',
 provider_error: '企业账号验证失败，请重新登录',
 identity_invalid: '无法验证内部成员身份，请联系平台管理员',
 login_failed: '登录失败，请稍后重试',
};
function ProviderIcon({id}:{id:string}) {
 return <svg width="24" height="24" viewBox="0 0 24 24" aria-hidden="true" fill="none">
  {id==='feishu' ? <><path d="m3 7 8 4 9-7-6 11-4 5-2-8-5-5Z" fill="#3370FF"/><path d="m3 7 8 4 3 4-6-3-5-5Z" fill="#45C3EE"/></> : <><path d="M3 11c0-4 3.8-7 8.4-7 4.7 0 8.6 3 8.6 7s-3.9 7-8.6 7H9l-4 3 .4-5C3.9 14.7 3 13 3 11Z" fill="#2788E8"/><path d="m8 11 3 3 6-6" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></>}
 </svg>;
}
export default function Login() {
 const [providers,setProviders]=useState<Provider[]>([]);
 const [loading,setLoading]=useState(true);
 const [failed,setFailed]=useState(false);
 const [attempt,setAttempt]=useState(0);
 const [starting,setStarting]=useState<string>();
 const autoStarted=useRef(false);
 const params=new URLSearchParams(window.location.search);
 const code=params.get('error');
 const skipAutoLogin=params.has('error') || params.get('logged_out')==='1';
 useEffect(()=>{
  let active=true;setLoading(true);setFailed(false);
  Promise.all([getProviders(),getMember()]).then(([items,member])=>{
   if(!active)return;if(member){history.replace('/workspace');return;}setProviders(items);
   // Client detection selects an authorization entry only; all access checks remain on the backend.
   if(!skipAutoLogin && !autoStarted.current && /\bwxwork\b/i.test(navigator.userAgent)
    && items.some(provider=>provider.id==='wecom' && provider.enabled)) {
    autoStarted.current=true;setStarting('wecom');window.location.replace('/api/auth/wecom/start');
   }
  }).catch(()=>{if(active)setFailed(true);}).finally(()=>{if(active)setLoading(false);});
  return ()=>{active=false;};
 },[attempt,skipAutoLogin]);
 return <main className="login-page">
  <section className="login-art" aria-label="信计 X 平台">
   <Brand/>
   <div className="login-copy"><span className="eyebrow">SINX PLATFORM</span><h1>让工作，<br/>从这里开始。</h1><p>统一登录，便捷访问内部应用。</p>
    <div className="card-stack" aria-hidden="true">
     <div className="visual-card card-one"><span className="visual-icon">X</span><div><strong>信计 X 平台</strong><small>统一身份 · 应用入口</small></div><span className="visual-tag">统一登录</span><div className="visual-lines"><i/><i/></div></div>
     <div className="visual-card card-two"><span className="visual-icon sage"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><circle cx="9" cy="8" r="3"/><path d="M3 20v-3a6 6 0 0 1 12 0v3m1-15a3 3 0 0 1 0 6m2 3a5 5 0 0 1 3 4v2"/></svg></span><div><strong>创作者管理</strong><small>创作者档案 · 资料管理</small></div><span className="visual-tag sage">内部应用</span><div className="visual-lines"><i/><i/></div></div>
    </div>
   </div>
   <footer>SinX Platform · 公司内部工作平台</footer>
  </section>
  <section className="login-content">
   <div className="mobile-brand"><Brand/></div>
   <div className="login-box"><span className="eyebrow">欢迎回来</span><h2>登录信计 X 平台</h2><p className="login-subtitle">使用公司账号继续</p>
    {code && <Alert role="alert" type="error" showIcon message={Object.hasOwn(errors,code) ? errors[code] : errors.login_failed} className="login-alert"/>}
    {loading ? <div className="loading-block" role="status" aria-label="正在加载登录方式"><Spin/></div> : failed ? <div className="login-retry"><Alert role="alert" type="warning" message="暂时无法连接服务"/><Button onClick={()=>setAttempt(n=>n+1)}>重试</Button></div> : <div className="provider-list">{providers.map(provider=><div key={provider.id} className="provider-row"><Button block size="large" className="provider-button" disabled={!provider.enabled || !!starting} loading={starting===provider.id} onClick={()=>{setStarting(provider.id);window.location.assign('/api/auth/'+provider.id+'/start');}}><ProviderIcon id={provider.id}/><span>使用{provider.name}登录</span></Button>{!provider.enabled && <span className="provider-status">暂未开通</span>}</div>)}</div>}
    <p className="login-note">仅供公司内部成员使用</p>
   </div>
   <footer className="login-footer">信计 X 平台</footer>
  </section>
 </main>;
}
