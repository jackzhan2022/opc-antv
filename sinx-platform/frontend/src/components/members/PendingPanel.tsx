import { useState } from 'react';
import { Button, Table, Tag } from 'antd';
import { listPending, type PendingIdentity } from '@/services/api';
import { BindingDialog } from './BindingDialog';
import { ErrorNotice, formatTime, providerName, usePagedData } from './shared';

export function PendingPanel() {
 const [page,setPage]=useState(1);const [identity,setIdentity]=useState<PendingIdentity|null>(null);
 const {data,loading,error,reload}=usePagedData(()=>listPending(page),[page]);
 return <section aria-label="待关联账号">
  <div className="management-toolbar"><p className="muted">完成企业授权、尚未开通平台访问的账号。</p><Button onClick={reload} loading={loading}>刷新</Button></div>
  <ErrorNotice message={error} retry={reload}/>
  <Table<PendingIdentity> rowKey="id" loading={loading} dataSource={error?[]:data.items} scroll={{x:800}} locale={{emptyText:error?'账号加载失败':'暂无待关联账号'}}
   pagination={{current:page,pageSize:20,total:data.total,showSizeChanger:false,onChange:setPage,showTotal:total=>'共 '+total+' 个账号'}}
   columns={[
    {title:'账号姓名',dataIndex:'displayName',width:150,render:name=><strong className="table-name">{name}</strong>},
    {title:'来源',dataIndex:'provider',width:120,render:provider=><Tag>{providerName(provider)}</Tag>},
    {title:'账号标识',dataIndex:'subjectId',width:200,render:subject=><span className="identity-subject">{subject}</span>},
    {title:'最近授权',dataIndex:'lastSeenAt',width:170,render:formatTime},
    {title:'操作',key:'actions',width:140,render:(_,item)=><Button type="link" onClick={()=>setIdentity(item)}>关联成员</Button>},
   ]}/>
  {identity && <BindingDialog identity={identity} onClose={()=>setIdentity(null)} onBound={()=>{setIdentity(null);if(data.items.length===1 && page>1)setPage(page-1);else reload();}}/>}
 </section>;
}
