import { useState } from "react";
import { Result, Tag } from "antd";
import { Link } from "umi";
import { PageHeading, useMember } from "../components";
import { PLATFORM_MEMBERS_URL } from "../config-api";
import "../configuration.css";
export default function Roles() {
  const member = useMember();
  const [role, setRole] = useState<"ADMIN" | "MEMBER">("ADMIN");
  if (member?.role !== "ADMIN")
    return (
      <Result
        status="403"
        title="403"
        subTitle="仅管理员可访问此页面"
        extra={<Link to="/">返回创作者资源库</Link>}
      />
    );
  const admin = role === "ADMIN";
  return (
    <>
      <PageHeading title="角色权限" description="当前生效的内置角色策略" />
      <div className="configuration-layout">
        <aside
          className="panel configuration-sidebar"
          aria-label="平台内置角色"
        >
          <div className="configuration-panel-heading">
            <h2>平台内置角色</h2>
          </div>
          <div className="configuration-list">
            {(
              [
                ["ADMIN", "管理员"],
                ["MEMBER", "普通成员"],
              ] as const
            ).map(([key, name]) => (
              <button
                key={key}
                className={`configuration-item ${role === key ? "is-active" : ""}`}
                aria-pressed={role === key}
                onClick={() => setRole(key)}
              >
                <span>{name}</span>
                <span aria-hidden="true">›</span>
              </button>
            ))}
          </div>
        </aside>
        <section className="panel configuration-detail" aria-label="角色策略">
          <div className="configuration-panel-heading">
            <div>
              <h2>{admin ? "管理员" : "普通成员"}</h2>
              <p>{role}</p>
            </div>
            <Tag color="blue">内置角色</Tag>
          </div>
          <div className="configuration-role-content">
            <p className="configuration-policy">
              所有启用的内部成员均可维护公司共享档案。
            </p>
            <dl className="configuration-permissions">
              <div><dt>合同管理</dt><dd>{admin ? "拟定、发起与撤销合同，管理合同模板，查看及归档签署文件" : "拟定合同草稿，查看合同、获取签署链接及归档签署文件；正式发起由管理员确认"}</dd></div>
              <div>
                <dt>创作者档案</dt>
                <dd>查看、搜索、新增、编辑、归档与恢复</dd>
              </div>
              <div>
                <dt>基础配置</dt>
                <dd>
                  {admin
                    ? "维护职业分类、技能标签与来源渠道"
                    : "使用已启用的职业、标签与来源选项"}
                </dd>
              </div>
              <div>
                <dt>角色策略</dt>
                <dd>{admin ? "查看当前内置角色策略" : "按普通成员策略使用"}</dd>
              </div>
            </dl>
            <div className="configuration-members">
              <div>
                <h3>成员与身份</h3>
                <p>在统一平台管理成员状态与角色分配。</p>
              </div>
              <a
                className="ant-btn ant-btn-default"
                href={PLATFORM_MEMBERS_URL}
              >
                管理成员与身份
              </a>
            </div>
          </div>
        </section>
      </div>
    </>
  );
}
