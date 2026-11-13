import { useState } from 'react';
import { Alert, Form, Input, Modal, Select, Switch } from 'antd';
import { createMember, updateMember, type ManagedMember, type MemberInput } from '@/services/api';
import { failureMessage } from './shared';

export function MemberEditor({member,onClose,onSaved}:{member:ManagedMember|null;onClose:()=>void;onSaved:()=>void}) {
 const [form]=Form.useForm<MemberInput>();
 const [saving,setSaving]=useState(false);const [error,setError]=useState('');
 async function save() {
  let input:MemberInput;
  try {input=await form.validateFields();} catch {return;}
  setSaving(true);setError('');
  try {if(member)await updateMember(member.id,input);else await createMember(input);onSaved();}
  catch(error) {setError(failureMessage(error));} finally {setSaving(false);}
 }
 return <Modal open title={member?'编辑成员':'新增成员'} okText="保存" cancelText="取消" onOk={save} onCancel={onClose} confirmLoading={saving} cancelButtonProps={{disabled:saving}} closable={!saving} maskClosable={!saving} keyboard={!saving}>
  {error && <Alert role="alert" type="error" showIcon message={error} className="form-error"/>}
  <Form form={form} layout="vertical" initialValues={member||{displayName:'',role:'MEMBER',enabled:true}} requiredMark={false}>
   <Form.Item label="姓名" name="displayName" rules={[{required:true,whitespace:true,message:'请输入姓名'},{max:200,message:'姓名最多200个字符'}]}><Input maxLength={200} autoComplete="off"/></Form.Item>
   <Form.Item label="平台角色" name="role" rules={[{required:true,message:'请选择角色'}]}><Select options={[{value:'MEMBER',label:'普通成员'},{value:'ADMIN',label:'管理员'}]}/></Form.Item>
   <Form.Item label="启用状态" name="enabled" valuePropName="checked"><Switch checkedChildren="启用" unCheckedChildren="停用"/></Form.Item>
  </Form>
 </Modal>;
}
