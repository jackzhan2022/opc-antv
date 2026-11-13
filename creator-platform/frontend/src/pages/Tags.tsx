import { useMemo, useState } from "react";
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
import { Loading, PageHeading, useMember } from "../components";
import {
  configurationError,
  DictionaryEntry,
  DictionaryInput,
  DictionaryKind,
  needsConfigurationRefresh,
  saveDictionary,
  useDictionaries,
} from "../config-api";
import "../configuration.css";
const kinds = ["TAG", "SOURCE"] as const;
const groupKey = (row: Pick<DictionaryEntry, "kind" | "category">) =>
  row.kind === "SOURCE" ? "SOURCE" : `TAG:${row.category || "专业能力"}`;
type Fields = Pick<
  DictionaryInput,
  "name" | "category" | "description" | "enabled"
>;
export default function Tags() {
  const allowed = useMember()?.role === "ADMIN";
  const { entries, loading, error, reload } = useDictionaries(kinds, allowed);
  const [selected, setSelected] = useState("TAG:专业能力");
  const [dialog, setDialog] = useState<{
    kind: DictionaryKind;
    entry?: DictionaryEntry;
  }>();
  const [busy, setBusy] = useState(false),
    [saveError, setSaveError] = useState<unknown>();
  const [form] = Form.useForm<Fields>();
  const groups = useMemo(() => {
    const categories = [
      ...new Set([
        "专业能力",
        ...entries
          .filter((row) => row.kind === "TAG")
          .map((row) => row.category || "专业能力"),
      ]),
    ];
    return [
      ...categories.map((name) => ({
        key: `TAG:${name}`,
        name,
        kind: "TAG" as const,
      })),
      { key: "SOURCE", name: "来源渠道", kind: "SOURCE" as const },
    ];
  }, [entries]);
  const group = groups.find((row) => row.key === selected) || groups[0];
  const rows = entries.filter((row) => groupKey(row) === group.key);
  const source = dialog?.kind === "SOURCE",
    noun = source ? "来源" : "标签";
  function open(kind: DictionaryKind, entry?: DictionaryEntry) {
    form.resetFields();
    form.setFieldsValue(
      entry || {
        name: "",
        category:
          kind === "SOURCE"
            ? ""
            : group.kind === "TAG"
              ? group.name
              : "专业能力",
        description: "",
        enabled: true,
      },
    );
    setSaveError(undefined);
    setDialog({ kind, entry });
  }
  async function submit(values: Fields) {
    if (!dialog) return;
    setBusy(true);
    setSaveError(undefined);
    try {
      const row = await saveDictionary(
        {
          ...values,
          kind: dialog.kind,
          category:
            dialog.kind === "SOURCE"
              ? ""
              : (values.category || "专业能力").trim(),
          version: dialog.entry?.version,
        },
        dialog.entry?.id,
      );
      await reload();
      setSelected(groupKey(row));
      setDialog(undefined);
    } catch (e) {
      setSaveError(e);
    } finally {
      setBusy(false);
    }
  }
  async function refresh() {
    const result = await reload();
    if (!result) return;
    const latest = result.find((row) => row.id === dialog?.entry?.id);
    if (latest && dialog) {
      setDialog({ ...dialog, entry: latest });
      setSaveError(undefined);
    }
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
  return (
    <>
      <PageHeading
        title="技能标签"
        description="分类维护标签与来源渠道"
        actions={
          <Button type="primary" onClick={() => open(group.kind)}>
            {group.kind === "SOURCE" ? "新增来源" : "新增标签"}
          </Button>
        }
      />
      <div className="configuration-layout">
        <aside className="panel configuration-sidebar" aria-label="标签分类">
          <div className="configuration-panel-heading">
            <h2>标签分类</h2>
          </div>
          <div className="configuration-list">
            {groups.map((item) => (
              <button
                key={item.key}
                className={`configuration-item ${group.key === item.key ? "is-active" : ""}`}
                onClick={() => setSelected(item.key)}
                aria-pressed={group.key === item.key}
              >
                <span>{item.name}</span>
                <span className="configuration-count">
                  {entries.filter((row) => groupKey(row) === item.key).length}
                </span>
              </button>
            ))}
          </div>
        </aside>
        <section
          className="panel configuration-detail"
          aria-label={`${group.name}标签`}
        >
          <div className="configuration-panel-heading">
            <h2>{group.name}</h2>
            <span>
              {rows.length} 个{group.kind === "SOURCE" ? "来源" : "标签"}
            </span>
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
          ) : rows.length ? (
            <div className="configuration-tag-grid">
              {rows.map((row) => (
                <article
                  className="configuration-tag-card"
                  key={row.id}
                  aria-label={row.name}
                >
                  <div className="configuration-tag-copy">
                    <h3>{row.name}</h3>
                    <span>{row.usageCount} 人</span>
                  </div>
                  {!row.enabled && <Tag>已停用</Tag>}
                  <Button
                    type="text"
                    size="small"
                    aria-label={`编辑${row.name}`}
                    onClick={() => open(row.kind, row)}
                  >
                    编辑
                  </Button>
                </article>
              ))}
            </div>
          ) : (
            !error && (
              <div className="configuration-select-empty">
                <Empty
                  image={Empty.PRESENTED_IMAGE_SIMPLE}
                  description={
                    group.kind === "SOURCE" ? "暂无来源渠道" : "暂无标签"
                  }
                />
              </div>
            )
          )}
        </section>
      </div>
      <Modal
        className="configuration-modal"
        title={`${dialog?.entry ? "编辑" : "新增"}${noun}`}
        open={!!dialog}
        onCancel={() => {
          if (!busy) setDialog(undefined);
        }}
        footer={null}
        destroyOnHidden
        maskClosable={false}
      >
        <Form
          name="dictionary-tag-editor"
          form={form}
          layout="vertical"
          onFinish={submit}
          disabled={busy}
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
          <Form.Item
            name="name"
            label={`${noun}名称`}
            rules={[
              {
                required: true,
                whitespace: true,
                message: `请输入${noun}名称`,
              },
              {
                max: source ? 200 : 50,
                message: `${noun}名称最多${source ? 200 : 50}个字`,
              },
            ]}
          >
            <Input maxLength={source ? 200 : 50} />
          </Form.Item>
          {!source && (
            <>
              <Form.Item
                name="category"
                label="标签分类"
                rules={[{ max: 50, message: "分类名称最多50个字" }]}
              >
                <Input
                  list="configuration-tag-categories"
                  maxLength={50}
                  placeholder="专业能力"
                />
              </Form.Item>
              <datalist id="configuration-tag-categories">
                {groups
                  .filter((item) => item.kind === "TAG")
                  .map((item) => (
                    <option value={item.name} key={item.key} />
                  ))}
              </datalist>
            </>
          )}
          <Form.Item
            name="description"
            label={`${noun}说明`}
            rules={[{ max: 500, message: "说明最多500个字" }]}
          >
            <Input.TextArea rows={3} maxLength={500} />
          </Form.Item>
          <Form.Item name="enabled" label="启用状态" valuePropName="checked">
            <Switch
              aria-label={`启用${noun}`}
              checkedChildren="启用"
              unCheckedChildren="停用"
            />
          </Form.Item>
          <div className="configuration-modal-actions">
            <Button disabled={busy} onClick={() => setDialog(undefined)}>
              取消
            </Button>
            <Button htmlType="submit" type="primary" loading={busy}>
              {dialog?.entry ? "保存" : "创建"}
              {noun}
            </Button>
          </div>
        </Form>
      </Modal>
    </>
  );
}
