import { test, expect, Page } from "@playwright/test";
const creator = { id: "creator-1", name: "创作昵称", phone: "13800138000", wechat: "", city: "杭州", source: "推荐", professions: ["编剧"], tags: [], works: [], notes: "", intro: "", quoteAmount: null, quoteUnit: "", maintainerId: "me", maintainerName: "维护人", archived: false, version: 1, createdAt: "2026-11-14T00:00:00Z", updatedAt: "2026-11-14T00:00:00Z" };
const template = { id: "t1", name: "编剧合作模板", filename: "合作模板.docx", fileType: "docx", remoteId: "remote-template", enabled: true, version: 2, fields: [{ id: "fee", name: "合同金额", type: "TEXT", required: true }], createdAt: "2026-11-14T00:00:00Z" };
const contract = { id: "contract-1", creatorId: creator.id, templateId: template.id, templateName: template.name, title: "编剧创作合同", legalName: "签约实名", mobile: "13800138000", fields: { fee: "5000" }, fieldDefinitions: template.fields, status: "DRAFT", flowId: "", deadline: Math.floor(Date.now()/1000)+86400, version: 1, archived: false, errorCode: "", createdAt: "2026-11-14T00:00:00Z", updatedAt: "2026-11-14T00:00:00Z" };
async function fixture(page: Page, options: { role?: string; ready?: boolean; existing?: boolean } = {}) {
  let rows: any[] = options.existing ? [{ ...contract }] : [], templates: any[] = [{ ...template }];
  const writes: { path: string; body: any }[] = [];
  await page.route("**/creator-api/**", async route => {
    const request = route.request(), u = new URL(request.url()), path = u.pathname.replace("/creator-api", ""), method = request.method();
    const send = (json: unknown, status = 200) => route.fulfill({ json, status });
    if (path === "/me") return send({ id: "me", displayName: "当前成员", role: options.role || "ADMIN" });
    if (path === "/auth/csrf") return send({ token: "csrf", headerName: "X-CSRF-TOKEN" });
    if (method !== "GET") {
      expect(request.headers()["x-csrf-token"]).toBe("csrf");
      writes.push({ path, body: request.headers()["content-type"]?.includes("multipart") ? request.postData() : request.postDataJSON() });
    }
    if (path === "/contracts/integration") return send({ ready: options.ready !== false, environment: "test", companyName: "签约公司" });
    if (path === "/creators/creator-1") return send(creator);
    if (path === "/contract-templates" && method === "GET") return send(templates);
    if (path === "/contract-templates" && method === "POST") {
      const created = { ...template, id: "imported", name: "新的合作模板", remoteId: "", enabled: false, version: 1 }; templates.push(created); return send(created, 201);
    }
    if (path === "/contracts" && method === "GET") return send({ items: rows, total: rows.length, page: 1, pageSize: 20 });
    if (path === "/contracts" && method === "POST") { const row = { ...contract, ...request.postDataJSON() }; rows.push(row); return send(row, 201); }
    if (path.endsWith("/audit")) return send([{ id: "a1", actorName: "当前成员", action: "DRAFT_CREATED", createdAt: contract.createdAt }]);
    if (path.endsWith("/launch")) { rows[0] = { ...rows[0], status: "PREPARING", flowId: "flow-1", version: 2 }; return send(rows[0]); }
    if (path.endsWith("/sync")) { rows[0] = { ...rows[0], status: "SIGNED", version: 3 }; return send(rows[0]); }
    if (path.endsWith("/archive")) { rows[0].archived = true; return send(rows[0]); }
    if (path.endsWith("/draft-preview")) return send({ url: "https://qian.tencent.cn/preview" });
    return send({ code: "not_found" }, 404);
  });
  return writes;
}
test("管理员导入Word和PDF原文件，未配置电子签时不伪装为可用模板", async ({ page }) => {
  const writes = await fixture(page, { ready: false });
  await page.goto("/creators/contract-templates");
  await expect(page.getByText("电子签尚未配置", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "导入模板", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("模板名称").fill("新的合作模板");
  await dialog.getByLabel("模板原文件").setInputFiles({ name: "合作模板.docx", mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document", buffer: Buffer.from("test document upload fixture") });
  await dialog.getByRole("button", { name: "导入并保存原文件" }).click();
  await expect(page.getByText("待配置", { exact: true })).toBeVisible();
  expect(writes[0].body).toContain("合作模板.docx");
  await page.getByRole("button", { name: "导入模板", exact: true }).click();
  await dialog.getByLabel("模板名称").fill("新的合作模板");
  await dialog.getByLabel("模板原文件").setInputFiles({ name: "合作模板.pdf", mimeType: "application/pdf", buffer: Buffer.from("%PDF-1.4") });
  await dialog.getByRole("button", { name: "导入并保存原文件" }).click();
  await expect(dialog).toBeHidden();
  expect(writes[1].body).toContain("合作模板.pdf");
});
test("从创作者档案拟定合同，实名与金额需确认，普通成员保存后不显示发起按钮", async ({ page }) => {
  const writes = await fixture(page, { role: "MEMBER" });
  await page.goto("/creators/creator-1?tab=contracts");
  await page.getByRole("button", { name: "拟定合同", exact: true }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByLabel("合同模板", { exact: true }).click(); await page.getByText(template.name, { exact: true }).last().click();
  await dialog.getByLabel("创作者签约实名姓名").fill("签约实名");
  await dialog.getByLabel("合同金额").fill("5000");
  await dialog.getByRole("button", { name: "保存合同草稿" }).click();
  await expect(dialog.getByText("请先核对签约身份和填写内容")).toBeVisible();
  expect(writes).toHaveLength(0);
  await dialog.getByRole("checkbox").check();
  await dialog.getByRole("button", { name: "保存合同草稿" }).click();
  await expect(dialog).toBeHidden();
  expect(writes[0].body).toMatchObject({ creatorId: creator.id, legalName: "签约实名", mobile: creator.phone, fields: { fee: "5000" } });
  await expect(page.getByRole("button", { name: "发起签署", exact: true })).toHaveCount(0);
  await expect(page.getByText("草稿", { exact: true })).toBeVisible();
});
test("管理员二次确认发起，生成中可同步到完成并归档", async ({ page }) => {
  const writes = await fixture(page, { existing: true });
  await page.goto("/creators/contracts");
  await page.getByRole("button", { name: "发起签署", exact: true }).click();
  const confirm = page.getByRole("dialog");
  await expect(confirm).toContainText("签约实名"); await expect(confirm).toContainText("消耗电子签合同份额");
  expect(writes).toHaveLength(0);
  await confirm.getByRole("button", { name: "确认并发起签署" }).click();
  await expect(page.getByText("正在生成", { exact: true })).toBeVisible();
  expect(writes[0]).toMatchObject({ path: "/contracts/contract-1/launch", body: { version: 1 } });
  await page.getByRole("button", { name: "同步状态", exact: true }).click();
  await expect(page.getByText("已签署", { exact: true })).toBeVisible();
  await page.getByRole("button", { name: "归档签署文件", exact: true }).click();
  await expect(page.getByRole("button", { name: "下载合同", exact: true })).toBeVisible();
});
test("成员可查模板但不能导入；移动端合同导航与空状态可用", async ({ page }) => {
  await fixture(page, { role: "MEMBER", ready: false });
  await page.setViewportSize({ width: 375, height: 812 });
  await page.goto("/creators/contract-templates");
  await expect(page.getByRole("button", { name: "导入模板", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "配置模板", exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.goto("/creators/contracts");
  await expect(page.getByText("暂无合同记录", { exact: true })).toBeVisible();
  await page.screenshot({ path: "artifacts/contracts-mobile.png", fullPage: true, animations: "disabled" });
});
test("桌面合同页面显示真实状态与操作", async ({ page }) => {
  await fixture(page, { existing: true });
  await page.goto("/creators/contracts");
  await expect(page.getByText("草稿", { exact: true })).toBeVisible();
  await expect(page.locator(".ant-spin-spinning")).toHaveCount(0);
  await page.screenshot({ path: "artifacts/contracts-desktop.png", fullPage: true, animations: "disabled" });
});
