import { test, expect, Page } from "@playwright/test";
type Entry = Record<string, any>;
async function stableScreenshot(page: Page, path: string) {
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect(page.locator(".ant-spin-spinning, .ant-spin-blur")).toHaveCount(
    0,
  );
  // Ant Spin fades the container and its ::after veil for 300ms after loading clears.
  await expect
    .poll(() =>
      page.locator(".ant-spin-container").evaluateAll((containers) =>
        containers.every((container) => {
          const content = getComputedStyle(container),
            veil = getComputedStyle(container, "::after");
          return Number(content.opacity) === 1 && Number(veil.opacity) === 0;
        }),
      ),
    )
    .toBe(true);
  await page.screenshot({ path, fullPage: true, animations: "disabled" });
}
async function fixture(page: Page, initial: Entry[] = []) {
  let records = initial.map((x) => ({ ...x }));
  const audit: Entry[] = [];
  const requests: Entry[] = [];
  await page.route("**/creator-api/**", async (route) => {
    const request = route.request(),
      url = new URL(request.url()),
      p = url.pathname.slice("/creator-api".length),
      method = request.method();
    const body = request.postDataJSON();
    requests.push({
      p,
      method,
      body,
      query: Object.fromEntries(url.searchParams),
    });
    const send = (data: any, status = 200) =>
      route.fulfill({ status, json: data });
    if (p === "/me")
      return send({ id: "me", displayName: "当前成员", role: "MEMBER" });
    if (p === "/auth/csrf")
      return send({ token: "test-csrf", headerName: "X-CSRF-TOKEN" });
    if (method !== "GET")
      expect(request.headers()["x-csrf-token"]).toBe("test-csrf");
    if (p === "/members")
      return send([
        { id: "me", displayName: "当前成员" },
        { id: "other", displayName: "其他成员" },
      ]);
    if (p === "/dictionaries") return send((url.searchParams.get("kind") === "PROFESSION" ? ["编剧", "剪辑"] : url.searchParams.get("kind") === "TAG" ? ["短剧"] : ["同事推荐"]).map((name, i) => ({ id: String(i), kind: url.searchParams.get("kind"), name, enabled: true })));
    const matchFilters = (x: Entry) => [...url.searchParams].every(([key, value]) => !value || !["q", "profession", "tag", "city", "maintainerId", "source"].includes(key) || (key === "q" ? x.name.includes(value) : key === "profession" ? x.professions.includes(value) : key === "tag" ? x.tags.includes(value) : x[key] === value));
    if (p === "/creators/filter-options") return send({ cities: [...new Set(records.map(x => x.city))], sources: [...new Set(records.map(x => x.source))] });
    if (p === "/creators/summary") {
      const filtered = records.filter(matchFilters);
      return send({ all: filtered.filter(x => !x.archived).length, recent: filtered.filter(x => !x.archived && new Date(x.updatedAt).getTime() >= Date.now() - 7 * 86400000).length, archived: filtered.filter(x => x.archived).length });
    }
    if (p === "/auth/logout") return route.fulfill({ status: 204 });
    if (p === "/creators" && method === "GET") {
      const q = url.searchParams;
      const filtered = records
        .filter(matchFilters)
        .filter(x => q.get("recent") !== "true" || new Date(x.updatedAt).getTime() >= Date.now() - 7 * 86400000)
        .filter((x) => String(x.archived) === q.get("archived"))
        .filter((x) => !q.get("q") || x.name.includes(q.get("q")))
        .filter(
          (x) =>
            !q.get("profession") || x.professions.includes(q.get("profession")),
        )
        .filter((x) => !q.get("tag") || x.tags.includes(q.get("tag")))
        .filter((x) => !q.get("city") || x.city === q.get("city"));
      const n = Number(q.get("page") || 1),
        size = Number(q.get("pageSize") || 20);
      return send({
        items: filtered.slice((n - 1) * size, n * size),
        total: filtered.length,
        page: n,
        pageSize: size,
      });
    }
    if (p === "/creators" && method === "POST") {
      const c = {
        ...body,
        id: "c1",
        maintainerName: body.maintainerId === "other" ? "其他成员" : "当前成员",
        archived: false,
        version: 1,
        createdAt: "2026-11-13T08:00:00Z",
        updatedAt: "2026-11-13T08:00:00Z",
      };
      records.push(c);
      audit.push({
        id: "a1",
        action: "CREATED",
        actorName: "当前成员",
        detail: "创建档案",
        createdAt: c.createdAt,
      });
      return send(c, 201);
    }
    const m = p.match(/^\/creators\/([^/]+)(?:\/(audit|archive|restore))?$/);
    const c = records.find((x) => x.id === m?.[1]);
    if (!c) return send({ code: "creator_not_found" }, 404);
    if (m?.[2] === "audit")
      return send({ items: audit, total: audit.length, page: 1, pageSize: 20 });
    if (method === "GET") return send(c);
    if (body.version !== c.version)
      return send({ code: "version_conflict" }, 409);
    if (method === "PUT") Object.assign(c, body);
    else c.archived = m?.[2] === "archive";
    c.version++;
    audit.push({
      id: String(audit.length + 1),
      action:
        method === "PUT" ? "UPDATED" : c.archived ? "ARCHIVED" : "RESTORED",
      actorName: "当前成员",
      detail:
        method === "PUT" ? "变更：城市" : c.archived ? "归档档案" : "恢复档案",
      createdAt: c.updatedAt,
    });
    return send(c);
  });
  return { requests };
}
const existing = {
  id: "c1",
  name: "李小川",
  phone: "13800000000",
  wechat: "test-wx",
  city: "杭州",
  professions: ["编剧"],
  tags: ["短剧"],
  source: "同事推荐",
  maintainerId: "me",
  maintainerName: "当前成员",
  works: [{ title: "作品一", url: "https://example.com/work" }],
  notes: "擅长人物对白",
  intro: "短剧编剧，关注人物关系。",
  quoteAmount: 1200,
  quoteUnit: "集",
  archived: false,
  version: 1,
  createdAt: "2026-11-13T08:00:00Z",
  updatedAt: "2026-11-13T08:00:00Z",
};

test("公司共享列表呈现真实空状态，可从表单创建、编辑、归档和恢复", async ({
  page,
}) => {
  const { requests } = await fixture(page);
  await page.goto("/creators/");
  await expect(page.getByText("暂无创作者")).toBeVisible();
  await page.getByRole("button", { name: "新增创作者", exact: true }).click();
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(page.getByText("请输入姓名")).toBeVisible();
  await expect(page.getByText("请选择至少一个职业")).toBeVisible();
  await page.getByLabel("姓名", { exact: true }).fill("李小川");
  await page.getByLabel("职业", { exact: true }).click();
  await page.getByRole("option", { name: "编剧", exact: true }).click();
  await page.keyboard.press("Escape");
  await page.getByLabel("城市", { exact: true }).fill("杭州");
  await page.getByLabel("手机", { exact: true }).fill("13800000000");
  await page.getByLabel("微信", { exact: true }).fill("test-wx");
  await page.getByRole("button", { name: "添加作品链接" }).click();
  await page.getByLabel("作品名称 1").fill("作品一");
  await page.getByLabel("作品链接 1").fill("https://example.com/work");
  await stableScreenshot(page, "test-results/creator-editor-desktop.png");
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(
    page.getByRole("heading", { name: "李小川", exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("link", { name: "作品一" })).toHaveAttribute(
    "rel",
    "noopener noreferrer",
  );
  await page.getByRole("button", { name: "编辑档案" }).click();
  await page.getByLabel("城市", { exact: true }).fill("上海");
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(page.getByRole("tabpanel", { name: "人员档案" }).getByText("上海", { exact: true })).toBeVisible();
  await page.getByRole("tab", { name: "修改记录" }).click();
  await expect(page.getByText("变更：城市", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "归档档案", exact: true }).click();
  await expect(
    page.getByText("归档后将保留资料和操作记录，恢复后可继续编辑。"),
  ).toBeVisible();
  await page.getByRole("button", { name: "确认归档" }).click();
  await expect(page.getByText("已归档", { exact: true }).first()).toBeVisible();
  await expect(page.getByRole("button", { name: "编辑档案" })).toHaveCount(0);
  await page.getByRole("button", { name: "恢复档案" }).click();
  await expect(page.getByRole("button", { name: "编辑档案" })).toBeVisible();
  expect(
    requests.find((x) => x.method === "POST" && x.p === "/creators")!.body
      .maintainerId,
  ).toBe("me");
  await stableScreenshot(page, "test-results/creator-audit-desktop.png");
  await page.getByRole("tab", { name: "人员档案" }).click();
  await stableScreenshot(page, "test-results/creator-detail-desktop.png");
});

test("筛选会提交职业、标签、城市与分页参数，键盘可以搜索", async ({ page }) => {
  const { requests } = await fixture(page, [
    existing,
    ...Array.from({ length: 21 }, (_, i) => ({
      ...existing,
      id: `x${i}`,
      name: `其他${i}`,
    })),
  ]);
  await page.goto("/creators/");
  await page.getByRole("button", { name: "下一页" }).click();
  await expect
    .poll(() => requests.filter((x) => x.p === "/creators").at(-1)?.query.page)
    .toBe("2");
  await page.getByLabel("搜索创作者").fill("李小川");
  await page.getByLabel("搜索创作者").press("Enter");
  await expect
    .poll(() => requests.filter((x) => x.p === "/creators").at(-1)?.query.q)
    .toBe("李小川");
  await page.getByLabel("筛选职业").fill("编剧");
  await page.getByRole("button", { name: "更多筛选" }).click();
  await page.getByLabel("筛选标签").fill("短剧");
  await page.getByLabel("筛选城市").fill("杭州");
  await page.getByRole("button", { name: "查询", exact: true }).click();
  await expect
    .poll(() => requests.filter((x) => x.p === "/creators").at(-1)?.query)
    .toMatchObject({
      profession: "编剧",
      tag: "短剧",
      city: "杭州",
      page: "1",
    });
  await page.getByRole("tab", { name: "已归档" }).click();
  await expect(page.getByText("暂无归档档案")).toBeVisible();
});

test("自定义职业也可以通过筛选查找", async ({ page }) => {
  const { requests } = await fixture(page, [
    { ...existing, professions: ["制片"] },
  ]);
  await page.goto("/creators/");
  await page.getByLabel("筛选职业").fill("制片");
  await page.getByRole("button", { name: "查询", exact: true }).click();
  await expect
    .poll(
      () =>
        requests.filter((x) => x.p === "/creators").at(-1)?.query.profession,
    )
    .toBe("制片");
  await expect(
    page.getByRole("link", { name: "李小川", exact: true }),
  ).toBeVisible();
});

test("维护人可选择启用成员，服务端拒绝保存时保留输入", async ({ page }) => {
  const { requests } = await fixture(page, [existing]);
  await page.goto("/creators/c1/edit");
  await page.getByLabel("维护人", { exact: true }).focus();
  await page.getByLabel("维护人", { exact: true }).press("ArrowDown");
  await page.getByRole("option", { name: "其他成员", exact: true }).click();
  for (const [code, status, message] of [
    ["maintainer_unavailable", 400, "维护人已停用或不可用，请重新选择。"],
    ["creator_archived", 409, "档案已归档，请恢复后编辑。"],
    ["forbidden", 403, "暂无访问权限，请联系平台管理员。"],
  ] as const) {
    await page.route("**/creator-api/creators/c1", (route) =>
      route.request().method() === "PUT"
        ? route.fulfill({ status, json: { code } })
        : route.fallback(),
    );
    await page.getByRole("button", { name: "保存档案" }).click();
    await expect(page.getByText(message, { exact: true })).toBeVisible();
    await expect(page.getByLabel("姓名", { exact: true })).toHaveValue(
      "李小川",
    );
  }
  expect(requests.some((x) => x.p === "/members")).toBeTruthy();
});

test("无权限无法访问列表；退出当前应用携带CSRF并回到统一平台", async ({
  page,
}) => {
  await fixture(page, [existing]);
  await page.route("**/creator-api/me", (route) =>
    route.fulfill({ status: 403, json: { code: "forbidden" } }),
  );
  await page.goto("/creators/");
  await expect(
    page.getByText("暂无访问权限，请联系平台管理员。"),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "新增创作者", exact: true }),
  ).toHaveCount(0);
  await page.unroute("**/creator-api/me");
  await page.getByRole("button", { name: "重试", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "李小川", exact: true }),
  ).toBeVisible();
  await page.route("http://127.0.0.1:8002/", (route) =>
    route.fulfill({ contentType: "text/html", body: "统一平台" }),
  );
  await page.getByRole("button", { name: "退出", exact: true }).click();
  await expect(page).toHaveURL("http://127.0.0.1:8002/");
});

test("375px详情、作品表单和归档状态均可使用键盘且不横向溢出", async ({
  page,
}) => {
  await page.setViewportSize({ width: 375, height: 812 });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await fixture(page, [existing]);
  await page.goto("/creators/c1");
  await expect(
    page.getByRole("heading", { name: "李小川", exact: true }),
  ).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBeTruthy();
  await stableScreenshot(page, "test-results/creator-detail-mobile.png");
  await page.getByRole("button", { name: "编辑档案" }).click();
  await expect(page.getByLabel("作品链接 1")).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBeTruthy();
  await page.getByLabel("姓名", { exact: true }).focus();
  await page.keyboard.press("Tab");
  await expect(page.getByLabel("手机", { exact: true })).toBeFocused();
  await stableScreenshot(page, "test-results/creator-editor-works-mobile.png");
  await page.route("**/creator-api/creators/c1", (route) =>
    route.fulfill({ json: { ...existing, archived: true } }),
  );
  await page.goto("/creators/c1/edit");
  await expect(page.getByRole("heading", { name: "档案已归档" })).toBeVisible();
  await expect(page.getByRole("button", { name: "保存档案" })).toHaveCount(0);
});

test("职业标签与作品数量边界避免无效提交", async ({ page }) => {
  await fixture(page, [
    {
      ...existing,
      professions: Array.from({ length: 20 }, (_, i) => `职业${i}`),
      tags: Array.from({ length: 50 }, (_, i) => `标签${i}`),
      works: Array.from({ length: 50 }, (_, i) => ({
        title: `作品${i}`,
        url: "https://example.com/" + i,
      })),
    },
  ]);
  await page.goto("/creators/c1/edit");
  await expect(
    page.getByRole("button", { name: "添加作品链接" }),
  ).toBeDisabled();
  await page.getByLabel("职业", { exact: true }).fill("超额职业");
  await page.getByLabel("职业", { exact: true }).press("Enter");
  await page.keyboard.press("Escape");
  await page.getByLabel("擅长方向", { exact: true }).fill("超额标签");
  await page.getByLabel("擅长方向", { exact: true }).press("Enter");
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(page.getByText("职业最多 20 项")).toBeVisible();
  await expect(page.getByText("标签最多 50 项")).toBeVisible();
});

test("并发冲突保留编辑内容并提示刷新；不合法作品链接不能提交", async ({
  page,
}) => {
  await fixture(page, [existing]);
  await page.goto("/creators/c1/edit");
  await page.getByLabel("作品链接 1").fill("javascript:alert(1)");
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(page.getByText("请输入 http 或 https 链接")).toBeVisible();
  await page.getByLabel("作品链接 1").fill("https://example.com/work");
  await page.getByLabel("姓名", { exact: true }).fill("李小川更新");
  await page.route("**/creator-api/creators/c1", async (route) =>
    route.request().method() === "PUT"
      ? route.fulfill({ status: 409, json: { code: "version_conflict" } })
      : route.fallback(),
  );
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(
    page.getByText("档案已被其他成员更新，请刷新后重试。"),
  ).toBeVisible();
  await expect(page.getByLabel("姓名", { exact: true })).toHaveValue(
    "李小川更新",
  );
});

test("匿名会跳转统一登录入口，状态页由用户主动重试", async ({ page }) => {
  await page.route("**/creator-api/me", (route) =>
    route.fulfill({ status: 401, json: { code: "unauthenticated" } }),
  );
  await page.route("**/creator-api/oauth2/authorization/sinx", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "统一平台登录",
    }),
  );
  await page.goto("/creators/");
  await expect(page).toHaveURL(/\/creator-api\/oauth2\/authorization\/sinx$/);
  await page.goto("/creators/auth-error?code=forbidden");
  await expect(page.getByText("暂无访问权限")).toBeVisible();
  await expect(page.getByRole("link", { name: "重新登录" })).toHaveAttribute(
    "href",
    "/creator-api/oauth2/authorization/sinx",
  );
});

test("服务错误、无权限和档案缺失显示明确状态，可重试；移动端不横向溢出", async ({
  page,
}) => {
  await fixture(page, [existing]);
  await page.route("**/creator-api/creators?*", (route) =>
    route.fulfill({ status: 503, json: { code: "platform_unavailable" } }),
  );
  await page.goto("/creators/");
  await expect(
    page.getByText("统一平台暂时不可用，请稍后重试。"),
  ).toBeVisible();
  await page.unroute("**/creator-api/creators?*");
  await page.getByRole("button", { name: "重试", exact: true }).click();
  await expect(
    page.getByRole("link", { name: "李小川", exact: true }),
  ).toBeVisible();
  await expect(page.locator(".ant-spin-spinning")).toHaveCount(0);
  await stableScreenshot(page, "test-results/creator-list-desktop.png");
  await page.setViewportSize({ width: 375, height: 812 });
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBeTruthy();
  await stableScreenshot(page, "test-results/creator-list-mobile.png");
  await page.getByRole("button", { name: "新增创作者", exact: true }).click();
  await expect(page.getByLabel("姓名", { exact: true })).toBeVisible();
  await expect
    .poll(() =>
      page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    )
    .toBeTruthy();
  await stableScreenshot(page, "test-results/creator-editor-mobile.png");
  await page.goto("/creators/missing");
  await expect(page.getByText("档案不存在或已被移除")).toBeVisible();
});

test("资源库恢复业务导航、真实统计和筛选详情返回上下文", async ({ page }) => {
  const { requests } = await fixture(page, [existing, { ...existing, id: "old", updatedAt: "2020-01-01T00:00:00Z" }, { ...existing, id: "archived", archived: true }]);
  await page.goto("/creators/?city=杭州&maintainerId=me&source=同事推荐&tab=recent&page=1");
  await expect(page.getByRole("navigation", { name: "业务导航" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "全部创作者 2" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "最近更新 1" })).toBeVisible();
  await expect(page.getByRole("tab", { name: "已归档 1" })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "联系方式" })).toBeVisible();
  await expect(page.getByText("档案 c1", { exact: true })).toBeVisible();
  await page.getByRole("link", { name: "查看", exact: true }).click();
  await expect(page.getByRole("heading", { name: "资料维护" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "参考报价" })).toBeVisible();
  await expect(page.getByText("1,200.00")).toBeVisible();
  await page.getByRole("button", { name: "编辑档案" }).click();
  await expect(page.getByRole("navigation", { name: "表单目录" })).toBeVisible();
  await page.getByRole("button", { name: "取消", exact: true }).click();
  await page.getByRole("link", { name: "返回创作者资源库" }).click();
  await expect(page).toHaveURL(/city=.*maintainerId=me.*source=.*tab=recent.*page=1/);
  expect(requests.some(x => x.p === "/creators" && x.query.recent === "true" && x.query.maintainerId === "me")).toBeTruthy();
  await stableScreenshot(page, "test-results/creator-restored-list-desktop.png");
});

test("375px导航抽屉支持键盘关闭，状态页不触发成员登录", async ({ page }) => {
  const { requests } = await fixture(page, [existing]);
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/creators/");
  const toggle = page.getByRole("button", { name: "展开导航" });
  await toggle.click();
  await expect(page.getByRole("dialog", { name: "业务导航" })).toBeVisible();
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog", { name: "业务导航" })).toBeHidden();
  await expect(toggle).toBeFocused();
  await stableScreenshot(page, "test-results/creator-restored-list-mobile.png");
  const count = requests.filter(x => x.p === "/me").length;
  await page.goto("/creators/auth-error?code=forbidden");
  await expect(page.getByText("暂无访问权限", { exact: true })).toBeVisible();
  expect(requests.filter(x => x.p === "/me").length).toBe(count);
});

test("简介报价遵循成对与金额校验且保存真实字段", async ({ page }) => {
  const { requests } = await fixture(page, [existing]);
  await page.goto("/creators/c1/edit");
  await page.getByLabel("个人简介").fill("新的个人简介");
  for (const amount of ["-1", "1.001", "10000000000"]) {
    await page.getByLabel("报价金额").fill(amount);
    await page.getByRole("button", { name: "保存档案" }).click();
    await expect(page.getByText("报价金额需为非负数，最多两位小数且不超过 9999999999.99")).toBeVisible();
  }
  await page.getByLabel("报价金额").fill("1250.50");
  await page.getByLabel("报价单位").selectOption("");
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(page.getByText("请同时填写报价金额与单位").first()).toBeVisible();
  await page.getByLabel("报价单位").selectOption("项目");
  await page.getByRole("button", { name: "保存档案" }).click();
  await expect(page.getByRole("heading", { name: "李小川", exact: true })).toBeVisible();
  expect([...requests].reverse().find(x => x.method === "PUT")?.body).toMatchObject({ intro: "新的个人简介", quoteAmount: 1250.5, quoteUnit: "项目" });
});

test("列表分页与人名关键词在详情编辑返回后保留在URL，不写入本地存储", async ({ page }) => {
  await fixture(page, Array.from({ length: 22 }, (_, i) => ({ ...existing, id: `record-${i}` })));
  await page.goto("/creators/?q=李小川&profession=编剧&tab=all&page=2");
  await expect(page.getByRole("link", { name: "查看", exact: true })).toHaveCount(2);
  await page.getByRole("link", { name: "查看", exact: true }).first().click();
  await page.getByRole("button", { name: "编辑档案" }).click();
  await page.getByRole("button", { name: "取消", exact: true }).click();
  await page.getByRole("link", { name: "返回创作者资源库" }).click();
  await expect(page).toHaveURL(/q=.*profession=.*tab=all&page=2/);
  await expect(page.getByLabel("搜索创作者")).toHaveValue("李小川");
  expect(await page.evaluate(() => Object.values(localStorage).join(" ").includes("李小川"))).toBe(false);
});

test("末页唯一档案归档后返回会保留筛选并退回有效页，避免假空列表", async ({ page }) => {
  const { requests } = await fixture(page, Array.from({ length: 21 }, (_, i) => ({ ...existing, id: `record-${i}` })));
  await page.goto("/creators/?q=李小川&profession=编剧&city=杭州&maintainerId=me&source=同事推荐&tab=all&page=2");
  await page.getByRole("link", { name: "查看", exact: true }).click();
  await page.getByRole("button", { name: "归档档案", exact: true }).click();
  await page.getByRole("button", { name: "确认归档" }).click();
  await expect(page.getByRole("button", { name: "恢复档案" })).toBeVisible();
  await page.getByRole("link", { name: "返回创作者资源库" }).click();
  await expect(page).toHaveURL(/q=.*profession=.*city=.*maintainerId=me&source=.*tab=all&page=1/);
  await expect(page.getByRole("link", { name: "查看", exact: true })).toHaveCount(20);
  expect(requests.filter(x => x.p === "/creators").at(-1)?.query).toMatchObject({ page: "1", q: "李小川", profession: "编剧", city: "杭州", maintainerId: "me", source: "同事推荐" });
});
