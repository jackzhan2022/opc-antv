import { useEffect, useState } from 'react';
import { Alert, Button } from 'antd';
import { history } from 'umi';
import { ApiError, type PageResult } from '@/services/api';

export const providerName=(id:string)=>id==='feishu'?'飞书':'企业微信';
export const formatTime=(value:string|null)=>value?new Intl.DateTimeFormat('zh-CN',{dateStyle:'short',timeStyle:'short',hour12:false}).format(new Date(value)):'—';
export function failureMessage(error:unknown) {
 if(error instanceof ApiError && error.status===401) history.replace('/login');
 return error instanceof ApiError?error.message:'暂时无法连接服务，请稍后重试';
}
export function ErrorNotice({message,retry}:{message:string;retry?:()=>void}) {
 return message?<div className="management-error"><Alert role="alert" type="error" showIcon message={message}/>{retry && <Button onClick={retry}>重试</Button>}</div>:null;
}
export function usePagedData<T>(load:()=>Promise<PageResult<T>>,dependencies:unknown[]) {
 const [data,setData]=useState<PageResult<T>>({items:[],total:0,page:1,pageSize:20});
 const [loading,setLoading]=useState(true);
 const [error,setError]=useState('');
 const [revision,setRevision]=useState(0);
 useEffect(()=>{
  let active=true;setLoading(true);setError('');
  load().then(result=>{if(active)setData(result);}).catch(error=>{if(active)setError(failureMessage(error));}).finally(()=>{if(active)setLoading(false);});
  return ()=>{active=false;};
 },[...dependencies,revision]);
 return {data,loading,error,reload:()=>setRevision(n=>n+1)};
}
