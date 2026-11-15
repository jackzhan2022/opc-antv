import { useEffect, useState } from "react";
import { Alert, Button, Empty, Form, Input, Modal, Result, Space, Table, Tag } from "antd";
import { Link } from "umi";
import { api, dateTime } from "../api";
import { ErrorNotice, ErrorState, PageHeading, useMember } from "../components";
import { ContractTemplate, downloadContractFile, externalUrl, Integration, integration } from "../contract-api";
import "../contracts.css";

export default function ContractTemplates() {
  const member = useMember(), admin = member?.role === "ADMIN";
  const [rows, setRows] = useState<ContractTemplate[]>([]), [loading, setLoading] = useState(true), [error, setError] = useState<unknown>(), [loadError, setLoadError] = useState<unknown>(), [refresh, setRefresh] = useState(0);
  const [config, setConfig] = useState<Integration>(), [importOpen, setImportOpen] = useState(false), [file, setFile] = useState<File>(), [busy, setBusy] = useState(false), [binding, setBinding] = useState<ContractTemplate>(), [editor, setEditor] = useState<ContractTemplate>(), [url, setUrl] = useState(""), [converting, setConverting] = useState(false);
  const [importForm] = Form.useForm(), [bindForm] = Form.useForm(), [modal, context] = Modal.useModal();
  useEffect(() => {
    let active = true; setLoading(true); setLoadError(undefined);
    Promise.all([api<ContractTemplate[]>("/contract-templates"), integration()]).then(([items, status]) => { if (active) { setRows(items); setConfig(status); } }).catch(e => { if (active) setLoadError(e); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [refresh]);
  async function importFile(values: { name: string }) {
    if (!file) return; setBusy(true); setError(undefined);
    try { const body = new FormData(); body.set("name", values.name); body.set("file", file); await api("/contract-templates", "POST", body); setImportOpen(false); setFile(undefined); importForm.resetFields(); setRefresh(x => x + 1); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  async function openEditor(row: ContractTemplate) {
    setBusy(true); setError(undefined); setEditor(row); setUrl("");
    try { const result = await api<{ state: string; url?: string }>(`/contract-templates/${row.id}/editor`, "POST"); setConverting(result.state === "CONVERTING"); if (result.url) setUrl(externalUrl(result.url)); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  async function bind(values: { remoteId: string }) {
    if (!binding) return; setBusy(true); setError(undefined);
    try { await api(`/contract-templates/${binding.id}/bind`, "POST", { ...values, version: binding.version }); setBinding(undefined); setRefresh(x => x + 1); }
    catch (e) { setError(e); } finally { setBusy(false); }
  }
  async function disable(row: ContractTemplate) {
    try { await api(`/contract-templates/${row.id}/disable`, "POST", { version: row.version }); setRefresh(x => x + 1); }
    catch (e) { setError(e); }
  }
  if (loadError) return <ErrorState error={loadError} retry={() => setRefresh(x => x + 1)}/>;
  return <>{context}<PageHeading title="合同模板" description="导入原文件，配置填写字段和签署位置后重复使用" actions={admin && <Button type="primary" onClick={() => { setError(undefined); setFile(undefined); importForm.resetFields(); setImportOpen(true); }}>导入模板</Button>}/>
    {!config?.ready && !loading && <Alert className="contract-notice" type="info" showIcon message="电子签尚未配置" description="可以先导入并保留模板原文件。配置企业接入参数后，即可编辑模板并发起真实签署。"/>}
    {config?.ready && config.environment === "test" && <Alert className="contract-notice" type="warning" showIcon message="当前为联调环境，模板与生产环境分别配置"/>}
    {!!error && !importOpen && !binding && !editor && <ErrorNotice error={error}/>}
    <section className="panel contract-panel"><Table<ContractTemplate> rowKey="id" loading={loading} dataSource={rows} scroll={{ x: 800 }} pagination={{ pageSize: 20, showSizeChanger: false }} locale={{ emptyText: <Empty description="暂无合同模板" image={Empty.PRESENTED_IMAGE_SIMPLE}/> }} columns={[
      { title: "模板名称", dataIndex: "name", render: (v, row) => <div><strong>{v}</strong><div className="muted">{row.filename}</div></div> },
      { title: "格式", dataIndex: "fileType", width: 80, render: v => v.toUpperCase() },
      { title: "状态", width: 125, render: (_, row) => <Tag color={row.enabled ? "green" : undefined}>{row.enabled ? "可用于签约" : row.remoteId ? "已停用" : "待配置"}</Tag> },
      { title: "导入时间", dataIndex: "createdAt", width: 170, render: dateTime },
      { title: "操作", width: 310, render: (_, row) => <Space wrap><Button size="small" onClick={() => downloadContractFile(`/contract-templates/${row.id}/source`, row.filename).catch(setError)}>原文件</Button>{admin && <><Button size="small" disabled={!config?.ready} onClick={() => openEditor(row)}>配置模板</Button><Button size="small" disabled={!config?.ready} onClick={() => { setError(undefined); setBinding(row); bindForm.setFieldsValue({ remoteId: row.remoteId }); }}>关联模板</Button>{row.enabled && <Button size="small" onClick={() => modal.confirm({ title: "停用此模板？", content: "已生成的合同和签署记录继续保留。", okText: "停用", cancelText: "取消", onOk: () => disable(row) })}>停用</Button>}</>}</Space> }
    ]}/></section>
    <div className="contract-help"><strong>模板导入流程</strong><p>1. 导入 Word 或 PDF 原文件；2. 在电子签编辑页配置公司和个人两个签署方、填写字段与签章位置；3. 发布后关联模板编号。</p><p>Word 转换后的分页和字体需要在配置页核对。扫描 PDF 也需要手动配置字段和位置。</p></div>
    <Modal title="导入合同模板" open={importOpen} onCancel={() => { if (!busy) setImportOpen(false); }} footer={null} destroyOnHidden>
      {!!error && <ErrorNotice error={error}/>}<Form form={importForm} layout="vertical" onFinish={importFile}><Form.Item name="name" label="模板名称" rules={[{ required: true, whitespace: true, message: "请填写模板名称" }]}><Input maxLength={100}/></Form.Item>
        <div className="contract-file"><label htmlFor="template-file">模板原文件</label><input id="template-file" type="file" accept=".doc,.docx,.pdf" required onChange={e => setFile(e.target.files?.[0])}/><p className="muted">支持 .doc、.docx、.pdf，单个文件不超过 5MB。</p></div>
        <Button type="primary" htmlType="submit" loading={busy} disabled={!file}>导入并保存原文件</Button></Form>
    </Modal>
    <Modal title="关联已发布模板" open={!!binding} onCancel={() => { if (!busy) setBinding(undefined); }} footer={null} destroyOnHidden>
      <p>请在电子签模板配置中，依次设置一位公司签署方和一位个人创作者，并发布为普通手动签署模板。粘贴模板编号后会校验可用状态并同步填写字段。</p>
      {!!error && <ErrorNotice error={error}/>}<Form form={bindForm} layout="vertical" onFinish={bind}><Form.Item name="remoteId" label="电子签模板编号" rules={[{ required: true, whitespace: true, message: "请填写模板编号" }]}><Input maxLength={100}/></Form.Item><Button type="primary" htmlType="submit" loading={busy}>校验并启用模板</Button></Form>
    </Modal>
    <Modal className="contract-editor-modal" title={editor ? `配置模板 · ${editor.name}` : "配置模板"} width={1100} open={!!editor} onCancel={() => { setEditor(undefined); setUrl(""); setConverting(false); setError(undefined); }} footer={<Button onClick={() => { setEditor(undefined); setUrl(""); }}>完成配置后关联模板编号</Button>} destroyOnHidden>
      {!!error && <ErrorNotice error={error}/>} {converting && <Alert type="info" showIcon message="Word 正在转换为 PDF" description="转换完成后，请核对分页、字体和签署位置。"/>}
      {!url && <Button loading={busy} disabled={!editor} onClick={() => editor && openEditor(editor)}>{converting ? "检查转换结果" : "重新获取配置页"}</Button>}
      {url && <iframe title="腾讯电子签模板配置" src={url} referrerPolicy="no-referrer" className="contract-iframe"/>}
    </Modal>
  </>;
}
