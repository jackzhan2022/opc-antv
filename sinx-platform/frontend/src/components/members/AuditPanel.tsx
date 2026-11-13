import { useState } from 'react';
import { Button, Table } from 'antd';
import { listAudit, type AuditEntry } from '@/services/api';
import { ErrorNotice, formatTime, usePagedData } from './shared';

const actions:Record<string,string>={MEMBER_CREATED:'新增成员',MEMBER_UPDATED:'编辑成员',IDENTITY_LINKED:'关联账号',IDENTITY_UNLINKED:'解除关联',ADMIN_BOOTSTRAPPED:'初始化管理员'};
export function AuditPanel() {
 const [page,setPage]=useState(1);
 const {data,loading,error,reload}=usePagedData(()=>listAudit(page),[page]);
 return <section aria-label="操作记录"><div className="management-toolbar"><p className="muted">成员与账号的管理记录。</p><Button onClick={reload} loading={loading}>刷新</Button></div>
  <ErrorNotice message={error} retry={reload}/>
  <Table<AuditEntry> rowKey="id" loading={loading} dataSource={error?[]:data.items} scroll={{x:900}} locale={{emptyText:error?'记录加载失败':'暂无操作记录'}}
   pagination={{current:page,pageSize:20,total:data.total,showSizeChanger:false,onChange:setPage,showTotal:total=>'共 '+total+' 条记录'}}
   columns={[
    {title:'时间',dataIndex:'createdAt',width:165,render:formatTime},
    {title:'操作人',dataIndex:'actorName',width:120},
    {title:'操作',dataIndex:'action',width:130,render:action=>Object.hasOwn(actions,action)?actions[action]:action},
    {title:'成员',dataIndex:'targetName',width:120},
    {title:'详情',dataIndex:'detail',width:365,render:detail=><span className="audit-detail">{detail}</span>},
   ]}/>
 </section>;
}
