import { useCallback, useEffect, useRef, useState } from "react";
import { api, ApiError, errorMessage, PLATFORM_URL } from "./api";
export type DictionaryKind = "PROFESSION" | "TAG" | "SOURCE";
export interface DictionaryEntry {
  id: string;
  kind: DictionaryKind;
  name: string;
  category: string;
  description: string;
  enabled: boolean;
  version: number;
  usageCount: number;
}
export interface DictionaryInput {
  kind: DictionaryKind;
  name: string;
  category: string;
  description: string;
  enabled: boolean;
  version?: number;
}
export function listDictionaries(
  kind: DictionaryKind,
  includeDisabled = false,
) {
  return api<DictionaryEntry[]>(
    `/dictionaries?kind=${kind}&includeDisabled=${includeDisabled}`,
  );
}
export function saveDictionary(input: DictionaryInput, id?: string) {
  const body = {
    ...input,
    name: input.name.trim(),
    category: input.category.trim(),
    description: input.description.trim(),
  };
  return api<DictionaryEntry>(
    id ? `/dictionaries/${encodeURIComponent(id)}` : "/dictionaries",
    id ? "PATCH" : "POST",
    body,
  );
}
export function configurationError(error: unknown): string {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      dictionary_name_exists: "同类名称已存在，请使用其他名称。",
      dictionary_in_use: "此名称已被档案使用，无法改名；仍可修改说明或停用。",
      dictionary_not_found: "此配置已不存在，请刷新配置。",
      version_conflict:
        "配置已被其他成员更新，请刷新配置后重试，当前编辑内容会保留。",
      invalid_request: "配置格式有误，请检查名称、分类和说明。",
    };
    return messages[error.code] || errorMessage(error);
  }
  return errorMessage(error);
}
export function needsConfigurationRefresh(error: unknown) {
  return (
    error instanceof ApiError &&
    ["version_conflict", "dictionary_not_found"].includes(error.code)
  );
}
function platformMembersUrl() {
  const base = PLATFORM_URL.endsWith("/") ? PLATFORM_URL : PLATFORM_URL + "/";
  const url = new URL("members", new URL(base, window.location.origin));
  return url.origin === window.location.origin ? url.pathname : url.toString();
}
export const PLATFORM_MEMBERS_URL = platformMembersUrl();
export function useDictionaries(
  kinds: readonly DictionaryKind[],
  enabled: boolean,
) {
  const [entries, setEntries] = useState<DictionaryEntry[]>([]);
  const [loading, setLoading] = useState(enabled);
  const [error, setError] = useState<unknown>();
  const request = useRef(0);
  const kindsKey = kinds.join(",");
  const reload = useCallback(async () => {
    if (!enabled) return undefined;
    const sequence = ++request.current;
    setLoading(true);
    setError(undefined);
    try {
      const groups = await Promise.all(
        kindsKey
          .split(",")
          .map((kind) => listDictionaries(kind as DictionaryKind, true)),
      );
      const result = groups.flat();
      if (sequence === request.current) setEntries(result);
      return result;
    } catch (e) {
      if (sequence === request.current) setError(e);
      return undefined;
    } finally {
      if (sequence === request.current) setLoading(false);
    }
  }, [enabled, kindsKey]);
  useEffect(() => {
    void reload();
    return () => {
      request.current++;
    };
  }, [reload]);
  return { entries, loading, error, reload };
}
