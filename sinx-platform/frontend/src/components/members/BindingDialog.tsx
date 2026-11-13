import { useEffect, useState } from 'react';
import { Descriptions, Modal, Select, Tag } from 'antd';
import { bindIdentity, listMembers, type PendingIdentity } from '@/services/api';
import { ErrorNotice, failureMessage, providerName, usePagedData } from './shared';

export function BindingDialog({identity,onClose,onBound}:{identity:PendingIdentity;onClose:()=>void;onBound:()=>void}) {
 const [search,setSearch]=useState('');const [query,setQuery]=useState('');
 const [selected,setSelected]=useState<string>();
 const [saving,setSaving]=useState(false);const [error,setError]=useState('');
 useEffect(()=>{const timer=setTimeout(()=>setQuery(search),250);return ()=>clearTimeout(timer);},[search]);
 const {data,loading,error:searchError,reload}=usePagedData(()=>listMembers(query,1,true),[query]);
 async function bind() {
  if(!selected){setError('请选择要关联的平台成员');return;}
  setSaving(true);setError('');
  try {await bindIdentity(identity.id,selected);onBound();}
  catch(error) {setError(failureMessage(error));} finally {setSaving(false);}
 }
 return <Modal open title="关联平台成员" okText="确认关联" cancelText="取消" onOk={bind} onCancel={onClose} confirmLoading={saving} cancelButtonProps={{disabled:saving}} okButtonProps={{disabled:loading||!!searchError}} closable={!saving} maskClosable={!saving} keyboard={!saving}>
  <div className="binding-source"><Tag>{providerName(identity.provider)}</Tag><Descriptions size="small" column={1}>
   <Descriptions.Item label="账号姓名">{identity.displayName}</Descriptions.Item>
   <Descriptions.Item label="账号标识"><span className="identity-subject">{identity.subjectId}</span></Descriptions.Item>
  </Descriptions></div>
  <ErrorNotice message={error}/><ErrorNotice message={searchError} retry={reload}/>
  <label className="binding-label" htmlFor="binding-member">平台成员</label>
  <Select id="binding-member" showSearch virtual={false} filterOption={false} placeholder="搜索并选择已启用的成员" loading={loading} value={selected} onChange={setSelected} onSearch={setSearch} disabled={saving}
   options={data.items.map(member=>({value:member.id,label:member.displayName+' / '+(member.role==='ADMIN'?'管理员':'普通成员')+' / '+member.id.slice(-8)}))}
   notFoundContent={loading?'正在搜索…':searchError?'加载失败':'没有符合条件的成员'} className="binding-select"/>
  <p className="binding-note">核对后关联。该账号将使用所选成员的平台权限。</p>
 </Modal>;
}
