import { useState } from 'react';
import { Button, Input, Space, Table, Tag } from 'antd';
import { listMembers, type ManagedMember } from '@/services/api';
import { ErrorNotice, formatTime, providerName, usePagedData } from './shared';
import { MemberEditor } from './MemberEditor';
import { IdentityDialog } from './IdentityDialog';

export function MembersPanel() {
 const [query,setQuery]=useState('');const [page,setPage]=useState(1);
 const {data,loading,error,reload}=usePagedData(()=>listMembers(query,page),[query,page]);
 const [editor,setEditor]=useState<{member:ManagedMember|null}|null>(null);
 const [identityMember,setIdentityMember]=useState<ManagedMember|null>(null);
 return <section aria-label="平台成员">
  <div className="management-toolbar"><Input.Search aria-label="搜索成员姓名" placeholder="搜索成员姓名" allowClear onSearch={value=>{setQuery(value);setPage(1);}}/><Button type="primary" onClick={()=>setEditor({member:null})}>新增成员</Button></div>
  <ErrorNotice message={error} retry={reload}/>
  <Table<ManagedMember> rowKey="id" loading={loading} dataSource={error?[]:data.items} scroll={{x:850}} locale={{emptyText:error?'成员加载失败':'暂无成员'}}
   pagination={{current:page,pageSize:20,total:data.total,showSizeChanger:false,onChange:setPage,showTotal:total=>'共 '+total+' 位成员'}}
   columns={[
    {title:'姓名',dataIndex:'displayName',width:160,render:name=><strong className="table-name">{name}</strong>},
    {title:'平台角色',dataIndex:'role',width:120,render:role=>role==='ADMIN'?<Tag color="blue">管理员</Tag>:<span>普通成员</span>},
    {title:'状态',dataIndex:'enabled',width:90,render:enabled=><span className={enabled?'status-enabled':'status-disabled'}><i aria-hidden="true"/>{enabled?'启用':'停用'}</span>},
    {title:'登录来源',dataIndex:'identities',width:170,render:identities=>identities.length?identities.map((i:ManagedMember['identities'][number])=><Tag key={i.id}>{providerName(i.provider)}</Tag>):<span className="muted">未关联</span>},
    {title:'最近登录',dataIndex:'lastLoginAt',width:170,render:formatTime},
    {title:'操作',key:'actions',width:190,render:(_,member)=><Space size={4}><Button type="link" onClick={()=>setEditor({member})}>编辑</Button><Button type="link" onClick={()=>setIdentityMember(member)}>登录账号</Button></Space>},
   ]}/>
  {editor && <MemberEditor member={editor.member} onClose={()=>setEditor(null)} onSaved={()=>{setEditor(null);reload();}}/>}
  {identityMember && <IdentityDialog member={identityMember} onClose={()=>setIdentityMember(null)} onChanged={reload}/>}
 </section>;
}
