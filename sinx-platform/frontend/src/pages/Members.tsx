import { useEffect, useState } from 'react';
import { Button, Result, Skeleton, Tabs } from 'antd';
import { Link, history } from 'umi';
import { PlatformHeader } from '@/components/PlatformHeader';
import { MembersPanel } from '@/components/members/MembersPanel';
import { PendingPanel } from '@/components/members/PendingPanel';
import { AuditPanel } from '@/components/members/AuditPanel';
import { ErrorNotice, failureMessage } from '@/components/members/shared';
import { getMember, type Member } from '@/services/api';

export default function Members() {
 const [member,setMember]=useState<Member|null>(null);const [loading,setLoading]=useState(true);
 const [error,setError]=useState('');const [attempt,setAttempt]=useState(0);const [tab,setTab]=useState('members');
 useEffect(()=>{
  let active=true;setLoading(true);setError('');
  getMember().then(user=>{if(!active)return;if(!user){history.replace('/login');return;}setMember(user);})
   .catch(error=>{if(active)setError(failureMessage(error));}).finally(()=>{if(active)setLoading(false);});
  return ()=>{active=false;};
 },[attempt]);
 return <div className="workspace-page">
  <PlatformHeader member={member} active="members" onError={setError}/>
  <main className="workspace-main management-main">
   {loading?<Skeleton active paragraph={{rows:5}}/>:!member?<ErrorNotice message={error} retry={()=>setAttempt(n=>n+1)}/>:member.role!=='ADMIN'?
    <Result status="403" title="暂无管理权限" subTitle="请联系平台管理员。" extra={<Link to="/workspace"><Button>返回工作台</Button></Link>}/>:
    <><div className="workspace-title"><h1>成员管理</h1><p>管理平台成员与登录账号</p></div><ErrorNotice message={error}/>
     <Tabs activeKey={tab} onChange={setTab} destroyOnHidden items={[
      {key:'members',label:'成员',children:tab==='members'?<MembersPanel/>:null},
      {key:'pending',label:'待关联账号',children:tab==='pending'?<PendingPanel/>:null},
      {key:'audit',label:'操作记录',children:tab==='audit'?<AuditPanel/>:null},
     ]}/>
    </>}
  </main>
  <footer className="workspace-footer">SinX Platform</footer>
 </div>;
}
