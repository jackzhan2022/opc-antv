import { useState } from 'react';
import { Alert, Button, Input, Modal, Select, Table, Tag } from 'antd';
import { ApiError, listApplicationMembers, setApplicationAccess, type ApplicationMember, type ManagedApplication } from '@/services/api';
import { ErrorNotice, failureMessage, usePagedData } from '../members/shared';

export function ApplicationGrants({applications,selectedId,onSelect,onChanged}:{applications:ManagedApplication[];selectedId:string;onSelect:(id:string)=>void;onChanged:()=>void}) {
 const [query,setQuery]=useState(''),[filter,setFilter]=useState('all'),[page,setPage]=useState(1);
 const app=applications.find(item=>item.id===selectedId);
 const {data,loading,error,reload}=usePagedData(()=>app?listApplicationMembers(app.id,query,filter,page):Promise.resolve({items:[],total:0,page:1,pageSize:20}),[selectedId,query,filter,page,!!app]);
 const [target,setTarget]=useState<ApplicationMember|null>(null),[saving,setSaving]=useState(false),[changeError,setChangeError]=useState('');
 const [conflict,setConflict]=useState(false);
 async function confirm() {
  if(!app || !target)return;setSaving(true);setChangeError('');
  try {await setApplicationAccess(app.id,target.id,!target.granted,target.version);setTarget(null);reload();onChanged();}
  catch(error) {setChangeError(failureMessage(error));setConflict(error instanceof ApiError&&error.code==='application_conflict');reload();} finally {setSaving(false);}
 }
 return <section aria-label="应用授权管理">
  <div className="application-grant-toolbar">
   <label className="application-picker">选择应用<Select aria-label="选择授权应用" value={app?.id} placeholder="请选择应用" options={applications.map(item=>({value:item.id,label:item.name+(item.enabled?'':'（已停用）')}))} onChange={id=>{onSelect(id);setPage(1);}}/></label>
   <Input.Search aria-label="搜索授权成员" placeholder="搜索成员姓名" allowClear onSearch={value=>{setQuery(value);setPage(1);}}/>
   <Select aria-label="授权状态筛选" value={filter} options={[{value:'all',label:'全部成员'},{value:'granted',label:'已授权'},{value:'ungranted',label:'未授权'}]} onChange={value=>{setFilter(value);setPage(1);}}/>
  </div>
  {app && <p className="application-help">{app.integrationType==='SSO'?'单点登录应用：撤销后，成员将失去应用访问权限；重新授权后需重新登录。':'链接应用：授权仅控制工作台入口，直接访问目标系统仍由目标系统管理权限。'}{!app.enabled?' 应用已停用，现有授权暂不生效。':''}</p>}
  <ErrorNotice message={error} retry={reload}/>
  <Table<ApplicationMember> rowKey="id" loading={loading} dataSource={error?[]:data.items} scroll={{x:650}} locale={{emptyText:error?'授权列表加载失败':app?'暂无匹配成员':'请先登记应用'}}
   pagination={{current:page,pageSize:20,total:data.total,showSizeChanger:false,onChange:setPage,showTotal:total=>'共 '+total+' 位成员'}} columns={[
    {title:'成员',dataIndex:'displayName',width:180,render:name=><strong className="table-name">{name}</strong>},
    {title:'平台角色',dataIndex:'role',width:140,render:role=>role==='ADMIN'?<Tag color="blue">管理员</Tag>:'普通成员'},
    {title:'成员状态',dataIndex:'enabled',width:110,render:enabled=>enabled?'启用':'停用'},
    {title:'应用授权',dataIndex:'granted',width:110,render:granted=><Tag color={granted?'green':'default'}>{granted?'已授权':'未授权'}</Tag>},
    {title:'操作',width:110,render:(_,member)=><Button type="link" danger={member.granted} disabled={!member.enabled&&!member.granted} onClick={()=>{setTarget(member);setChangeError('');setConflict(false);}}>{member.granted?'撤销授权':'分配授权'}</Button>},
   ]}/>
  {target && <Modal open title={target.granted?'撤销应用授权':'分配应用授权'} okText={target.granted?'确认撤销':'确认授权'} cancelText={conflict?'关闭并刷新':'取消'} okButtonProps={{danger:target.granted,disabled:conflict}} confirmLoading={saving} onOk={confirm} onCancel={()=>{setTarget(null);if(conflict)reload();}} closable={!saving} maskClosable={!saving} keyboard={!saving} cancelButtonProps={{disabled:saving}}>
   {changeError && <Alert className="form-error" role="alert" type="error" showIcon message={changeError}/>}
   <p>成员：{target.displayName}</p><p>应用：{app?.name}</p>
   <p className="application-help">{target.granted?'撤销后该成员将失去应用访问权限。':'确认向此成员分配应用访问权限。'}{app?.integrationType==='LINK'?' 此处仅控制工作台入口。':''}</p>
  </Modal>}
 </section>;
}
