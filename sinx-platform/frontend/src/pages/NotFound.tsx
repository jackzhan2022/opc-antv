import { Result, Button } from 'antd';
import { history } from 'umi';
export default function NotFound(){ return <Result status="404" title="页面不存在" extra={<Button onClick={()=>history.replace('/workspace')}>返回工作台</Button>}/>; }
