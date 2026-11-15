import { useEffect, useState } from "react";
import { Button, ConfigProvider, Drawer } from "antd";
import zhCN from "antd/locale/zh_CN";
import { Link, Outlet, useLocation } from "umi";
import { api, Member, PLATFORM_URL } from "../api";
import { ErrorNotice, ErrorState, Loading, MemberContext } from "../components";

function NavIcon({ kind }: { kind: string }) {
  return <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {kind === "people" ? <><circle cx="9" cy="7" r="3"/><path d="M3 20v-3a6 6 0 0 1 12 0v3M16 4a3 3 0 0 1 0 6M21 20v-3a6 6 0 0 0-3-5"/></> : kind === "work" ? <><rect x="3" y="7" width="18" height="14" rx="2"/><path d="M8 7V3h8v4M3 12c6 3 12 3 18 0M12 12v4"/></> : kind === "tag" ? <><path d="m3 3 8 0 10 10-8 8L3 11Z"/><circle cx="7" cy="7" r="1"/></> : <><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/><path d="m8 12 3 3 5-6"/></>}
  </svg>;
}

export default function Layout() {
  const location = useLocation(), isState = location.pathname === "/auth-error";
  const [member, setMember] = useState<Member | null>(null), [error, setError] = useState<unknown>(), [retry, setRetry] = useState(0), [busy, setBusy] = useState(false), [navigationOpen, setNavigationOpen] = useState(false);
  const titles: Record<string, string> = { "/": "创作者资源库", "/occupations": "职业分类", "/tags": "技能标签", "/roles": "角色权限", "/new": "新增创作者", "/auth-error": "访问状态", "/contracts": "合同管理", "/contract-templates": "合同模板" };
  const title = titles[location.pathname] || (location.pathname.endsWith("/edit") ? "编辑档案" : "创作者详情");
  useEffect(() => { setNavigationOpen(false); }, [location.pathname]);
  useEffect(() => {
    if (isState) return;
    let active = true;
    setError(undefined);
    api<Member>("/me").then(x => { if (active) setMember(x); }).catch(e => { if (active) setError(e); });
    return () => { active = false; };
  }, [isState, retry]);
  async function logout() {
    setBusy(true);
    try { await api("/auth/logout", "POST"); window.location.assign(PLATFORM_URL); }
    catch (e) { setError(e); setBusy(false); }
  }
  const isAdmin = member?.role === "ADMIN";
  function item(path: string, label: string, icon: string) {
    const active = path === "/" ? !["/occupations", "/tags", "/roles", "/auth-error", "/contracts", "/contract-templates"].includes(location.pathname) : location.pathname === path;
    return <Link to={path} className={`nav-item${active ? " active" : ""}`} aria-current={active ? "page" : undefined} onClick={() => setNavigationOpen(false)}><NavIcon kind={icon}/><span>{label}</span></Link>;
  }
  const navigation = <>
    <Link className="brand" to="/" aria-label="创作者平台首页"><span className="brand-symbol"><NavIcon kind="people"/></span><span>创作者管理<small>CREATOR HUB</small></span></Link>
    <nav aria-label="业务导航"><div className="nav-group"><p className="nav-label">资源管理</p>{item("/", "创作者资源库", "people")}{item("/contracts", "合同管理", "work")}{item("/contract-templates", "合同模板", "work")}</div>{isAdmin && <><div className="nav-group"><p className="nav-label">基础配置</p>{item("/occupations", "职业分类", "work")}{item("/tags", "技能标签", "tag")}</div><div className="nav-group"><p className="nav-label">系统设置</p>{item("/roles", "角色权限", "shield")}</div></>}</nav>
    <div className="sidebar-foot"><a href={PLATFORM_URL}>← <span>返回信计 X 平台</span></a></div>
  </>;
  return <ConfigProvider button={{ autoInsertSpace: false }} locale={zhCN} theme={{ token: { colorPrimary: "#2563eb", borderRadius: 6, fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif', colorText: "#263f61", colorTextSecondary: "#60718a", colorBorder: "#dce4ef", controlHeight: 36, fontSize: 13 }, components: { Table: { headerBg: "#f8fafc", cellPaddingBlock: 16, fontSize: 12 }, Button: { fontWeight: 500 } } }}>
    <div className={`app-shell${isState ? " state-shell" : ""}`}>
      <a className="skip-link" href="#main-content">跳转到主要内容</a>
      {!isState && <aside className="business-sidebar">{navigation}</aside>}
      <div className="workspace-shell">
        <header className="topbar"><div className="topbar-inner"><div className="breadcrumb"><Button className="mobile-toggle" type="text" aria-label="展开导航" aria-expanded={navigationOpen} onClick={() => setNavigationOpen(true)}><svg className="nav-icon" viewBox="0 0 24 24" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16"/></svg></Button><span className="crumb-root">创作者管理 <span className="crumb-divider">/</span></span><span>{title}</span></div>{member && !isState && <div className="member-menu"><span className="avatar member-avatar" aria-hidden="true">{member.displayName.slice(-2)}</span><div><span className="member-name">{member.displayName}</span><small className="member-role">{isAdmin ? "管理员" : "成员"}</small></div><Button type="text" loading={busy} onClick={logout}>退出</Button></div>}</div></header>
        <main id="main-content" tabIndex={-1} className="main-content">{isState ? <Outlet/> : member ? <MemberContext.Provider value={member}>{!!error && <div className="notice"><ErrorNotice error={error}/></div>}<Outlet/></MemberContext.Provider> : error ? <ErrorState error={error} retry={() => setRetry(x => x + 1)}/> : <Loading/>}</main>
      </div>
      {!isState && <Drawer title="业务导航" placement="left" width={260} open={navigationOpen} onClose={() => setNavigationOpen(false)} className="navigation-drawer" keyboard>{navigation}</Drawer>}
    </div>
  </ConfigProvider>;
}
