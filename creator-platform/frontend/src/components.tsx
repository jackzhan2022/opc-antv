import { Alert, Button, Result, Spin } from "antd";
import { cloneElement, createContext, isValidElement, useContext } from "react";
import { Link } from "umi";
import { ApiError, errorMessage, Member } from "./api";
export const MemberContext = createContext<Member | null>(null);
export function useMember() {
  return useContext(MemberContext)!;
}
export function Loading() {
  return (
    <div className="loading" role="status" aria-label="正在加载">
      <Spin size="large" />
      <span>正在加载</span>
    </div>
  );
}
export function ErrorNotice({ error }: { error: unknown }) {
  return (
    <Alert role="alert" type="error" showIcon message={errorMessage(error)} />
  );
}
export function ErrorState({
  error,
  retry,
}: {
  error: unknown;
  retry?: () => void;
}) {
  const missing =
    error instanceof ApiError && error.code === "creator_not_found";
  return (
    <Result
      status={missing ? "404" : "error"}
      title={errorMessage(error)}
      extra={
        <div className="button-row">
          {retry && <Button onClick={retry}>重试</Button>}
          <Link to="/">返回创作者列表</Link>
        </div>
      }
    />
  );
}
export function PageHeading({
  title,
  description,
  actions,
}: {
  title: string;
  description?: string;
  actions?: React.ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <h1>{title}</h1>
        {description && <p>{description}</p>}
      </div>
      {actions && <div className="button-row">{actions}</div>}
    </div>
  );
}
export function paginationItem(
  _page: number,
  type: string,
  original: React.ReactNode,
) {
  return (type === "prev" || type === "next") && isValidElement(original)
    ? cloneElement(original as React.ReactElement<{ "aria-label": string }>, {
        "aria-label": type === "next" ? "下一页" : "上一页",
      })
    : original;
}
