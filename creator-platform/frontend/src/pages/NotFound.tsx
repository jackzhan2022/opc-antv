import { Result } from "antd";
import { Link } from "umi";
export default function NotFound() {
  return (
    <Result
      status="404"
      title="页面不存在"
      extra={<Link to="/">返回创作者列表</Link>}
    />
  );
}
