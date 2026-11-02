import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, createApiClient } from "@/lib/api-client";
vi.mock("../../../api/api-client", () => ({apiClient: {}}));
import { createAiReportApi } from "./aiReport.api";
import { putAiReportOriginal } from "./aiReport.upload";
import type { AiReport, AiReportRecovery } from "../types/ai-report.types";
import { AiReportImportError, aiReportErrorMessage } from "../utils/ai-report.errors";
import { validateAiReportFile, validateAiReportMetadata, AI_REPORT_CONTENT_TYPES } from "../utils/ai-report.validation";
import { readAiReportRecovery, saveAiReportRecovery } from "../utils/ai-report.recovery";

const FILE = "11111111-1111-4111-8111-111111111111";
const REPORT = "22222222-2222-4222-8222-222222222222";
const recovery: AiReportRecovery = {fileId:FILE, name:"Report", version:"V1", fileName:"Báo cáo.pdf",fileType:"PDF"};
const report: AiReport = {...recovery,id:REPORT,createdBy:"owner",updatedBy:null,
  createdAt:"2026-10-12T00:00:00Z",updatedAt:"2026-10-12T00:00:00Z"};
const selection = {name:" Report ",version:" V1 ",file:new File(["%PDF-1.7\nreport"],"Báo cáo.pdf",{type:"application/pdf"})};
const upload = {fileId:FILE,visibility:"PRIVATE",publicUrl:null,uploadUrl:"https://private.test/key?signed=synthetic",expiresInSeconds:300};
let calls: {url:URL; init:RequestInit}[];
let initCode: number, completeCode: number, importCode: number, listCode: number, downloadCode: number;
let completeValue: unknown, importValue: unknown, initValue:unknown, downloadBody: string, downloadMime: string, downloadFilename:string;
let listValue: unknown, patchValue: unknown;

function json(body: unknown, status=200) {
  return new Response(JSON.stringify(body), {status,headers:{"Content-Type":"application/json"}});
}
function fixture(put = vi.fn<typeof putAiReportOriginal>().mockResolvedValue(undefined)) {
  const client = createApiClient({baseUrl:"http://gateway.test/api",retry:false,
    tokenProvider:{getAccessToken:async()=>"synthetic-user-token",refreshAccessToken:async()=>null}});
  return {api:createAiReportApi(client,put),put};
}

beforeEach(() => {
  calls=[]; initCode=completeCode=importCode=listCode=downloadCode=200;
  completeValue="Success"; importValue=report;initValue=upload; downloadBody="original-pdf"; downloadMime="application/pdf";downloadFilename="Báo cáo.pdf";
  listValue=undefined;patchValue=report;
  vi.stubGlobal("fetch",vi.fn(async (input: string, init:RequestInit) => {
    const url=new URL(input); calls.push({url,init});
    if(url.pathname.endsWith("/init-upload")) return json({data:initValue,code:200},initCode);
    if(url.pathname.endsWith("/complete")) return json({data:completeValue},completeCode);
    if(url.pathname.endsWith("/download")) return new Response(downloadBody,{status:downloadCode,headers:{
      "Content-Type":downloadMime,"Content-Disposition":`attachment; filename*=UTF-8''${encodeURIComponent(downloadFilename)}`}});
    if(init.method==="POST") return json(importValue,importCode);
    if(init.method==="PATCH") return json(patchValue);
    if(url.pathname.endsWith(REPORT)) return json(report);
    if(listValue!==undefined) return json(listValue,listCode);
    return json({content:[report],page:Number(url.searchParams.get("page")),size:Number(url.searchParams.get("size")),
      totalElements:31,totalPages:2,first:false,last:true},listCode);
  }));
});
afterEach(()=>{vi.restoreAllMocks();vi.unstubAllGlobals();});

describe("AI Report gateway contracts and original upload",()=>{
  it("orchestrates unwrapped init, private PUT, complete, plain report DTO with existing JWT",async()=>{
    const {api,put}=fixture(); const progress=vi.fn(), checkpoint=vi.fn();
    expect(await api.prepareImport(selection).submit(progress,checkpoint)).toEqual(report);
    expect(calls.map(c=>c.url.pathname)).toEqual(["/api/file/init-upload",`/api/file/${FILE}/complete`,"/api/recommendation/ai-reports"]);
    expect(JSON.parse(calls[0].init.body as string)).toEqual({originalFilename:"Báo cáo.pdf",contentType:"application/pdf",
      sizeBytes:selection.file.size,purpose:"USER_DOCUMENT",ownerType:"AI_REPORT"});
    expect(JSON.parse(calls[2].init.body as string)).toEqual({name:"Report",version:"V1",fileId:FILE});
    expect(put).toHaveBeenCalledOnce(); expect(put.mock.calls[0].slice(0,3)).toEqual([upload.uploadUrl,selection.file,"application/pdf"]);
    expect(progress.mock.calls.map(c=>c[0].stage)).toEqual(["initializing","uploading","completing","importing"]);
    expect(checkpoint).toHaveBeenCalledWith(recovery);
    for(const call of calls) {
      const headers=new Headers(call.init.headers); expect(headers.get("Authorization")).toBe("Bearer synthetic-user-token");
      expect(headers.has("X-Signature")).toBe(false); expect(headers.has("X-User-ID")).toBe(false);
    }
  });
  it.each(["PDF","XLSX"] as const)("normalizes absent browser MIME for %s",async(kind)=>{
    const {api,put}=fixture();
    const file=new File(["original"],`report.${kind.toLowerCase()}`);
    importValue={...report,fileName:file.name,fileType:kind};
    await api.prepareImport({...selection,file}).submit();
    expect(JSON.parse(calls[0].init.body as string).contentType).toBe(AI_REPORT_CONTENT_TYPES[kind]);
    expect(put.mock.calls[0][2]).toBe(AI_REPORT_CONTENT_TYPES[kind]);
  });
  it("retries import using exactly the uploaded file and immutable intent, without another PUT/init",async()=>{
    const {api,put}=fixture(); const op=api.prepareImport(selection); importCode=502;
    await expect(op.submit()).rejects.toMatchObject({stage:"importing",retryable:true});
    importCode=200; expect(await op.submit()).toEqual(report); expect(await op.submit()).toEqual(report);
    expect(calls.filter(c=>c.url.pathname.endsWith("init-upload"))).toHaveLength(1);
    expect(calls.filter(c=>c.url.pathname.endsWith("complete"))).toHaveLength(1);
    expect(calls.filter(c=>c.url.pathname==="/api/recommendation/ai-reports")).toHaveLength(2);
    expect(put).toHaveBeenCalledOnce();
  });
  it("resumes completed upload after page reload with only the business POST",async()=>{
    const {api,put}=fixture(); expect(await api.resumeImport(recovery).submit()).toEqual(report);
    expect(calls).toHaveLength(1); expect(calls[0].url.pathname).toBe("/api/recommendation/ai-reports"); expect(put).not.toHaveBeenCalled();
  });
  it("concurrent submits share one operation; 201 and idempotent 200 are accepted",async()=>{
    importCode=201; const {api,put}=fixture();const op=api.prepareImport(selection);
    const one=op.submit(),two=op.submit(); expect(one).toBe(two); await expect(one).resolves.toEqual(report);
    expect(put).toHaveBeenCalledOnce(); expect(calls).toHaveLength(3);
  });
  it("completion uncertainty retries completion only, keeping fileId and bytes",async()=>{
    const {api,put}=fixture();const op=api.prepareImport(selection);completeCode=503;
    await expect(op.submit()).rejects.toMatchObject({stage:"completing",retryable:true});
    completeCode=200; await op.submit(); expect(put).toHaveBeenCalledOnce();
    expect(calls.filter(c=>c.url.pathname.endsWith("init-upload"))).toHaveLength(1);
  });
  it("does not import when completion is unconfirmed",async()=>{
    completeValue=null;const {api}=fixture();await expect(api.prepareImport(selection).submit()).rejects.toMatchObject({stage:"completing"});
    expect(calls).toHaveLength(2);
  });
  it("does not cache a malformed import response as success",async()=>{
    importValue={...report,fileId:"wrong"};const {api,put}=fixture();const op=api.prepareImport(selection);
    await expect(op.submit()).rejects.toMatchObject({stage:"importing"});
    importValue=report;await expect(op.submit()).resolves.toEqual(report); expect(put).toHaveBeenCalledOnce();
  });
  it("network failure retries the same PUT capability while valid",async()=>{
    const put=vi.fn().mockRejectedValueOnce(new ApiError({status:0,code:"NETWORK_ERROR",message:"network"})).mockResolvedValue(undefined);
    const {api}=fixture(put);const op=api.prepareImport(selection);
    await expect(op.submit()).rejects.toMatchObject({stage:"uploading",retryable:true});await op.submit();
    expect(put).toHaveBeenCalledTimes(2); expect(put.mock.calls[0][0]).toBe(put.mock.calls[1][0]);
    expect(calls.filter(c=>c.url.pathname.endsWith("init-upload"))).toHaveLength(1);
  });
  it("expired/denied PUT requires explicit new selection; no duplicate init or import",async()=>{
    const put=vi.fn().mockRejectedValue(new ApiError({status:403,code:"UPLOAD_FAILED",message:"secret URL not shown"}));
    const {api}=fixture(put);
    await expect(api.prepareImport(selection).submit()).rejects.toMatchObject({stage:"uploading",retryable:false});
    expect(calls).toHaveLength(1);
  });
  it("init failure stops PUT/completion/import",async()=>{
    initCode=503;const {api,put}=fixture();await expect(api.prepareImport(selection).submit()).rejects.toMatchObject({stage:"initializing"});
    expect(put).not.toHaveBeenCalled();expect(calls).toHaveLength(1);
  });
  it.each([{visibility:"PUBLIC"},{publicUrl:"https://public.test/file"},{uploadUrl:"invalid"},{expiresInSeconds:0}])("rejects malformed/private-incompatible capability",async(change)=>{
    initValue={...upload,...change};const {api,put}=fixture();await expect(api.prepareImport(selection).submit()).rejects.toMatchObject({retryable:false});
    expect(put).not.toHaveBeenCalled();expect(calls).toHaveLength(1);
  });
  it.each([400,401,403,404,409,415,422,502,503])("handles business status %s without duplicating uploaded original",async(status)=>{
    importCode=status;const {api,put}=fixture();await expect(api.prepareImport(selection).submit()).rejects.toBeInstanceOf(AiReportImportError);
    expect(put).toHaveBeenCalledOnce(); expect(calls.filter(c=>c.url.pathname.endsWith("init-upload"))).toHaveLength(1);
  });
});

describe("report metadata, pagination and authorized binary response",()=>{
  it.each([{content:[]},{content:[{id:REPORT}],page:0,size:20,totalElements:1,totalPages:1,first:true,last:true}])("rejects malformed page/row instead of crashing or fabricating list success",async(value)=>{
    listValue=value;await expect(fixture().api.listAiReports(0,20)).rejects.toThrow();
  });
  it("rejects empty metadata update instead of closing with false success",async()=>{
    patchValue=null;await expect(fixture().api.updateAiReport(REPORT,{name:"New",version:"V2"})).rejects.toThrow();
  });
  it("uses server pagination query and exact GET/PATCH routes",async()=>{
    const {api}=fixture();const controller=new AbortController();const page=await api.listAiReports(1,20,controller.signal);
    expect(page.content).toEqual([report]);expect(page.page).toBe(1);expect(page.totalElements).toBe(31);
    expect(calls[0].url.searchParams.get("page")).toBe("1");expect(calls[0].url.searchParams.get("size")).toBe("20");
    await api.getAiReport(REPORT);await api.updateAiReport(REPORT,{name:"New",version:"V2"});
    expect(calls[1].url.pathname).toBe(`/api/recommendation/ai-reports/${REPORT}`);
    expect(JSON.parse(calls[2].init.body as string)).toEqual({name:"New",version:"V2"});
  });
  it("preserves original bytes, MIME and RFC5987 filename using shared binary client",async()=>{
    const {api}=fixture(); const blob=await api.downloadAiReport(report);
    expect(await blob.text()).toBe("original-pdf");expect(blob.type).toBe("application/pdf");
    expect((blob as Blob&{name?:string}).name).toBe("Báo cáo.pdf");
    expect(calls[0].url.pathname).toBe(`/api/recommendation/ai-reports/${REPORT}/download`);
    expect(new Headers(calls[0].init.headers).get("Authorization")).toBe("Bearer synthetic-user-token");
  });
  it("downloads original XLSX bytes and filename without transforming workbook",async()=>{
    downloadBody="PK-synthetic-original"; downloadMime=AI_REPORT_CONTENT_TYPES.XLSX;downloadFilename="Báo cáo.xlsx";
    const {api}=fixture();const blob=await api.downloadAiReport({...report,fileType:"XLSX",fileName:downloadFilename});
    expect(await blob.text()).toBe(downloadBody);expect(blob.type).toBe(downloadMime);
    expect((blob as Blob&{name?:string}).name).toBe(downloadFilename);
  });
  it.each([401,403,404,502,503])("rejects download HTTP %s instead of saving error bytes",async(status)=>{
    downloadCode=status;const {api}=fixture();await expect(api.downloadAiReport(report)).rejects.toBeInstanceOf(ApiError);
  });
  it.each(["empty","json"])("rejects %s successful download response",async(kind)=>{
    if(kind==="empty")downloadBody="";else downloadMime="application/json";
    const {api}=fixture();await expect(api.downloadAiReport(report)).rejects.toThrow("Invalid original-file response");
  });
  it("unauthorized list stays an error",async()=>{listCode=403;await expect(fixture().api.listAiReports(0,20)).rejects.toMatchObject({status:403});});
});

describe("validation and private recovery",()=>{
  it.each([
    new File(["a"],"r.txt",{type:"text/plain"}),new File([],"r.pdf"),
    new File([new Uint8Array(10*1024*1024+1)],"r.pdf"),new File(["a"],"r.pdf",{type:"text/html"}),
  ])("rejects invalid selection before network",async(file)=>{
    const {api}=fixture();expect(validateAiReportFile(file)).not.toBeNull();
    await expect(api.prepareImport({...selection,file}).submit()).rejects.toMatchObject({retryable:false});expect(calls).toHaveLength(0);
  });
  it("matches metadata limits 200/50 and accepts case-insensitive extension",()=>{
    expect(validateAiReportMetadata({name:"n".repeat(201),version:"v".repeat(51)})).toHaveProperty("name");
    expect(validateAiReportFile(new File(["a"],"R.PDF"))).toBeNull();
  });
  it("stores completed file checkpoint per actor without capabilities/token and rejects malformed recovery",()=>{
    const values=new Map<string,string>();vi.stubGlobal("sessionStorage",{getItem:(k:string)=>values.get(k)??null,
      setItem:(k:string,v:string)=>values.set(k,v),removeItem:(k:string)=>values.delete(k)});
    saveAiReportRecovery("owner",recovery);expect(readAiReportRecovery("owner")).toEqual(recovery);
    expect(readAiReportRecovery("other")).toBeNull();expect([...values.values()][0]).not.toContain("signed");
    values.set("hvtra.ai-report-import:owner",JSON.stringify({...recovery,fileId:"bad"}));expect(readAiReportRecovery("owner")).toBeNull();
    saveAiReportRecovery("owner",null);expect(values.size).toBe(0);
  });
  it("does not render raw backend messages or signed URLs",()=>{
    expect(aiReportErrorMessage(new ApiError({status:403,code:"DENIED",message:"sensitive-url"}))).not.toContain("sensitive-url");
  });
});

describe("capability upload transport",()=>{
  it("sends only Content-Type with progress and no cookies/JWT",async()=>{
    class Xhr {
      upload={onprogress:undefined as ((event:{lengthComputable:boolean;loaded:number;total:number})=>void)|undefined};
      status=200;withCredentials=true;timeout=0;onload?:()=>void;onerror?:()=>void;ontimeout?:()=>void;
      open=vi.fn();setRequestHeader=vi.fn();
      send=vi.fn(()=>{this.upload.onprogress?.({lengthComputable:true,loaded:2,total:4});this.onload?.();});
    }
    const xhr=new Xhr();vi.stubGlobal("XMLHttpRequest",class{constructor(){return xhr;}});const progress=vi.fn();
    await putAiReportOriginal(upload.uploadUrl,selection.file,"application/pdf",progress);
    expect(xhr.open).toHaveBeenCalledWith("PUT",upload.uploadUrl);expect(xhr.withCredentials).toBe(false);
    expect(xhr.setRequestHeader.mock.calls).toEqual([["Content-Type","application/pdf"]]);expect(progress).toHaveBeenCalledWith(50);
  });
});
