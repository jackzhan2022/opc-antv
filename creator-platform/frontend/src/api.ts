export interface Member {
  id: string;
  displayName: string;
  role?: string;
}
export interface Work {
  title: string;
  url: string;
}
export interface CreatorInput {
  name: string;
  phone: string;
  wechat: string;
  city: string;
  professions: string[];
  tags: string[];
  source: string;
  maintainerId: string;
  works: Work[];
  notes: string;
  intro: string;
  quoteAmount: number | null;
  quoteUnit: string;
}
export interface Creator extends CreatorInput {
  id: string;
  maintainerName: string;
  archived: boolean;
  version: number;
  createdAt: string;
  updatedAt: string;
}
export interface PageData<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}
export interface Audit {
  id: string;
  actorName: string;
  action: "CREATED" | "UPDATED" | "ARCHIVED" | "RESTORED";
  detail: string;
  createdAt: string;
}
export interface DictionaryOption {
  id: string;
  kind: "PROFESSION" | "TAG" | "SOURCE";
  name: string;
  enabled: boolean;
}
export interface CreatorSummary { all: number; recent: number; archived: number }
export interface CreatorFilterOptions { cities: string[]; sources: string[] }
export function registryReturn(search: string) {
  const target = new URLSearchParams(search).get("returnTo");
  return target && (target === "/" || target.startsWith("/?")) ? target : "/";
}
export function contextualPath(path: string, returnTo: string) {
  return `${path}?${new URLSearchParams({ returnTo })}`;
}
export const shortCreatorId = (id: string) => id.length > 12 ? `${id.slice(0, 8)}…` : id;
export const LOGIN_URL = "/creator-api/oauth2/authorization/sinx";
export const PLATFORM_URL = process.env.SINX_PLATFORM_URL || "/";
const errors: Record<string, string> = {
  unauthenticated: "登录已失效，正在前往统一平台。",
  forbidden: "暂无访问权限，请联系平台管理员。",
  invalid_request: "资料格式有误，请检查后重试。",
  creator_not_found: "档案不存在或已被移除",
  version_conflict: "档案已被其他成员更新，请刷新后重试。",
  creator_archived: "档案已归档，请恢复后编辑。",
  maintainer_unavailable: "维护人已停用或不可用，请重新选择。",
  platform_unavailable: "统一平台暂时不可用，请稍后重试。",
  network_error: "网络连接失败，请重试。",
};
export class ApiError extends Error {
  constructor(
    public code: string,
    public status = 0,
  ) {
    super(errors[code] || "操作未完成，请稍后重试。");
  }
}
export async function api<T>(
  path: string,
  method = "GET",
  body?: unknown,
): Promise<T> {
  try {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (method !== "GET") {
      const csrf = await api<{ token: string; headerName: string }>(
        "/auth/csrf",
      );
      headers[csrf.headerName] = csrf.token;
      headers["Content-Type"] = "application/json";
    }
    const response = await fetch("/creator-api" + path, {
      method,
      credentials: "same-origin",
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    if (!response.ok) {
      let code = "unknown";
      try {
        code = (await response.json()).code || code;
      } catch {}
      if (response.status === 401) {
        window.location.assign(LOGIN_URL);
        code = "unauthenticated";
      }
      throw new ApiError(code, response.status);
    }
    return response.status === 204 ? (undefined as T) : await response.json();
  } catch (error) {
    if (error instanceof ApiError) throw error;
    throw new ApiError("network_error");
  }
}
export const errorMessage = (error: unknown) =>
  error instanceof Error ? error.message : "操作未完成，请稍后重试。";
export const professions = [
  "编剧",
  "剪辑",
  "导演",
  "摄影",
  "演员",
  "配音",
  "其他",
];
export const dateTime = (date: string) =>
  new Intl.DateTimeFormat("zh-CN", {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(new Date(date));
