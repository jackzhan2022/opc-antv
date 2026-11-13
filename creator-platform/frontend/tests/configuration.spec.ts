import { test, expect, Page } from "@playwright/test";
type Dictionary = {
  id: string;
  kind: string;
  name: string;
  category: string;
  description: string;
  enabled: boolean;
  version: number;
  usageCount: number;
};
const occupation: Dictionary = {
  id: "profession-1",
  kind: "PROFESSION",
  name: "编剧",
  category: "",
  description: "剧本与故事创作",
  enabled: true,
  version: 1,
  usageCount: 7,
};
const tag: Dictionary = {
  id: "tag-1",
  kind: "TAG",
  name: "短剧",
  category: "专业能力",
  description: "",
  enabled: true,
  version: 1,
  usageCount: 3,
};
const source: Dictionary = {
  id: "source-1",
  kind: "SOURCE",
  name: "同事推荐",
  category: "",
  description: "",
  enabled: true,
  version: 1,
  usageCount: 2,
};
async function fixture(
  page: Page,
  options: {
    role?: string;
    empty?: boolean;
    conflict?: boolean;
    failOnce?: boolean;
  } = {},
) {
  const entries = options.empty
    ? []
    : [occupation, tag, source].map((x) => ({ ...x }));
  const writes: any[] = [];
  let conflict = options.conflict,
    fail = options.failOnce;
  await page.route("**/creator-api/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      path = url.pathname.replace("/creator-api", ""),
      method = request.method();
    const send = (json: any, status = 200) => route.fulfill({ json, status });
    if (path === "/me")
      return send({
        id: "me",
        displayName: "当前成员",
        role: options.role || "ADMIN",
      });
    if (path === "/auth/csrf")
      return send({ token: "csrf", headerName: "X-CSRF-TOKEN" });
    if (path === "/dictionaries" && method === "GET") {
      if (fail) {
        fail = false;
        return send({ code: "network_error" }, 503);
      }
      expect(url.searchParams.get("includeDisabled")).toBe("true");
      return send(
        entries.filter((x) => x.kind === url.searchParams.get("kind")),
      );
    }
    if (path.startsWith("/dictionaries") && method !== "GET") {
      expect(request.headers()["x-csrf-token"]).toBe("csrf");
      const body = request.postDataJSON();
      writes.push({ path, method, body });
      if (conflict) {
        conflict = false;
        entries[0].version = 2;
        return send({ code: "version_conflict" }, 409);
      }
      if (body.name === "重复")
        return send({ code: "dictionary_name_exists" }, 409);
      if (path === "/dictionaries") {
        const created = {
          ...body,
          id: `new-${entries.length}`,
          usageCount: 0,
          version: 1,
        };
        entries.push(created);
        return send(created, 201);
      }
      const row = entries.find((x) => x.id === path.split("/").pop());
      if (!row) return send({ code: "dictionary_not_found" }, 404);
      Object.assign(row, body, { version: row.version + 1 });
      return send(row);
    }
    if (path === "/creators")
      return send({ items: [], total: 0, page: 1, pageSize: 20 });
    return send({ code: "not_found" }, 404);
  });
  return { entries, writes };
}

test("职业分类展示真实使用人数，可编辑说明和停用", async ({ page }) => {
  const { writes } = await fixture(page);
  await page.goto("/creators/occupations");
  await expect(
    page.getByRole("heading", { name: "职业分类", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: /编剧.*7 人/ })).toBeVisible();
  await expect(page.getByLabel("职业名称")).toHaveValue("编剧");
  await page.getByLabel("职业说明").fill("更新后的说明");
  await page.getByRole("switch", { name: "启用职业" }).click();
  await page.getByRole("button", { name: "保存职业" }).click();
  await expect(page.getByText("职业已保存")).toBeVisible();
  expect(writes[0].body).toMatchObject({
    description: "更新后的说明",
    enabled: false,
    version: 1,
  });
});

test("可新增职业，后端重名错误保留输入并可重试", async ({ page }) => {
  const { writes } = await fixture(page, { empty: true });
  await page.goto("/creators/occupations");
  await expect(page.getByText("暂无职业")).toBeVisible();
  await page.getByRole("button", { name: "新增职业" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("职业名称").fill("重复");
  await dialog.getByRole("button", { name: "创建职业" }).click();
  await expect(dialog.getByRole("alert")).toContainText("同类名称已存在");
  await expect(dialog.getByLabel("职业名称")).toHaveValue("重复");
  await dialog.getByLabel("职业名称").fill("配音");
  await dialog.getByRole("button", { name: "创建职业" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("button", { name: /配音.*0 人/ })).toBeVisible();
  expect(writes.at(-1).body.kind).toBe("PROFESSION");
});

test("职业版本冲突后刷新配置保留草稿并使用最新版本保存", async ({ page }) => {
  const { writes } = await fixture(page, { conflict: true });
  await page.goto("/creators/occupations");
  await page.getByLabel("职业说明").fill("保留的草稿");
  await page.getByRole("button", { name: "保存职业" }).click();
  await expect(page.getByRole("alert")).toContainText("其他成员更新");
  await page.getByRole("button", { name: "刷新配置" }).click();
  await expect(page.getByLabel("职业说明")).toHaveValue("保留的草稿");
  await page.getByRole("button", { name: "保存职业" }).click();
  await expect(page.getByText("职业已保存")).toBeVisible();
  expect(writes.at(-1).body.version).toBe(2);
});

test("标签按分类展示，支持新分类、编辑、启停和来源渠道", async ({ page }) => {
  const { writes } = await fixture(page);
  await page.goto("/creators/tags");
  await expect(
    page.getByRole("heading", { name: "技能标签", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("article", { name: "短剧" })).toContainText(
    "3 人",
  );
  await page.getByRole("button", { name: "编辑短剧" }).click();
  let dialog = page.getByRole("dialog");
  await dialog.getByLabel("标签说明").fill("短剧创作");
  await dialog.getByRole("switch", { name: "启用标签" }).click();
  await dialog.getByRole("button", { name: "保存标签" }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByRole("article", { name: "短剧" })).toContainText(
    "已停用",
  );
  await page.getByRole("button", { name: "新增标签", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("标签名称").fill("调色");
  await dialog.getByLabel("标签分类").fill("后期制作");
  await dialog.getByRole("button", { name: "创建标签" }).click();
  await expect(page.getByRole("button", { name: /后期制作/ })).toBeVisible();
  await page.getByRole("button", { name: /来源渠道/ }).click();
  await expect(page.getByRole("article", { name: "同事推荐" })).toContainText(
    "2 人",
  );
  await page.getByRole("button", { name: "新增来源", exact: true }).click();
  dialog = page.getByRole("dialog");
  await dialog.getByLabel("来源名称").fill("作品平台");
  await dialog.getByRole("button", { name: "创建来源" }).click();
  await expect(page.getByRole("article", { name: "作品平台" })).toBeVisible();
  expect(writes.at(-1).body.kind).toBe("SOURCE");
});

test("配置接口加载失败可在页面内重试", async ({ page }) => {
  await fixture(page, { failOnce: true });
  await page.goto("/creators/occupations");
  await page.getByRole("button", { name: "重试", exact: true }).click();
  await expect(page.getByLabel("职业名称")).toHaveValue("编剧");
});

test("权限页展示真实内置策略并链接统一平台成员管理", async ({ page }) => {
  await fixture(page);
  await page.goto("/creators/roles");
  await expect(
    page.getByRole("heading", { name: "角色权限", exact: true }),
  ).toBeVisible();
  await expect(page.getByText("ADMIN", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: /普通成员/ }).click();
  await expect(page.getByText("MEMBER", { exact: true })).toBeVisible();
  await expect(
    page.getByText("所有启用的内部成员均可维护公司共享档案。"),
  ).toBeVisible();
  await expect(page.getByRole("checkbox")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "保存权限" })).toHaveCount(0);
  await expect(
    page.getByRole("link", { name: "管理成员与身份" }),
  ).toHaveAttribute("href", "/members");
});

for (const path of ["occupations", "tags", "roles"]) {
  test(`普通成员直接访问${path}显示403`, async ({ page }) => {
    const { writes } = await fixture(page, { role: "MEMBER" });
    await page.goto(`/creators/${path}`);
    await expect(page.getByText("403", { exact: true })).toBeVisible();
    await expect(page.getByText("仅管理员可访问此页面")).toBeVisible();
    expect(writes).toHaveLength(0);
  });
}

test("减少动态效果下新增职业和标签弹窗仍清晰可见", async ({ page }) => {
  await fixture(page);
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 375, height: 812 });
  for (const [path, noun] of [
    ["occupations", "职业"],
    ["tags", "标签"],
  ]) {
    await page.goto(`/creators/${path}`);
    await page
      .getByRole("button", { name: `新增${noun}`, exact: true })
      .click();
    const modal = page.locator(".ant-modal");
    await expect(modal).toHaveCSS("opacity", "1");
    await expect(
      page.getByRole("dialog").getByLabel(`${noun}名称`),
    ).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= window.innerWidth,
      ),
    ).toBe(true);
  }
});
