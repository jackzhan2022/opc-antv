import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Skeleton } from 'antd';
import { history } from 'umi';
import { PlatformHeader } from '@/components/PlatformHeader';
import { ApiError, getMember, getApplications, type Application, type Member } from '@/services/api';
import './Workspace.less';

function ApplicationIcon({ kind }: { kind: string }) {
 return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
  {kind === 'users'
   ? <><circle cx="9" cy="8" r="3"/><path d="M3.5 20v-2a5.5 5.5 0 0 1 11 0v2M16 5.2a3 3 0 0 1 0 5.6M20.5 20v-2a5.5 5.5 0 0 0-3.5-5.1"/></>
   : kind === 'document' ? <path d="M14 3H5v18h14V8Zm0 0v5h5M8 12h8M8 16h6"/>
   : kind === 'video' ? <><rect x="3" y="5" width="18" height="14" rx="2"/><path d="m10 9 5 3-5 3Z"/></>
   : kind === 'chart' ? <path d="M4 3v18h17M9 16v-5M14 16V7M19 16v-3"/>
   : <><rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/></>}
 </svg>;
}

export default function Workspace() {
 const [member,setMember]=useState<Member|null>(null);
 const [apps,setApps]=useState<Application[]>([]);
 const [loading,setLoading]=useState(true);
 const [error,setError]=useState('');
 const [attempt,setAttempt]=useState(0);
 useEffect(()=>{
  let active=true;setLoading(true);setError('');
  (async()=>{
   const user=await getMember();if(!active)return;
   if(!user){history.replace('/login');return;}
   setMember(user);const items=await getApplications();if(active)setApps(items);
  })().catch(error=>{
   if(!active)return;if(error instanceof ApiError && error.status===401){history.replace('/login');return;}
   setError('暂时无法加载工作台，请重试');
  }).finally(()=>{if(active)setLoading(false);});
  return ()=>{active=false;};
 },[attempt]);
 return <div className="workspace-page workspace-portal">
  <PlatformHeader member={member} active="workspace" onError={setError}/>
  <main className="workspace-main"><div className="workspace-title"><h1>工作台</h1><p>选择应用，开始今天的工作。</p></div>
   {error && <div className="workspace-error"><Alert role="alert" type="error" showIcon message={error}/><Button onClick={()=>setAttempt(n=>n+1)}>重试</Button></div>}
   {loading ? <div className="workspace-loading" role="status" aria-label="正在加载应用"><Skeleton active title={{width:'38%'}} paragraph={{rows:2}}/></div> : !error && <section aria-label="我的应用" className="application-area">{apps.length===0 ? <div className="workspace-empty"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<><h2>暂无可用应用</h2><p>应用开通后会显示在这里</p></>}/></div> : <div className="app-grid">{apps.map(app=><a className="app-card" href={app.url} key={app.id} aria-label={`进入${app.name}`}>
    <div className="app-card-heading"><span className="app-icon"><ApplicationIcon kind={app.icon || (app.id==='creator-platform'?'users':'grid')}/></span><div className="app-card-content"><h2>{app.name}</h2><p>{app.description}</p></div></div>
    <div className="app-card-footer"><span className="app-category">内部应用</span><span className="app-enter">进入应用<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d="M5 12h14m-6-6 6 6-6 6"/></svg></span></div>
   </a>)}</div>}</section>}
  </main><footer className="workspace-footer">SinX Platform</footer>
 </div>;
}
