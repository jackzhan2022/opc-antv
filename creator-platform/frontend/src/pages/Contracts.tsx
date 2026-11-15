import { Link } from "umi";
import ContractRecords from "../ContractRecords";
import { PageHeading } from "../components";
export default function Contracts() {
  return <><PageHeading title="合同管理" description="与创作者的合作合同和签署记录" actions={<Link className="ant-btn ant-btn-default" to="/">选择创作者拟定合同</Link>}/><section className="panel contract-panel"><ContractRecords/></section></>;
}
