import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Skeleton } from 'antd';
import { history } from 'umi';
import { PlatformHeader } from '@/components/PlatformHeader';
import { ApiError, getMember, getApplications, type Application, type Member } from '@/services/api';
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
 return <div className="workspace-page">
  <PlatformHeader member={member} active="workspace" onError={setError}/>
  <main className="workspace-main"><div className="workspace-title"><h1>工作台</h1><p>我的应用</p></div>
   {error && <div className="workspace-error"><Alert role="alert" type="error" showIcon message={error}/><Button onClick={()=>setAttempt(n=>n+1)}>重试</Button></div>}
   {loading ? <Skeleton active paragraph={{rows:3}}/> : !error && <section aria-label="我的应用" className="application-area">{apps.length===0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={<><h2>暂无可用应用</h2><p>应用开通后会显示在这里</p></>}/> : <div className="app-grid">{apps.map(app=><a className="app-card" href={app.url} key={app.id}><strong>{app.name}</strong><p>{app.description}</p></a>)}</div>}</section>}
  </main><footer className="workspace-footer">SinX Platform</footer>
 </div>;
}
