import { useState } from 'react';
import { Button, Table } from 'antd';
import { listApplicationAudit, type ApplicationAudit as Audit } from '@/services/api';
import { ErrorNotice, formatTime, usePagedData } from '../members/shared';

const labels:Record<string,string>={APPLICATION_CREATED:'登记应用',APPLICATION_UPDATED:'编辑应用',ACCESS_GRANTED:'分配授权',ACCESS_REVOKED:'撤销授权',ACCESS_MIGRATED:'迁移既有授权'};
export function ApplicationAudit() {
 const [page,setPage]=useState(1);
 const {data,loading,error,reload}=usePagedData(()=>listApplicationAudit(page),[page]);
 return <section aria-label="应用操作记录"><div className="management-toolbar"><p className="muted">应用配置与成员授权的变更记录。</p><Button onClick={reload} loading={loading}>刷新</Button></div><ErrorNotice message={error} retry={reload}/>
  <Table<Audit> rowKey="id" loading={loading} dataSource={error?[]:data.items} scroll={{x:950}} locale={{emptyText:error?'记录加载失败':'暂无操作记录'}} pagination={{current:page,pageSize:20,total:data.total,showSizeChanger:false,onChange:setPage,showTotal:total=>'共 '+total+' 条记录'}} columns={[
   {title:'时间',dataIndex:'createdAt',width:165,render:formatTime},
   {title:'操作人',dataIndex:'actorName',width:120},
   {title:'操作',dataIndex:'action',width:130,render:action=>Object.hasOwn(labels,action)?labels[action]:action},
   {title:'应用',dataIndex:'applicationName',width:150},
   {title:'成员',dataIndex:'memberName',width:120,render:value=>value||'—'},
   {title:'详情',dataIndex:'detail',width:265,render:value=><span className="audit-detail">{value}</span>},
  ]}/>
 </section>;
}
