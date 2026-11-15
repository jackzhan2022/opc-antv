import { useEffect, useState } from 'react';
import { Button, Result, Skeleton, Space, Table, Tabs, Tag } from 'antd';
import { Link, history } from 'umi';
import { PlatformHeader } from '@/components/PlatformHeader';
import { ApplicationEditor } from '@/components/applications/ApplicationEditor';
import { ApplicationGrants } from '@/components/applications/ApplicationGrants';
import { ApplicationAudit } from '@/components/applications/ApplicationAudit';
import { ErrorNotice, failureMessage } from '@/components/members/shared';
import { getMember, listManagedApplications, type Member, type ManagedApplication } from '@/services/api';
import './Applications.less';

export default function Applications() {
 const [member,setMember]=useState<Member|null>(null),[applications,setApplications]=useState<ManagedApplication[]>([]);
 const [loading,setLoading]=useState(true),[error,setError]=useState(''),[revision,setRevision]=useState(0);
 const [tab,setTab]=useState('catalog'),[selectedId,setSelectedId]=useState('creator-platform');
 const [editor,setEditor]=useState<{application:ManagedApplication|null}|null>(null);
 const reload=()=>setRevision(n=>n+1);
 useEffect(()=>{
  let active=true;setLoading(true);setError('');
  (async()=>{
   const current=await getMember();if(!active)return;
   if(!current){history.replace('/login');return;}setMember(current);
   if(current.role==='ADMIN') {const items=await listManagedApplications();if(active)setApplications(items);}
  })().catch(error=>{if(active)setError(failureMessage(error));}).finally(()=>{if(active)setLoading(false);});
  return ()=>{active=false;};
 },[revision]);
 return <div className="workspace-page"><PlatformHeader member={member} active="applications" onError={setError}/>
  <main className="workspace-main management-main applications-main">
   {loading&&!member?<Skeleton active paragraph={{rows:5}}/>:!member?<ErrorNotice message={error} retry={reload}/>:member.role!=='ADMIN'?<Result status="403" title="暂无管理权限" subTitle="请联系平台管理员。" extra={<Link to="/workspace"><Button>返回工作台</Button></Link>}/>:<>
    <div className="workspace-title"><h1>应用管理</h1><p>管理应用入口与成员访问权限</p></div><ErrorNotice message={error} retry={reload}/>
    <Tabs activeKey={tab} onChange={setTab} destroyOnHidden items={[
     {key:'catalog',label:'应用目录',children:<section aria-label="应用目录"><div className="management-toolbar"><p className="muted">新登记应用需分配成员后才会出现在其工作台。</p><Button type="primary" onClick={()=>setEditor({application:null})}>登记应用</Button></div>
      <Table<ManagedApplication> rowKey="id" loading={loading} dataSource={error?[]:applications} pagination={false} scroll={{x:1000}} locale={{emptyText:error?'应用加载失败':'暂无应用'}} columns={[
       {title:'应用',width:220,render:(_,app)=><div className="application-identity"><strong>{app.name}</strong><small>{app.code}</small></div>},
       {title:'应用说明',dataIndex:'description',width:240,render:value=>value||'—'},
       {title:'接入方式',width:140,render:(_,app)=><><Tag color={app.integrationType==='SSO'?'blue':'default'}>{app.integrationType==='SSO'?'单点登录':'链接入口'}</Tag>{!app.connected&&<small className="muted">尚未启用接入</small>}</>},
       {title:'状态',dataIndex:'enabled',width:90,render:enabled=><span className={enabled?'status-enabled':'status-disabled'}><i aria-hidden="true"/>{enabled?'启用':'停用'}</span>},
       {title:'授权人数',dataIndex:'grantedMembers',width:95},
       {title:'顺序',dataIndex:'sortOrder',width:70},
       {title:'操作',width:145,render:(_,app)=><Space size={4}><Button type="link" onClick={()=>setEditor({application:app})}>编辑</Button><Button type="link" onClick={()=>{setSelectedId(app.id);setTab('grants');}}>授权</Button></Space>},
      ]}/><p className="application-help">授权人数仅统计启用成员。已接入单点登录应用的地址由系统维护。</p>
     </section>},
     {key:'grants',label:'应用授权',children:tab==='grants'?<ApplicationGrants applications={error?[]:applications} selectedId={selectedId} onSelect={setSelectedId} onChanged={reload}/>:null},
     {key:'audit',label:'操作记录',children:tab==='audit'?<ApplicationAudit/>:null},
    ]}/>
   </>}
  </main><footer className="workspace-footer">SinX Platform</footer>
  {editor && <ApplicationEditor application={editor.application} onClose={()=>{setEditor(null);reload();}} onSaved={()=>{setEditor(null);reload();}}/>}
 </div>;
}
