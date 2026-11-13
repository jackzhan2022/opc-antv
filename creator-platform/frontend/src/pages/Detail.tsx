import { useEffect, useState } from "react";
import { Button, Descriptions, Empty, Modal, Table, Tabs, Tag } from "antd";
import { history, Link, useLocation, useParams } from "umi";
import { api, Audit, Creator, contextualPath, dateTime, PageData, registryReturn, shortCreatorId } from "../api";
import { ErrorNotice, ErrorState, Loading, paginationItem } from "../components";
const actionNames = { CREATED: "创建档案", UPDATED: "编辑档案", ARCHIVED: "归档档案", RESTORED: "恢复档案" };
function AuditTable({ id, revision }: { id: string; revision: number }) {
  const [data, setData] = useState<PageData<Audit>>(), [page, setPage] = useState(1), [loading, setLoading] = useState(true), [error, setError] = useState<unknown>(), [retry, setRetry] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(undefined);
    api<PageData<Audit>>(`/creators/${id}/audit?page=${page}&pageSize=20`).then(x => { if (active) setData(x); }).catch(e => { if (active) setError(e); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, page, revision, retry]);
  return error ? <ErrorState error={error} retry={() => setRetry(x => x + 1)}/> : <Table<Audit> rowKey="id" loading={loading} dataSource={data?.items || []} scroll={{ x: 620 }} pagination={{ itemRender: paginationItem, current: page, total: data?.total, pageSize: 20, showSizeChanger: false, onChange: setPage }} locale={{ emptyText: <Empty description="暂无操作记录" image={Empty.PRESENTED_IMAGE_SIMPLE}/> }} columns={[{ title: "操作", dataIndex: "action", width: 100, render: (x: keyof typeof actionNames) => actionNames[x] }, { title: "操作人", dataIndex: "actorName", width: 120 }, { title: "变更内容", dataIndex: "detail" }, { title: "时间", dataIndex: "createdAt", width: 175, render: dateTime }]}/>;
}
export default function Detail() {
  const { id } = useParams<{ id: string }>(), location = useLocation(), returnTo = registryReturn(location.search);
  const [creator, setCreator] = useState<Creator>(), [loading, setLoading] = useState(true), [loadError, setLoadError] = useState<unknown>(), [error, setError] = useState<unknown>(), [retry, setRetry] = useState(0), [busy, setBusy] = useState(false), [copied, setCopied] = useState(false);
  const [modal, contextHolder] = Modal.useModal();
  useEffect(() => {
    let active = true;
    setLoading(true); setLoadError(undefined);
    api<Creator>("/creators/" + id).then(x => { if (active) setCreator(x); }).catch(e => { if (active) setLoadError(e); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, retry]);
  async function changeStatus(action: "archive" | "restore") {
    setBusy(true); setError(undefined);
    try { setCreator(await api<Creator>(`/creators/${id}/${action}`, "POST", { version: creator!.version })); }
    catch (e) { setError(e); }
    finally { setBusy(false); }
  }
  function archive() { modal.confirm({ title: "归档此创作者？", content: "归档后将保留资料和操作记录，恢复后可继续编辑。", okText: "确认归档", cancelText: "取消", onOk: () => changeStatus("archive") }); }
  async function copySummary() {
    if (!creator) return;
    const c = creator, text = `${c.name}｜${c.professions.join(" / ")}｜${c.city || "城市未填写"}\n擅长方向：${c.tags.join("、") || "未填写"}\n微信：${c.wechat || "未填写"}\n手机：${c.phone || "未填写"}${c.intro ? "\n" + c.intro : ""}`;
    try { await navigator.clipboard.writeText(text); setCopied(true); }
    catch { modal.info({ title: "复制档案摘要", content: <textarea className="summary-text" aria-label="档案摘要" readOnly value={text} rows={7}/> }); }
  }
  if (loading) return <Loading/>;
  if (loadError) return <ErrorState error={loadError} retry={() => setRetry(x => x + 1)}/>;
  if (!creator) return null;
  const c = creator, value = (x?: string) => x || <span className="muted">未填写</span>;
  return <>
    {contextHolder}<Link className="back-link" to={returnTo}>← 返回创作者资源库</Link>
    {!!error && <div className="notice"><ErrorNotice error={error}/><Button className="refresh-button" onClick={() => { setError(undefined); setRetry(x => x + 1); }}>刷新档案</Button></div>}
    {c.archived && <div className="archive-banner"><Tag>已归档</Tag>这位创作者已归档，资料仍然保留。</div>}
    <div className="detail-layout">
      <section className="panel profile-panel" aria-label="创作者档案">
        <div className="profile-head"><span className="avatar profile-avatar" aria-hidden="true">{c.name.slice(-2)}</span><div className="profile-identity"><h1>{c.name}</h1><div className="row-tags">{c.professions.map(x => <Tag color="blue" key={x}>{x}</Tag>)}<span className="muted">{c.city}</span></div><div className="profile-meta"><span title={c.id}>档案编号 {shortCreatorId(c.id)}</span><span>最近更新 {new Intl.DateTimeFormat("zh-CN").format(new Date(c.updatedAt))}</span></div></div>{!c.archived && <Button onClick={() => history.push(contextualPath(`/${id}/edit`, returnTo))}>编辑档案</Button>}</div>
        <Tabs items={[{ key: "profile", label: "人员档案", children: <div className="profile-content">
          <section className="profile-section"><h2>基础资料</h2><Descriptions column={{ xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2 }} layout="vertical" items={[{ key: "name", label: "姓名 / 昵称", children: c.name }, { key: "city", label: "所在城市", children: value(c.city) }, { key: "wechat", label: "微信号", children: value(c.wechat) }, { key: "phone", label: "手机号", children: value(c.phone) }, { key: "source", label: "来源渠道", children: value(c.source) }, { key: "maintainer", label: "维护负责人", children: c.maintainerName }, { key: "intro", label: "个人简介", span: 2, children: <div className="notes">{value(c.intro)}</div> }]}/></section>
          <section className="profile-section"><h2>职业与能力</h2><Descriptions column={{ xs: 1, sm: 2, md: 2, lg: 2, xl: 2, xxl: 2 }} layout="vertical" items={[{ key: "professions", label: "职业", children: <div className="row-tags">{c.professions.map(x => <Tag color="blue" key={x}>{x}</Tag>)}</div> }, { key: "tags", label: "擅长方向", children: c.tags.length ? <div className="row-tags">{c.tags.map(x => <Tag key={x}>{x}</Tag>)}</div> : value("") }]}/></section>
          <section className="profile-section"><h2>代表作品 <span className="count-label">{c.works.length} 项</span></h2>{c.works.length ? <ul className="work-links">{c.works.map((work, i) => <li key={i}><span className="work-symbol" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5"><rect x="3" y="3" width="18" height="18" rx="2"/><path d="m10 8 6 4-6 4Z"/></svg></span><div>{/^https?:\/\//i.test(work.url) ? <a href={work.url} target="_blank" rel="noopener noreferrer">{work.title || work.url}<span aria-hidden="true"> ↗</span></a> : <span>{work.title || "链接不可用"}</span>}<small>{work.url}</small></div></li>)}</ul> : <p className="muted">未填写作品链接</p>}</section>
          <section className="profile-section"><h2>内部备注</h2><p className="notes">{value(c.notes)}</p></section>
        </div> }, { key: "audit", label: "修改记录", children: <div className="audit-content"><h2>资料修改记录</h2><AuditTable id={id!} revision={c.version}/></div> }]}/>
      </section>
      <aside className="detail-aside" aria-label="档案维护与操作"><section className="side-card"><h2>资料维护</h2><dl className="maintenance-meta"><dt>维护负责人</dt><dd><span className="avatar small-avatar" aria-hidden="true">{c.maintainerName.slice(-2)}</span>{c.maintainerName}</dd><dt>录入时间</dt><dd>{dateTime(c.createdAt)}</dd><dt>最近更新时间</dt><dd>{dateTime(c.updatedAt)}</dd></dl></section><section className="side-card"><h2>参考报价</h2><div className="quote-amount">{c.quoteAmount != null ? <><span className="currency">¥ </span><span>{c.quoteAmount.toLocaleString("zh-CN", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span><small> / {c.quoteUnit}</small></> : <span className="muted">未填写</span>}</div></section><section className="side-card"><h2>档案操作</h2><div className="side-actions"><Button onClick={copySummary}>{copied ? "已复制档案摘要" : "复制档案摘要"}</Button>{copied && <span role="status" className="muted">档案摘要已复制</span>}{c.archived ? <Button type="primary" onClick={() => changeStatus("restore")} loading={busy}>恢复档案</Button> : <Button type="text" onClick={archive} loading={busy}>归档档案</Button>}</div></section></aside>
    </div>
  </>;
}
