import { useState } from 'react';
import { Button, Empty, Modal, Popconfirm, Tag } from 'antd';
import { unlinkIdentity, type ManagedMember } from '@/services/api';
import { ErrorNotice, failureMessage, providerName } from './shared';

export function IdentityDialog({member,onClose,onChanged}:{member:ManagedMember;onClose:()=>void;onChanged:()=>void}) {
 const [identities,setIdentities]=useState(member.identities);
 const [working,setWorking]=useState('');const [error,setError]=useState('');
 async function unlink(id:string) {
  setWorking(id);setError('');
  try {await unlinkIdentity(member.id,id);setIdentities(items=>items.filter(item=>item.id!==id));onChanged();}
  catch(error) {setError(failureMessage(error));} finally {setWorking('');}
 }
 return <Modal open title={member.displayName+' · 登录账号'} footer={<Button onClick={onClose} disabled={!!working}>关闭</Button>} onCancel={onClose} closable={!working} maskClosable={!working} keyboard={!working}>
  <ErrorNotice message={error}/>
  {identities.length===0?<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未关联登录账号"/>:<div className="identity-list">{identities.map(identity=><div className="identity-item" key={identity.id}>
   <div><Tag>{providerName(identity.provider)}</Tag><strong>{identity.displayName}</strong><p className="identity-subject">{identity.subjectId}</p></div>
   <Popconfirm title="解除这个登录账号？" description="该账号将无法登录，成员的已有会话也会失效。" okText="确认解除" cancelText="取消" onConfirm={()=>unlink(identity.id)} disabled={!!working}>
    <Button type="link" danger loading={working===identity.id} disabled={!!working}>解除关联</Button>
   </Popconfirm>
  </div>)}</div>}
 </Modal>;
}
