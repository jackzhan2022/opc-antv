import { useEffect, useMemo, useState } from "react";
import { Button, Empty, Form, Input, Table, Tabs, Tag } from "antd";
import { Link, history, useLocation } from "umi";
import { api, Creator, CreatorFilterOptions, CreatorSummary, contextualPath, DictionaryOption, Member, PageData, shortCreatorId } from "../api";
import { ErrorNotice, ErrorState, PageHeading, paginationItem } from "../components";

type Filters = { q: string; profession: string; tag: string; city: string; maintainerId: string; source: string };
const empty: Filters = { q: "", profession: "", tag: "", city: "", maintainerId: "", source: "" };
export default function Registry() {
  const location = useLocation(), [form] = Form.useForm<Filters>();
  const params = useMemo(() => new URLSearchParams(location.search), [location.search]);
  const filters = useMemo(() => Object.fromEntries(Object.keys(empty).map(key => [key, params.get(key) || ""])) as Filters, [params]);
  const tab = params.get("tab") === "recent" ? "recent" : params.get("tab") === "archived" || params.get("archived") === "true" ? "archived" : "all";
  const requestedPage = Number(params.get("page"));
  const page = Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;
  const [data, setData] = useState<PageData<Creator>>(), [summary, setSummary] = useState<CreatorSummary>(), [loading, setLoading] = useState(true), [error, setError] = useState<unknown>(), [optionsError, setOptionsError] = useState<unknown>(), [retry, setRetry] = useState(0), [more, setMore] = useState(Boolean(filters.tag || filters.maintainerId || filters.source)), [members, setMembers] = useState<Member[]>([]), [options, setOptions] = useState<CreatorFilterOptions>({ cities: [], sources: [] }), [dictionary, setDictionary] = useState<DictionaryOption[]>([]);
  useEffect(() => { form.setFieldsValue(filters); }, [filters]);
  useEffect(() => {
    let active = true;
    setOptionsError(undefined);
    Promise.all([api<Member[]>("/members"), api<CreatorFilterOptions>("/creators/filter-options"), ...["PROFESSION", "TAG", "SOURCE"].map(kind => api<DictionaryOption[]>("/dictionaries?kind=" + kind))])
      .then(([people, values, ...entries]) => { if (active) { setMembers(people as Member[]); setOptions(values as CreatorFilterOptions); setDictionary((entries as DictionaryOption[][]).flat()); } }).catch(e => { if (active) setOptionsError(e); });
    return () => { active = false; };
  }, [retry]);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(undefined);
    const query = new URLSearchParams({ ...filters, archived: String(tab === "archived"), page: String(page), pageSize: "20" });
    if (tab === "recent") query.set("recent", "true");
    setSummary(undefined);
    Promise.all([api<PageData<Creator>>("/creators?" + query), api<CreatorSummary>("/creators/summary?" + new URLSearchParams(filters))]).then(([items, counts]) => {
      if (!active) return;
      const lastPage = Math.max(1, Math.ceil(items.total / 20));
      if (page > lastPage) {
        const corrected = new URLSearchParams(location.search);
        corrected.set("page", String(lastPage));
        history.replace("/?" + corrected);
        return;
      }
      setData(items); setSummary(counts);
    }).catch(e => { if (active) setError(e); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [filters, tab, page, retry]);
  function navigate(values: Filters, selectedTab = tab, selectedPage = 1) {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(values)) if (value?.trim()) query.set(key, value.trim());
    query.set("tab", selectedTab); query.set("page", String(selectedPage));
    history.push("/?" + query);
  }
  const returnTo = "/" + location.search, link = (path: string) => contextualPath(path, returnTo);
  const recommendations = (kind: string) => dictionary.filter(x => x.kind === kind).map(x => x.name);
  return <>
    <PageHeading title="创作者资源库" description="集中登记与维护公司的外部职业创作者资料。" actions={<Button type="primary" onClick={() => history.push(link("/new"))}><span aria-hidden="true">＋</span>新增创作者</Button>}/>
    <section className="panel registry-panel" aria-label="创作者列表">
      <Tabs activeKey={tab} onChange={key => navigate(filters, key)} items={[["all", "全部创作者"], ["recent", "最近更新"], ["archived", "已归档"]].map(([key, label]) => ({ key, label: <span>{label} <span className="tab-count">{summary ? summary[key as keyof CreatorSummary] : "—"}</span></span> }))}/>
      <Form className="registry-filters" form={form} initialValues={filters} onFinish={values => navigate(values)}>
        <div className="registry-toolbar">
          <Form.Item name="q" className="search-field"><Input aria-label="搜索创作者" placeholder="搜索姓名、手机或微信" allowClear prefix={<svg className="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" aria-hidden="true"><circle cx="10" cy="10" r="6"/><path d="m15 15 5 5"/></svg>}/></Form.Item>
          <Form.Item name="profession"><Input aria-label="筛选职业" list="profession-options" placeholder="全部职业" allowClear/></Form.Item>
          <Form.Item name="city"><Input aria-label="筛选城市" list="city-options" placeholder="全部城市" allowClear/></Form.Item>
          <Button type="text" aria-expanded={more} onClick={() => setMore(!more)}>更多筛选</Button>
          <div className="button-row"><Button htmlType="submit" type="primary">查询</Button><Button onClick={() => { form.setFieldsValue(empty); navigate(empty); }}>重置</Button></div>
        </div>
        {more && <div className="registry-toolbar advanced-filters"><Form.Item name="tag" label="擅长方向"><Input aria-label="筛选标签" list="tag-options" placeholder="全部方向" allowClear/></Form.Item><Form.Item name="maintainerId" label="维护人"><select className="native-select" aria-label="筛选维护人"><option value="">全部维护人</option>{members.map(x => <option key={x.id} value={x.id}>{x.displayName}</option>)}</select></Form.Item><Form.Item name="source" label="来源渠道"><Input aria-label="筛选来源" list="source-options" placeholder="全部来源" allowClear/></Form.Item></div>}
        <datalist id="profession-options">{recommendations("PROFESSION").map(x => <option key={x} value={x}/>)}</datalist><datalist id="city-options">{options.cities.map(x => <option key={x} value={x}/>)}</datalist><datalist id="tag-options">{recommendations("TAG").map(x => <option key={x} value={x}/>)}</datalist><datalist id="source-options">{[...new Set([...options.sources, ...recommendations("SOURCE")])].map(x => <option key={x} value={x}/>)}</datalist>
      </Form>
      {!!optionsError && <div className="notice filter-notice"><ErrorNotice error={optionsError}/><Button onClick={() => setRetry(x => x + 1)}>重试筛选选项</Button></div>}
      {error ? <ErrorState error={error} retry={() => setRetry(x => x + 1)}/> : <Table<Creator> rowKey="id" loading={loading} dataSource={data?.items || []} scroll={{ x: 1030 }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={tab === "archived" ? "暂无归档档案" : "暂无创作者"}/> }} pagination={{ itemRender: paginationItem, current: page, total: data?.total || 0, pageSize: 20, showSizeChanger: false, onChange: n => navigate(filters, tab, n), showTotal: total => `共 ${total} 位创作者`, responsive: true }} columns={[
        { title: "创作者", dataIndex: "name", width: 190, render: (name, c) => <div className="person"><span className="avatar" aria-hidden="true">{name.slice(-2)}</span><span><Link className="creator-name" to={link(`/${c.id}`)}>{name}</Link><small title={c.id}>档案 {shortCreatorId(c.id)}</small></span></div> },
        { title: "职业", dataIndex: "professions", width: 125, render: (items: string[]) => <div className="row-tags">{items.map(x => <Tag color="blue" key={x}>{x}</Tag>)}</div> },
        { title: "擅长方向", dataIndex: "tags", width: 170, render: (items: string[]) => <div className="row-tags">{items.length ? items.map(x => <Tag key={x}>{x}</Tag>) : "—"}</div> },
        { title: "城市", dataIndex: "city", width: 75, render: x => x || "—" },
        { title: "联系方式", width: 160, render: (_, c) => <div className="contact"><span>{c.wechat || "未填写微信"}</span><small>{c.phone || "未填写手机"}</small></div> },
        { title: "维护人 / 更新日期", width: 160, render: (_, c) => <div className="contact"><span>{c.maintainerName}</span><small>{new Intl.DateTimeFormat("zh-CN").format(new Date(c.updatedAt))}</small></div> },
        { title: "操作", width: 100, render: (_, c) => <div className="table-actions"><Link to={link(`/${c.id}`)}>查看</Link>{!c.archived && <Link to={link(`/${c.id}/edit`)}>编辑</Link>}</div> },
      ]}/>}
    </section>
  </>;
}
