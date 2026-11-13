import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import type { ReactNode } from 'react';
export function rootContainer(container: ReactNode) {
 return <ConfigProvider button={{autoInsertSpace:false}} locale={zhCN} theme={{token:{colorPrimary:'#285bd4',colorText:'#202735',colorTextSecondary:'#687384',borderRadius:8,fontFamily:'Inter, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',controlHeight:44,fontSize:14}}}>{container}</ConfigProvider>;
}
