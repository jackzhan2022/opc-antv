import { useEffect, useState } from "react";
import { Alert, Button, Checkbox, Descriptions, Empty, Form, Input, Modal, Select, Space, Table, Tag } from "antd";
import { Link } from "umi";
import { api, ApiError, Creator, dateTime, PageData } from "./api";
import { ErrorNotice, ErrorState, paginationItem, useMember } from "./components";
import { contractStates, ContractTemplate, CreatorContract, downloadContractFile, externalUrl, Integration, integration } from "./contract-api";
import "./contracts.css";

const auditNames: Record<string,string> = { DRAFT_CREATED: "创建草稿", DRAFT_UPDATED: "修改草稿", DRAFT_PREVIEWED: "预览草稿", LAUNCH_REQUESTED: "确认发起签署", PDF_ARCHIVED: "归档签署文件", CANCEL_REQUESTED: "管理员撤销" };
function futureDate() { const d = new Date(Date.now() + 30 * 86400000); return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,"0")}-${String(d.getDate()).padStart(2,"0")}`; }
export default function ContractRecords({ creator }: { creator?: Creator }) {
  const admin = useMember()?.role === "ADMIN";
  const [data, setData] = useState<PageData<CreatorContract>>(), [templates, setTemplates] = useState<ContractTemplate[]>([]), [config, setConfig] = useState<Integration>();
  const [loading, setLoading] = useState(true), [loadError, setLoadError] = useState<unknown>(), [error, setError] = useState<unknown>(), [page, setPage] = useState(1), [revision, setRevision] = useState(0), [busy, setBusy] = useState(false);
  const [creating, setCreating] = useState(false), [detail, setDetail] = useState<CreatorContract>(), [audits, setAudits] = useState<{ id: string; actorName: string; action: string; createdAt: string }[]>([]), [link, setLink] = useState(""), [previewUrl, setPreviewUrl] = useState("");
  const [editing, setEditing] = useState<CreatorContract>();
  const [form] = Form.useForm(), [modal, context] = Modal.useModal();
  const templateId = Form.useWatch("templateId", form), selected = templates.find(x => x.id === templateId);
  useEffect(() => {
    let active = true; setLoading(true); setLoadError(undefined);
    const params = new URLSearchParams({ page: String(page), pageSize: "20", ...(creator ? { creatorId: creator.id } : {}) });
    Promise.all([api<PageData<CreatorContract>>(`/contracts?${params}`), api<ContractTemplate[]>("/contract-templates"), integration()])
      .then(([items, definitions, status]) => { if (active) { setData(items); setTemplates(definitions); setConfig(status); } }).catch(e => { if (active) setLoadError(e); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [creator?.id, page, revision]);
  useEffect(() => {
    if (!data?.items.some(row => row.status === "PREPARING")) return;
    const timer = setTimeout(() => setRevision(x => x + 1), 15000); return () => clearTimeout(timer);
  }, [data]);
  async function run(action: () => Promise<unknown>) { setBusy(true); setError(undefined); try { await action(); setRevision(x => x + 1); } catch (e) { setError(e); } finally { setBusy(false); } }
  async function create(values: any) {
    await run(async () => { await api(editing ? `/contracts/${editing.id}` : "/contracts", editing ? "PUT" : "POST", { version: editing?.version, creatorId: editing?.creatorId || creator!.id, templateId: values.templateId, title: values.title, legalName: values.legalName, mobile: values.mobile, deadline: Math.floor(new Date(values.deadline + "T23:59:59").getTime()/1000), fields: values.fields || {} }); setCreating(false); form.resetFields(); });
  }
  async function view(row: CreatorContract) {
    await run(async () => { const logs = await api<typeof audits>(`/contracts/${row.id}/audit`); setAudits(logs); setDetail(row); });
  }
  async function resultLink(row: CreatorContract, preview: boolean) {
    await run(async () => { const result = await api<{url:string}>(`/contracts/${row.id}/${preview ? "preview" : "signing-url"}`, "POST"); if (preview) setPreviewUrl(externalUrl(result.url)); else setLink(externalUrl(result.url)); });
  }
  function launch(row: CreatorContract) {
    modal.confirm({ title: "确认发起真实合同签署？", content: <><p>{row.title}</p><p>签约创作者：{row.legalName}（{row.mobile}）</p><p>公司：{config?.companyName || "未配置"}</p><p>发起后将消耗电子签合同份额，合同内容将固定。请确认签约姓名、手机号和填写内容。</p></>, okText: "确认并发起签署", cancelText: "返回核对", onOk: () => run(async () => { const updated = await api<CreatorContract>(`/contracts/${row.id}/launch`, "POST", { version: row.version }); if (updated.errorCode) setError(new Error("合同生成暂未完成，进度已保存。请稍后同步状态，不必重新创建。")); }) });
  }
  if (loadError) return <ErrorState error={loadError} retry={() => setRevision(x => x + 1)}/>;
  return <>{context}
    <div className="contract-toolbar"><div>{creator ? <><h2>合同记录</h2><p className="muted">每次签约独立留存，资料变更不覆盖历史合同</p></> : <p className="muted">在创作者档案中拟定合同，统一查看生成与签署进度。</p>}</div><Space>{creator && !creator.archived && <Button type="primary" onClick={() => { setError(undefined); setEditing(undefined); form.resetFields(); form.setFieldsValue({ title: `${creator.name}合作合同`, legalName: creator.name, mobile: creator.phone, deadline: futureDate() }); setCreating(true); }}>拟定合同</Button>}<Button onClick={() => setRevision(x => x + 1)} loading={loading}>刷新记录</Button></Space></div>
    {!config?.ready && !loading && <Alert className="contract-notice" type="info" showIcon message="电子签尚未配置完成" description="管理员可先导入合同模板，完成企业接入和模板配置后即可生成合同。"/>}
    {config?.ready && config.environment === "test" && <Alert className="contract-notice" type="warning" showIcon message="当前为联调环境，生成的合同不具备法律效力"/>}
    {!!error && !creating && <ErrorNotice error={error}/>}
    <Table<CreatorContract> rowKey="id" loading={loading} dataSource={data?.items || []} scroll={{ x: 1050 }} pagination={{ current: page, total: data?.total, pageSize: 20, showSizeChanger: false, itemRender: paginationItem, onChange: setPage }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无合同记录"/> }} columns={[
      { title: "合同名称", dataIndex: "title", width: 230, render: (value, row) => <><Button type="link" className="contract-title" onClick={() => view(row)}>{value}</Button><div className="muted">{row.templateName}</div></> },
      { title: "创作者", dataIndex: "legalName", width: 120, render: (value, row) => <Link to={`/${row.creatorId}?tab=contracts`}>{value}</Link> },
      { title: "状态", dataIndex: "status", width: 130, render: (value, row) => <><Tag color={value === "SIGNED" ? "green" : ["PREPARING","SIGNING","FILLING"].includes(value) ? "blue" : undefined}>{contractStates[value] || value}</Tag>{row.errorCode && <div className="contract-status-note">需同步确认</div>}</> },
      { title: "签署截止", dataIndex: "deadline", width: 150, render: value => new Date(value * 1000).toLocaleDateString("zh-CN") },
      { title: "创建时间", dataIndex: "createdAt", width: 170, render: dateTime },
      { title: "操作", width: 320, render: (_, row) => <Space wrap><Button size="small" onClick={() => view(row)}>详情</Button>{row.status === "DRAFT" && <><Button size="small" onClick={() => { setEditing(row); form.resetFields(); form.setFieldsValue({ ...row, deadline: new Date(row.deadline * 1000).toLocaleDateString("sv-SE") }); setCreating(true); }}>编辑草稿</Button><Button size="small" disabled={!config?.ready || busy} onClick={() => modal.confirm({ title: "生成合同预览？", content: "预览仅用于核对内容，不会发起签署；文档合成会使用电子签预览服务份额。", okText: "生成预览", cancelText: "取消", onOk: () => run(async () => { const result = await api<{url:string}>(`/contracts/${row.id}/draft-preview`, "POST"); setPreviewUrl(externalUrl(result.url)); }) })}>预览草稿</Button></>}{row.status === "DRAFT" && admin && <Button type="primary" size="small" disabled={!config?.ready || busy} onClick={() => launch(row)}>发起签署</Button>}{row.status !== "DRAFT" && <Button size="small" disabled={busy || !config?.ready} onClick={() => run(() => api(`/contracts/${row.id}/sync`, "POST"))}>同步状态</Button>}{["SIGNING","FILLING"].includes(row.status) && <Button size="small" disabled={busy} onClick={() => resultLink(row, false)}>签署链接</Button>}{row.flowId && row.status !== "PREPARING" && <Button size="small" disabled={busy} onClick={() => resultLink(row, true)}>预览</Button>}{(row.archived || row.status === "SIGNED") && (row.archived ? <Button size="small" onClick={() => run(() => downloadContractFile(`/contracts/${row.id}/pdf`, row.title + ".pdf"))}>下载合同</Button> : <Button size="small" disabled={busy} onClick={() => run(() => api(`/contracts/${row.id}/archive`, "POST"))}>归档签署文件</Button>)}{admin && ["SIGNING","FILLING"].includes(row.status) && <Button size="small" danger disabled={busy} onClick={() => modal.confirm({ title: "撤销此合同？", content: "此操作将请求腾讯电子签撤销尚未签完的合同，额度是否返还以电子签规则为准。", okText: "确认撤销", cancelText: "取消", onOk: () => run(() => api(`/contracts/${row.id}/cancel`, "POST", { version: row.version })) })}>撤销</Button>}</Space> }
    ]}/>
    <Modal title={editing ? "编辑合同草稿" : "拟定创作者合同"} open={creating} onCancel={() => { if (!busy) setCreating(false); }} footer={null} destroyOnHidden width={680}>
      {!!error && <ErrorNotice error={error}/>} {!templates.some(x => x.enabled) ? <Empty description={<span>暂无可用模板，请先在<Link to="/contract-templates">合同模板</Link>中完成导入与配置。</span>}/> : <Form form={form} layout="vertical" onFinish={create} preserve={false}>
        <Form.Item label="合同模板" name="templateId" rules={[{ required: true, message: "请选择合同模板" }]}><Select placeholder="选择已配置的模板" options={templates.filter(x => x.enabled).map(x => ({ value: x.id, label: x.name }))} onChange={() => form.setFieldValue("fields", {})}/></Form.Item>
        <Form.Item label="合同名称" name="title" rules={[{ required: true, whitespace: true, message: "请填写合同名称" }]}><Input maxLength={200}/></Form.Item>
        <div className="contract-form-grid"><Form.Item label="创作者签约实名姓名" name="legalName" extra="档案可能使用昵称，请核对本人真实姓名" rules={[{ required: true, whitespace: true, message: "请填写签约实名姓名" }]}><Input maxLength={100}/></Form.Item><Form.Item label="签署手机号" name="mobile" rules={[{ required: true, pattern: /^1[3-9]\d{9}$/, message: "请填写大陆手机号" }]}><Input maxLength={11}/></Form.Item></div>
        <Form.Item label="签署截止日期" name="deadline" rules={[{ required: true, message: "请选择截止日期" }]}><Input type="date"/></Form.Item>
        {selected && <section className="contract-fields"><h3>合同填写内容</h3>{selected.fields.length ? selected.fields.map(field => <Form.Item key={field.id} name={["fields", field.id]} label={field.name || field.id} rules={field.required ? [{ required: true, whitespace: true, message: `请填写${field.name || "此字段"}` }] : []}>{field.type === "CHECK_BOX" ? <Select allowClear options={[{ value: "true", label: "是" }, { value: "false", label: "否" }]}/> : field.type === "MULTI_LINE_TEXT" ? <Input.TextArea rows={3} maxLength={10000}/> : <Input maxLength={10000}/>}</Form.Item>) : <p className="muted">此模板没有发起方填写字段。签约身份仍将按模板角色设置。</p>}</section>}
        <Form.Item name="identityConfirmed" valuePropName="checked" rules={[{ validator: (_, value) => value ? Promise.resolve() : Promise.reject(new Error("请先核对签约身份和填写内容")) }]}><Checkbox>已核对签约实名姓名、手机号和合同填写内容</Checkbox></Form.Item>
        <Button type="primary" htmlType="submit" loading={busy}>保存合同草稿</Button><p className="muted">保存草稿不会发起签署，管理员确认后正式发起。</p>
      </Form>}
    </Modal>
    <Modal title="合同详情" open={!!detail} onCancel={() => setDetail(undefined)} footer={<Button onClick={() => setDetail(undefined)}>关闭</Button>} width={720}>
      {detail && <><Descriptions bordered column={1} items={[{ key: "id", label: "内部合同编号", children: detail.id }, { key: "title", label: "合同名称", children: detail.title }, { key: "template", label: "使用模板", children: detail.templateName }, { key: "creator", label: "签约人", children: `${detail.legalName} · ${detail.mobile}` }, { key: "state", label: "签署状态", children: contractStates[detail.status] }, { key: "flow", label: "电子签合同编号", children: detail.flowId || "尚未生成" }, { key: "archive", label: "签署文件", children: detail.archived ? "已归档" : "尚未归档" }]}/>{detail.errorCode && <ErrorNotice error={new ApiError(detail.errorCode)}/>}<h3>填写内容</h3>{Object.entries(detail.fields).map(([key, value]) => <p key={key}><strong>{detail.fieldDefinitions.find(x => x.id === key)?.name || key}：</strong><span className="contract-field-value">{value || "未填写"}</span></p>)}<h3>操作记录</h3><ul className="contract-audit">{audits.map(x => <li key={x.id}><strong>{auditNames[x.action] || (x.action.startsWith("STATUS_") ? `状态变更：${contractStates[x.action.slice(7)] || x.action}` : x.action)}</strong><span>{x.actorName} · {dateTime(x.createdAt)}</span></li>)}</ul></>}
    </Modal>
    <Modal title="创作者签署链接" open={!!link} onCancel={() => setLink("")} footer={<Button onClick={() => setLink("")}>关闭</Button>}><p>此链接仅供对应创作者本人打开，请通过你的业务沟通渠道交给对方。</p><Input.TextArea aria-label="创作者签署链接" value={link} readOnly rows={3}/><a href={link} target="_blank" rel="noopener noreferrer">打开签署入口 ↗</a></Modal>
    <Modal title="合同预览" width={1100} open={!!previewUrl} onCancel={() => setPreviewUrl("")} footer={<Button onClick={() => setPreviewUrl("")}>关闭</Button>} destroyOnHidden><iframe className="contract-iframe" title="腾讯电子签合同预览" src={previewUrl} referrerPolicy="no-referrer"/></Modal>
  </>;
}
