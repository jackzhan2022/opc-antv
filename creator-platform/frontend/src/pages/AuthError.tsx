import { Result, Button } from "antd";
import { LOGIN_URL, PLATFORM_URL } from "../api";
export default function AuthError() {
  const code = new URLSearchParams(location.search).get("code");
  return (
    <section className="panel state-panel">
      <Result
        status={code === "forbidden" ? "403" : "warning"}
        title={code === "forbidden" ? "暂无访问权限" : "登录未完成"}
        subTitle={
          code === "forbidden"
            ? "请联系平台管理员，确认成员已启用并关联企业身份。"
            : "请重新通过统一平台登录后继续。"
        }
        extra={
          <div className="button-row">
            <Button type="primary" href={LOGIN_URL}>
              重新登录
            </Button>
            <Button href={PLATFORM_URL}>返回统一平台</Button>
          </div>
        }
      />
    </section>
  );
}
