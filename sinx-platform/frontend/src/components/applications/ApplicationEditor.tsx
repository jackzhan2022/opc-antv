import { useState } from 'react';
import { Alert, Form, Input, InputNumber, Modal, Select, Switch } from 'antd';
import { ApiError, createApplication, updateApplication, type ApplicationInput, type ManagedApplication } from '@/services/api';
import { failureMessage } from '../members/shared';

export function ApplicationEditor({application,onClose,onSaved}:{application:ManagedApplication|null;onClose:()=>void;onSaved:()=>void}) {
 const [form]=Form.useForm<ApplicationInput>();
 const [saving,setSaving]=useState(false),[error,setError]=useState('');
 const [conflict,setConflict]=useState(false);
 const managed=application?.integrationType==='SSO';
 async function save() {
  let values:ApplicationInput;try {values=await form.validateFields();} catch {return;}
  const input:ApplicationInput={code:values.code,name:values.name,description:values.description||'',url:values.url,icon:values.icon,sortOrder:values.sortOrder,enabled:values.enabled,...(application?{version:application.version}:{})};
  setSaving(true);setError('');
  try {if(application)await updateApplication(application.id,input);else await createApplication(input);onSaved();}
  catch(error) {setError(failureMessage(error));setConflict(error instanceof ApiError&&error.code==='application_conflict');} finally {setSaving(false);}
 }
 return <Modal open title={application?'编辑应用':'登记应用'} okText="保存" cancelText={conflict?'关闭并刷新':'取消'} okButtonProps={{disabled:conflict}} width={600} onOk={save} onCancel={onClose} confirmLoading={saving} cancelButtonProps={{disabled:saving}} closable={!saving} maskClosable={!saving} keyboard={!saving}>
  {error&&<Alert className="form-error" role="alert" type="error" showIcon message={error}/>}
  <p className="application-help">{managed?'单点登录应用会检查成员的业务访问权限；地址由接入配置管理。':'链接应用仅控制工作台入口，目标系统的访问权限由目标系统管理。'}</p>
  <Form form={form} layout="vertical" requiredMark={false} initialValues={application||{code:'',name:'',description:'',url:'',icon:'grid',sortOrder:0,enabled:true}}>
   <Form.Item label="应用名称" name="name" rules={[{required:true,whitespace:true,message:'请输入应用名称'},{max:100,message:'最多100个字符'}]}><Input maxLength={100}/></Form.Item>
   <div className="application-form-grid">
    <Form.Item label="应用标识" name="code" extra="创建后不可修改" rules={[{required:true,message:'请输入应用标识'},{pattern:/^[a-z][a-z0-9-]{1,63}$/,message:'使用2至64位小写字母、数字或连字符，以字母开头'}]}><Input disabled={!!application} maxLength={64} placeholder="例如 creator-platform"/></Form.Item>
    <Form.Item label="显示顺序" name="sortOrder" rules={[{required:true,message:'请输入显示顺序'},{type:'integer',min:0,max:9999,message:'使用0至9999的整数'}]}><InputNumber min={0} max={9999} precision={0}/></Form.Item>
   </div>
   <Form.Item label="应用地址" name="url" rules={[{required:true,whitespace:true,message:'请输入应用地址'},{max:2048,message:'最多2048个字符'}]}><Input disabled={managed} maxLength={2048} placeholder="https://"/></Form.Item>
   <Form.Item label="应用说明" name="description" rules={[{max:500,message:'最多500个字符'}]}><Input.TextArea rows={3} maxLength={500}/></Form.Item>
   <div className="application-form-grid">
    <Form.Item label="应用图标" name="icon" rules={[{required:true,message:'请选择图标'}]}><Select options={[{value:'grid',label:'应用'},{value:'users',label:'人员'},{value:'document',label:'文档'},{value:'video',label:'视频'},{value:'chart',label:'图表'}]}/></Form.Item>
    <Form.Item label="启用状态" name="enabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用"/></Form.Item>
   </div>
   {managed && <p className="application-help">停用后，成员将无法进入应用；重新启用后需重新登录。</p>}
  </Form>
 </Modal>;
}
