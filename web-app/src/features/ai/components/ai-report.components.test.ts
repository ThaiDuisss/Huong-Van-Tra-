import { describe, expect, it, vi } from "vitest";
import { createElement as h, type ReactNode } from "react";
import { renderToStaticMarkup } from "react-dom/server";
vi.mock("../../../api/api-client",()=>({apiClient:{}}));
vi.mock("../../../components/ui/dialog",()=>({
  Dialog:({children,open}:{children:ReactNode;open:boolean})=>open?children:null,
  DialogContent:({children}:{children:ReactNode})=>h("div",{role:"dialog"},children),
  DialogDescription:({children}:{children:ReactNode})=>h("p",{},children),
  DialogFooter:({children}:{children:ReactNode})=>h("footer",{},children),
  DialogHeader:({children}:{children:ReactNode})=>h("header",{},children),
  DialogTitle:({children}:{children:ReactNode})=>h("h2",{},children),
}));
import { AiReportTable, type AiReportListState } from "./AiReportTable";
import { AiReportFormDialog } from "./AiReportFormDialog";
import { AiReportsPage } from "../pages/AiReportsPage";
import type { AiReport, AiReportPage } from "../types/ai-report.types";
import { AuthContext, type AuthContextValue } from "../../../auth/auth-context";
import { ProtectedRoute } from "../../../auth/ProtectedRoute";
import { AI_REPORT_ROLES } from "../../../auth/role-utils";
import { Pagination } from "../../manufacturing/components/shared/Pagination";

const record:AiReport={id:"report",name:"Báo cáo mẫu",version:"V1",fileId:"file",fileName:"Báo cáo gốc.pdf",
  fileType:"PDF",createdBy:"creator",updatedBy:null,createdAt:"2026-10-12T00:00:00Z",updatedAt:"2026-10-12T01:00:00Z"};
const page:AiReportPage={content:[record],page:0,size:20,totalElements:1,totalPages:1,first:true,last:true};
function table(state:AiReportListState,busy=false){
  return renderToStaticMarkup(h(AiReportTable,{state,busy,fileActionsAvailable:true,selectedId:null,
    onSelect:()=>{},onView:()=>{},onExport:()=>{},onEdit:()=>{},onRetry:()=>{}}));
}
function auth(roles:string[],authenticated=true):AuthContextValue {
  return {initialized:true,authenticated,roles,keycloakSubject:"owner",login:()=>{},register:()=>{},
    logout:async()=>{},hasRealmRole:(role)=>roles.includes(role),hasResourceRole:()=>false};
}

describe("existing AI Report screen render states",()=>{
  it("renders busy loading state without fake report rows",()=>{
    const html=table({status:"loading"});expect(html).toContain('aria-busy="true"');expect(html).not.toContain(record.name);
  });
  it("renders empty state after real empty page",()=>{
    const html=table({status:"ready",reports:[],page:{...page,content:[],totalElements:0,totalPages:0}});
    expect(html).toContain("Chưa có dữ liệu báo cáo");expect(html).not.toContain("chờ kết nối backend");
  });
  it("renders error and refresh action",()=>{expect(table({status:"error",message:"Không thể tải danh sách"})).toContain("Không thể tải danh sách");});
  it("shows supported original metadata and labels download honestly",()=>{
    const html=table({status:"ready",reports:[record],page});
    expect(html).toContain(record.name);expect(html).toContain(record.fileName);expect(html).toContain("PDF");
    expect(html).toContain("Tải bản gốc");expect(html).not.toContain(">Xuất<");expect(html).toContain(record.createdAt);
  });
  it("disables file actions during download",()=>{
    const html=table({status:"ready",reports:[record],page},true);expect(html.match(/disabled=""/g)?.length).toBeGreaterThanOrEqual(4);
  });
  it("uses server total and 1-based pagination UI",()=>{
    const html=renderToStaticMarkup(h(Pagination,{page:2,pageCount:3,totalItems:45,pageSize:20,totalItemsLabel:"Tổng số báo cáo",
      onPageChange:()=>{},onPageSizeChange:()=>{}}));expect(html).toContain("Tổng số báo cáo");expect(html).toContain("45");
  });
  it("keeps metadata readonly for same-intent retry but leaves retry enabled",()=>{
    const html=renderToStaticMarkup(h(AiReportFormDialog,{title:"Nhập báo cáo",description:"File gốc",submitLabel:"Thử lại nhập báo cáo",
      locked:true,onClose:()=>{},onSubmit:async()=>{},initialValues:{name:"Report",version:"V1"}}));
    expect(html).toContain('value="Report"');expect(html.match(/disabled=""/g)).toHaveLength(2);
    expect(html).toContain("Thử lại nhập báo cáo");expect(html).not.toContain("chờ kết nối backend");
  });
  it("permanent failures disable submit without pretending backend wiring is absent",()=>{
    const html=renderToStaticMarkup(h(AiReportFormDialog,{title:"Nhập",description:"File gốc",submitLabel:"Thử lại",submitDisabled:true,
      onClose:()=>{},onSubmit:async()=>{}}));expect(html).toContain('disabled=""');expect(html).not.toContain("chờ kết nối backend");
  });
  it("default connected page starts in loading state with the existing auth context",()=>{
    const html=renderToStaticMarkup(h(AuthContext.Provider,{value:auth(["SALE_STAFF"])},h(AiReportsPage)));
    expect(html).toContain("Nhập báo cáo");expect(html).toContain("Tải bản gốc");expect(html).toContain('aria-busy="true"');
  });
  it.each(AI_REPORT_ROLES)("retains allowed role %s",(role)=>{
    const html=renderToStaticMarkup(h(AuthContext.Provider,{value:auth([role])},
      h(ProtectedRoute,{roles:AI_REPORT_ROLES,children:h("p",{},"Report access")})));
    expect(html).toContain("Report access");
  });
  it("customer role cannot access staff report page",()=>{
    const html=renderToStaticMarkup(h(AuthContext.Provider,{value:auth(["CUSTOMER"])},
      h(ProtectedRoute,{roles:AI_REPORT_ROLES,children:h("p",{},"Report access")})));
    expect(html).not.toContain("Report access");expect(html).toContain("chưa được phân quyền");
  });
});
