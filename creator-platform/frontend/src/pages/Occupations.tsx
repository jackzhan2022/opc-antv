import { useEffect, useState } from "react";
import {
  Alert,
  Button,
  Empty,
  Form,
  Input,
  Modal,
  Result,
  Switch,
  Tag,
} from "antd";
import { Link } from "umi";
import { PageHeading, Loading, useMember } from "../components";
import {
  configurationError,
  DictionaryEntry,
  DictionaryInput,
  needsConfigurationRefresh,
  saveDictionary,
  useDictionaries,
} from "../config-api";
import "../configuration.css";
const kinds = ["PROFESSION"] as const;
type Fields = Pick<DictionaryInput, "name" | "description" | "enabled">;
export default function Occupations() {
  const allowed = useMember()?.role === "ADMIN";
  const { entries, loading, error, reload } = useDictionaries(kinds, allowed);
  const [selected, setSelected] = useState<string>();
  const [form] = Form.useForm<Fields>();
  const [createForm] = Form.useForm<Fields>();
  const [creating, setCreating] = useState(false),
    [busy, setBusy] = useState(false);
  const [saveError, setSaveError] = useState<unknown>(),
    [createError, setCreateError] = useState<unknown>();
  const [saved, setSaved] = useState(false);
  const current = entries.find((row) => row.id === selected);
  useEffect(() => {
    if (!selected && entries.length) choose(entries[0]);
  }, [entries, selected]);
  function choose(row: DictionaryEntry) {
    setSelected(row.id);
    form.setFieldsValue(row);
    setSaveError(undefined);
    setSaved(false);
  }
  async function submit(values: Fields) {
    if (!current) return;
    setBusy(true);
    setSaveError(undefined);
    setSaved(false);
    try {
      await saveDictionary(
        {
          ...values,
          kind: "PROFESSION",
          category: current.category || "",
          version: current.version,
        },
        current.id,
      );
      setSaved(true);
      await reload();
    } catch (e) {
      setSaveError(e);
    } finally {
      setBusy(false);
    }
  }
  async function create(values: Fields) {
    setBusy(true);
    setCreateError(undefined);
    try {
      const row = await saveDictionary({
        ...values,
        kind: "PROFESSION",
        category: "",
      });
      await reload();
      choose(row);
      setCreating(false);
    } catch (e) {
      setCreateError(e);
    } finally {
      setBusy(false);
    }
  }
  async function refresh() {
    if (await reload()) setSaveError(undefined);
  }
  if (!allowed)
    return (
      <Result
        status="403"
        title="403"
        subTitle="仅管理员可访问此页面"
        extra={<Link to="/">返回创作者资源库</Link>}
      />
    );
  const fields = (
    <>
      <Form.Item
        name="name"
        label="职业名称"
        rules={[
          { required: true, whitespace: true, message: "请输入职业名称" },
          { max: 50, message: "职业名称最多50个字" },
        ]}
      >
        <Input maxLength={50} />
      </Form.Item>
      <Form.Item
        name="description"
        label="职业说明"
        rules={[{ max: 500, message: "职业说明最多500个字" }]}
      >
        <Input.TextArea rows={4} maxLength={500} />
      </Form.Item>
      <Form.Item name="enabled" label="启用状态" valuePropName="checked">
        <Switch
          aria-label="启用职业"
          checkedChildren="启用"
          unCheckedChildren="停用"
        />
      </Form.Item>
    </>
  );
  return (
    <>
      <PageHeading
        title="职业分类"
        description="统一职业名称与使用状态"
        actions={
          <Button
            type="primary"
            onClick={() => {
              createForm.resetFields();
              setCreateError(undefined);
              setCreating(true);
            }}
          >
            新增职业
          </Button>
        }
      />
      <div className="configuration-layout">
        <aside className="panel configuration-sidebar" aria-label="职业类型">
          <div className="configuration-panel-heading">
            <h2>职业类型</h2>
            <span>{entries.length}</span>
          </div>
          {!!error && (
            <div className="configuration-feedback">
              <Alert
                role="alert"
                type="error"
                message={configurationError(error)}
                action={
                  <Button size="small" onClick={() => void reload()}>
                    重试
                  </Button>
                }
              />
            </div>
          )}
          {loading && !entries.length ? (
            <Loading />
          ) : (
            <div className="configuration-list">
              {entries.map((row) => (
                <button
                  key={row.id}
                  className={`configuration-item ${row.id === selected ? "is-active" : ""}`}
                  aria-pressed={row.id === selected}
                  onClick={() => choose(row)}
                  disabled={busy}
                >
                  <span className="configuration-item-name">
                    {row.name}
                    {!row.enabled && (
                      <span className="configuration-off">停用</span>
                    )}
                  </span>
                  <span className="configuration-count">
                    {row.usageCount} 人
                  </span>
                </button>
              ))}
              {!entries.length && !error && (
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description="暂无职业"
                />
              )}
            </div>
          )}
        </aside>
        <section className="panel configuration-detail" aria-label="职业设置">
          {current ? (
            <>
              <div className="configuration-panel-heading">
                <div>
                  <h2>{current.name}</h2>
                  <p>{current.usageCount} 位创作者使用</p>
                </div>
                <Tag color={current.enabled ? "blue" : undefined}>
                  {current.enabled ? "启用中" : "已停用"}
                </Tag>
              </div>
              <Form
                name="occupation-settings"
                form={form}
                layout="vertical"
                className="configuration-form"
                onFinish={submit}
                disabled={busy}
                onValuesChange={() => setSaved(false)}
              >
                {!!saveError && (
                  <Alert
                    role="alert"
                    type="error"
                    message={configurationError(saveError)}
                    action={
                      needsConfigurationRefresh(saveError) && (
                        <Button
                          size="small"
                          loading={loading}
                          onClick={() => void refresh()}
                        >
                          刷新配置
                        </Button>
                      )
                    }
                  />
                )}
                {saved && (
                  <Alert type="success" role="status" message="职业已保存" />
                )}
                {fields}
                <div className="configuration-form-footer">
                  <Button htmlType="submit" type="primary" loading={busy}>
                    保存职业
                  </Button>
                </div>
              </Form>
            </>
          ) : (
            <div className="configuration-select-empty">
              <Empty
                image={Empty.PRESENTED_IMAGE_SIMPLE}
                description="选择职业查看设置"
              />
            </div>
          )}
        </section>
      </div>
      <Modal
        className="configuration-modal"
        title="新增职业"
        open={creating}
        onCancel={() => {
          if (!busy) setCreating(false);
        }}
        footer={null}
        destroyOnHidden
        maskClosable={false}
      >
        <Form
          name="occupation-create"
          form={createForm}
          layout="vertical"
          onFinish={create}
          initialValues={{ name: "", description: "", enabled: true }}
          disabled={busy}
        >
          {!!createError && (
            <Alert
              role="alert"
              type="error"
              message={configurationError(createError)}
            />
          )}
          {fields}
          <div className="configuration-modal-actions">
            <Button onClick={() => setCreating(false)} disabled={busy}>
              取消
            </Button>
            <Button htmlType="submit" type="primary" loading={busy}>
              创建职业
            </Button>
          </div>
        </Form>
      </Modal>
    </>
  );
}
