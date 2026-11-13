import { useState } from 'react';
import { Avatar, Button } from 'antd';
import { Link, history } from 'umi';
import { Brand } from './Brand';
import { ApiError, logout, type Member } from '@/services/api';

export function PlatformHeader({member,active,onError}:{member:Member|null;active:'workspace'|'members';onError:(message:string)=>void}) {
 const [leaving,setLeaving]=useState(false);
 async function leave() {
  setLeaving(true);
  try {await logout();history.replace('/login?logged_out=1');}
  catch(error) {
   if(error instanceof ApiError && error.status===401) history.replace('/login?logged_out=1');
   else onError('退出失败，请重试');
  } finally {setLeaving(false);}
 }
 return <header className="platform-header"><Brand/>
  <nav aria-label="主导航">
   <Link to="/workspace" aria-current={active==='workspace'?'page':undefined}>工作台</Link>
   {member?.role==='ADMIN' && <Link to="/members" aria-current={active==='members'?'page':undefined}>成员管理</Link>}
  </nav>
  <div className="account">{member && <>
   <Avatar className="member-avatar">{member.displayName.slice(0,1)}</Avatar><span className="member-name">{member.displayName}</span>
   <Button type="text" onClick={leave} loading={leaving}>退出登录</Button>
  </>}</div>
 </header>;
}
