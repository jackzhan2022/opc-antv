import { api, ApiError, LOGIN_URL } from "./api";
export interface ContractField { id: string; name: string; type: string; required: boolean }
export interface ContractTemplate { id: string; name: string; filename: string; fileType: string; remoteId: string; enabled: boolean; version: number; fields: ContractField[]; createdAt: string }
export interface CreatorContract { id: string; creatorId: string; templateId: string; templateName: string; title: string; legalName: string; mobile: string; fields: Record<string,string>; fieldDefinitions: ContractField[]; status: string; flowId: string; deadline: number; version: number; archived: boolean; errorCode: string; createdAt: string; updatedAt: string }
export interface Integration { ready: boolean; environment: string; companyName: string }
export const contractStates: Record<string,string> = { DRAFT: "草稿", PREPARING: "正在生成", FILLING: "待填写", SIGNING: "签署中", SIGNED: "已签署", REJECTED: "已拒绝", EXPIRED: "已过期", CANCELLED: "已撤销", INVALID: "已失效", RELEASED: "已解除" };
export function externalUrl(value: string) {
  try { const u = new URL(value); if (u.protocol === "https:" && !u.username && !u.password && ["qian.tencent.cn", "qian.tencent.com", "ess.tencent.cn"].some(host => u.hostname === host || u.hostname.endsWith("." + host))) return u.href; } catch {}
  throw new ApiError("ess_unavailable");
}
export async function downloadContractFile(path: string, name: string) {
  const response = await fetch("/creator-api" + path, { credentials: "same-origin" });
  if (!response.ok) { if (response.status === 401) window.location.assign(LOGIN_URL); throw new ApiError("ess_unavailable", response.status); }
  const url = URL.createObjectURL(await response.blob()), link = document.createElement("a");
  link.href = url; link.download = name; link.click(); setTimeout(() => URL.revokeObjectURL(url), 1000);
}
export const integration = () => api<Integration>("/contracts/integration");
