import { useEffect, useState } from "react";
import { Button, Form, Input, Select } from "antd";
import { history, Link, useLocation, useParams } from "umi";
import { api, Creator, CreatorInput, contextualPath, DictionaryOption, Member, registryReturn } from "../api";
import { ErrorNotice, ErrorState, Loading, PageHeading, useMember } from "../components";
const units = ["条", "集", "分钟", "天", "项目"];
export default function Editor() {
  const { id } = useParams<{ id: string }>(), member = useMember(), location = useLocation(), returnTo = registryReturn(location.search), [form] = Form.useForm<CreatorInput>();
  const [creator, setCreator] = useState<Creator>(), [members, setMembers] = useState<Member[]>([]), [dictionary, setDictionary] = useState<DictionaryOption[]>([]), [loading, setLoading] = useState(true), [saving, setSaving] = useState(false), [error, setError] = useState<unknown>(), [loadError, setLoadError] = useState<unknown>(), [retry, setRetry] = useState(0);
  const back = id ? contextualPath("/" + id, returnTo) : returnTo;
  useEffect(() => {
    let active = true;
    setLoading(true); setLoadError(undefined);
    Promise.all([api<Member[]>("/members"), id ? api<Creator>("/creators/" + id) : Promise.resolve(undefined), api<DictionaryOption[]>("/dictionaries?kind=PROFESSION"), api<DictionaryOption[]>("/dictionaries?kind=TAG"), api<DictionaryOption[]>("/dictionaries?kind=SOURCE")]).then(([people, c, professions, tags, sources]) => {
      if (!active) return;
      setMembers(people); setCreator(c); setDictionary([...professions, ...tags, ...sources]);
      form.setFieldsValue(c ? { ...c, intro: c.intro || "", quoteAmount: c.quoteAmount ?? null, quoteUnit: c.quoteUnit || "" } : { name: "", phone: "", wechat: "", city: "", professions: [], tags: [], source: "", maintainerId: member.id, works: [], notes: "", intro: "", quoteAmount: null, quoteUnit: "" });
    }).catch(e => { if (active) setLoadError(e); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, retry]);
  async function save(values: CreatorInput) {
    setSaving(true); setError(undefined);
    try {
      const normalized = { ...values, name: values.name.trim(), professions: values.professions.map(x => x.trim()).filter(Boolean), tags: (values.tags || []).map(x => x.trim()).filter(Boolean), source: (values.source || "").trim(), intro: values.intro || "", quoteAmount: values.quoteAmount == null || String(values.quoteAmount) === "" ? null : Number(values.quoteAmount), quoteUnit: values.quoteUnit || "", works: (values.works || []).map(w => ({ title: w.title?.trim() || "", url: w.url.trim() })) };
      const c = await api<Creator>(id ? "/creators/" + id : "/creators", id ? "PUT" : "POST", id ? { ...normalized, version: creator!.version } : normalized);
      history.push(contextualPath("/" + c.id, returnTo));
    } catch (e) { setError(e); }
    finally { setSaving(false); }
  }
  const recommendations = (kind: string) => dictionary.filter(x => x.kind === kind).map(x => ({ value: x.name, label: x.name }));
  const pairedQuote = () => {
    const amount = form.getFieldValue("quoteAmount"), unit = form.getFieldValue("quoteUnit"), hasAmount = amount != null && String(amount) !== "";
    return hasAmount === Boolean(unit) ? Promise.resolve() : Promise.reject(new Error("请同时填写报价金额与单位"));
  };
  if (loading) return <Loading/>;
  if (loadError) return <ErrorState error={loadError} retry={() => setRetry(x => x + 1)}/>;
  if (creator?.archived) return <><PageHeading title="档案已归档"/><section className="panel"><p>恢复档案后可继续编辑。</p><Link to={back}>返回档案详情</Link></section></>;
  return <>
    <Link className="back-link" to={back}>← {id ? "返回档案" : "返回创作者资源库"}</Link>
    <PageHeading title={id ? "编辑档案" : "新增创作者"} description="完善创作者资料，方便团队查找与协作。"/>
    <Form className="form-layout" layout="vertical" form={form} onFinish={save} scrollToFirstError disabled={saving} noValidate>
      <section className="panel editor-panel">
        {!!error && <div className="notice"><ErrorNotice error={error}/></div>}
        <section className="editor-section" id="basic"><h2>基础资料</h2><div className="form-grid">
          <Form.Item name="name" label="姓名" rules={[{ required: true, whitespace: true, message: "请输入姓名" }, { max: 100, message: "姓名最多 100 字" }]}><Input maxLength={100} autoComplete="off"/></Form.Item>
          <Form.Item name="phone" label="手机"><Input maxLength={64} autoComplete="off"/></Form.Item>
          <Form.Item name="wechat" label="微信"><Input maxLength={100} autoComplete="off"/></Form.Item>
          <Form.Item name="city" label="城市"><Input maxLength={100}/></Form.Item>
          <Form.Item name="source" label="来源"><Input maxLength={200} list="editor-source-options" placeholder="选择推荐来源或自行输入"/></Form.Item>
          <datalist id="editor-source-options">{recommendations("SOURCE").map(x => <option key={x.value} value={x.value}/>)}</datalist>
          <Form.Item name="maintainerId" label="维护人" rules={[{ required: true, message: "请选择维护人" }]}><Select virtual={false} options={members.map(x => ({ value: x.id, label: x.displayName }))} showSearch optionFilterProp="label"/></Form.Item>
        </div><Form.Item name="intro" label="个人简介" rules={[{ max: 2000, message: "个人简介最多 2000 字" }]}><Input.TextArea rows={3} maxLength={2000} showCount placeholder="简要介绍创作者经历与合作特点"/></Form.Item></section>
        <section className="editor-section" id="capability"><h2>职业与能力</h2><div className="form-grid">
          <Form.Item name="professions" label="职业" rules={[{ required: true, type: "array", min: 1, transform: (values: string[]) => values?.map(x => x.trim()).filter(Boolean), message: "请选择至少一个职业" }, { type: "array", max: 20, message: "职业最多 20 项" }, { validator: (_, values: string[]) => !values?.some(x => !x.trim() || x.trim().length > 50) ? Promise.resolve() : Promise.reject(new Error("职业每项需为 1–50 字")) }]}><Select virtual={false} mode="tags" options={recommendations("PROFESSION")} placeholder="选择或输入职业"/></Form.Item>
          <Form.Item name="tags" label="擅长方向" rules={[{ type: "array", max: 50, message: "标签最多 50 项" }, { validator: (_, values: string[]) => !values?.some(x => !x.trim() || x.trim().length > 50) ? Promise.resolve() : Promise.reject(new Error("标签每项需为 1–50 字")) }]}><Select virtual={false} mode="tags" options={recommendations("TAG")} tokenSeparators={[",", "，"]} placeholder="选择推荐方向或输入后按回车添加"/></Form.Item>
          <Form.Item name="quoteAmount" label="报价金额" dependencies={["quoteUnit"]} rules={[{ validator: pairedQuote }, { validator: (_, value) => value == null || String(value) === "" || (/^\d+(\.\d{1,2})?$/.test(String(value)) && Number(value) <= 9999999999.99) ? Promise.resolve() : Promise.reject(new Error("报价金额需为非负数，最多两位小数且不超过 9999999999.99")) }]}><Input type="number" min="0" step="0.01" max="9999999999.99" placeholder="选填" prefix="¥"/></Form.Item>
          <Form.Item name="quoteUnit" label="报价单位" dependencies={["quoteAmount"]} rules={[{ validator: pairedQuote }]}><select className="native-select"><option value="">选择单位</option>{units.map(x => <option value={x} key={x}>{x}</option>)}</select></Form.Item>
        </div><p className="form-hint">参考报价供团队沟通使用，金额和单位需同时填写或同时留空。</p></section>
        <section className="editor-section" id="works"><h2>代表作品</h2><Form.List name="works">{(fields, { add, remove }) => <div className="works-editor">{fields.map((field, index) => <div className="work-row" key={field.key}><Form.Item name={[field.name, "title"]} label={`作品名称 ${index + 1}`} rules={[{ max: 200, message: "作品名称最多 200 字" }]}><Input maxLength={200} placeholder="选填"/></Form.Item><Form.Item name={[field.name, "url"]} label={`作品链接 ${index + 1}`} rules={[{ required: true, message: "请输入作品链接" }, { validator: (_, value) => { if (!value) return Promise.resolve(); try { const url = new URL(value); if (["http:", "https:"].includes(url.protocol)) return Promise.resolve(); } catch {} return Promise.reject(new Error("请输入 http 或 https 链接")); } }]}><Input placeholder="https://" maxLength={2048}/></Form.Item><Button className="remove-work" aria-label={`移除作品 ${index + 1}`} onClick={() => remove(field.name)}>移除</Button></div>)}<Button disabled={fields.length >= 50} onClick={() => add({ title: "", url: "" })}>添加作品链接</Button></div>}</Form.List></section>
        <section className="editor-section" id="notes"><h2>内部备注</h2><Form.Item name="notes" label="备注"><Input.TextArea rows={4} maxLength={10000} showCount/></Form.Item></section>
        <div className="form-actions"><Button htmlType="submit" type="primary" loading={saving}>保存档案</Button><Button onClick={() => history.push(back)}>取消</Button></div>
      </section>
      <aside className="editor-aside"><nav className="side-card form-section-nav" aria-label="表单目录"><h2>档案目录</h2><a href="#basic">01 基础资料</a><a href="#capability">02 职业与能力</a><a href="#works">03 代表作品</a><a href="#notes">04 内部备注</a></nav><section className="side-card form-tips"><h2>录入提示</h2><p>姓名和职业为必填项。</p><p>支持选择推荐职业和擅长方向，也可自行补充。作品仅支持 http 或 https 链接。</p></section></aside>
    </Form>
  </>;
}
