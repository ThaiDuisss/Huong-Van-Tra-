import { LoaderCircle, LockKeyhole, RefreshCw, Unplug } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";
import { Button } from "../../../components/ui/Button";
import { Field, NativeSelect, TextInput } from "../../../components/ui/Field";
import { PageHeader } from "../../manufacturing/components/shared/PageHeader";
import { IngredientResultCard, ResearchId } from "../components/IngredientResultCard";
import { formatDemoInstant, shortResearchId } from "../utils/ingredientDemo.display";
import { demoAsOf, demoErrorMessage, ingredientDemoApi } from "../services/ingredientDemo.api";
import type { IngredientCohort, IngredientMode, IngredientPolicy, IngredientRecommendations, ResearchCustomers } from "../types/ingredientDemo.types";

const COHORTS: Record<IngredientCohort, string> = { PRODUCT_ONLY: "Chỉ Product", BOM_ONLY: "Chỉ BOM", MIXED: "Kết hợp", NO_HISTORY: "Chưa có lịch sử" };
export function IngredientDemoPage() {
  const [draftToken, setDraftToken] = useState("");
  const [connected, setConnected] = useState(false);
  const [catalog, setCatalog] = useState<ResearchCustomers | null>(null);
  const [customersBusy, setCustomersBusy] = useState(false);
  const [customersError, setCustomersError] = useState("");
  const [search, setSearch] = useState("");
  const [cohort, setCohort] = useState("");
  const [coldOnly, setColdOnly] = useState(false);
  const [customerPage, setCustomerPage] = useState(0);
  const [selectedId, setSelectedId] = useState("");
  const [mode, setMode] = useState<IngredientMode>("UNIFIED");
  const [policy, setPolicy] = useState<IngredientPolicy>("DISCOVERY");
  const [topK, setTopK] = useState(5);
  const [date, setDate] = useState("2026-04-01");
  const [time, setTime] = useState("00:00");
  const [result, setResult] = useState<IngredientRecommendations | null>(null);
  const [recommendBusy, setRecommendBusy] = useState(false);
  const [recommendError, setRecommendError] = useState("");
  // Credential is scoped to this mounted page, never a global store or persistence.
  const session = useRef<{ token: string; id: number } | null>(null);
  const generation = useRef(0);
  const recommendationSequence = useRef(0);
  const customersController = useRef<AbortController | null>(null);
  const recommendationController = useRef<AbortController | null>(null);

  useEffect(() => () => {
    generation.current++;
    session.current = null;
    customersController.current?.abort();
    recommendationController.current?.abort();
  }, []);

  function invalidateRecommendation() {
    recommendationSequence.current++;
    recommendationController.current?.abort();
    setResult(null);
    setRecommendError("");
    setRecommendBusy(false);
  }

  function disconnect() {
    generation.current++;
    session.current = null;
    customersController.current?.abort();
    invalidateRecommendation();
    setDraftToken(""); setConnected(false); setCatalog(null); setSelectedId("");
    setCustomersBusy(false); setCustomersError("");
    setSearch(""); setCohort(""); setColdOnly(false); setCustomerPage(0);
  }

  async function loadCustomers(token?: string) {
    if (token !== undefined) {
      disconnect();
      session.current = { token, id: ++generation.current };
      setConnected(true);
    }
    const current = session.current;
    if (!current) return;
    invalidateRecommendation(); setSelectedId(""); setCatalog(null);
    customersController.current?.abort();
    const controller = new AbortController();
    customersController.current = controller;
    setCustomersBusy(true); setCustomersError("");
    try {
      const data = await ingredientDemoApi.customers(current.token, controller.signal);
      if (controller.signal.aborted || current.id !== generation.current) return;
      setCatalog(data); setCustomerPage(0);
    } catch (error) {
      if (!controller.signal.aborted && current.id === generation.current) setCustomersError(demoErrorMessage(error));
    } finally {
      if (!controller.signal.aborted && current.id === generation.current) setCustomersBusy(false);
    }
  }

  async function recommend() {
    const current = session.current;
    if (!current || !selectedId) return;
    let asOf: string;
    try { asOf = demoAsOf(date, time); }
    catch { setRecommendError("Chọn ngày/giờ UTC từ 01/03/2026 00:00 đến 01/05/2026 00:00."); return; }
    recommendationController.current?.abort();
    const controller = new AbortController();
    recommendationController.current = controller;
    const sequence = ++recommendationSequence.current;
    setRecommendBusy(true); setRecommendError(""); setResult(null);
    try {
      const value = await ingredientDemoApi.recommendations(current.token,
        { customer_id: selectedId, as_of: asOf, mode, policy, top_k: topK }, controller.signal);
      if (!controller.signal.aborted && sequence === recommendationSequence.current && current.id === generation.current) setResult(value);
    } catch (error) {
      if (!controller.signal.aborted && sequence === recommendationSequence.current && current.id === generation.current) setRecommendError(demoErrorMessage(error));
    } finally {
      if (!controller.signal.aborted && sequence === recommendationSequence.current && current.id === generation.current) setRecommendBusy(false);
    }
  }

  const filtered = useMemo(() => (catalog?.customers ?? []).filter(customer =>
    customer.customer_id.toLowerCase().includes(search.trim().toLowerCase()) && (!cohort || customer.cohort === cohort)
    && (!coldOnly || customer.cold_at_train_cutoff)), [catalog, search, cohort, coldOnly]);
  const pages = Math.max(1, Math.ceil(filtered.length / 20));
  const visiblePage = Math.min(customerPage, pages - 1);
  const visible = filtered.slice(visiblePage * 20, visiblePage * 20 + 20);
  const selected = catalog?.customers.find(customer => customer.customer_id === selectedId);
  const readableDate = /^\d{4}-\d{2}-\d{2}$/.test(date) ? date.split("-").reverse().join("/") : "Chưa chọn ngày";

  return <div className="space-y-5">
    <PageHeader title="Gợi ý nguyên liệu (Demo)" description="Không gian nghiên cứu Ingredient AI độc lập với Product V1 và bán hàng POS." />
    <div role="note" className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm font-semibold leading-6 text-amber-950">
      DỮ LIỆU MÔ PHỎNG (SYNTHETIC) — KHÔNG PHẢI GỢI Ý DÀNH CHO KHÁCH HÀNG THẬT.
    </div>
    <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
      <h2 className="flex items-center gap-2 font-semibold text-slate-900"><LockKeyhole size={17} /> Kết nối demo riêng</h2>
      <p className="text-sm text-slate-600">Demo-token khác token đăng nhập POS. Token chỉ giữ trong bộ nhớ của trang này; rời trang hoặc tải lại sẽ cần nhập lại.</p>
      <form className="flex flex-col gap-3 sm:flex-row" autoComplete="off" onSubmit={event => { event.preventDefault(); if (draftToken.trim()) void loadCustomers(draftToken.trim()); }}>
        <Field className="flex-1" label="Demo-token"><TextInput type="password" value={draftToken} onChange={event => setDraftToken(event.target.value)} autoComplete="off" spellCheck={false} placeholder="Nhập token riêng của Ingredient Demo Backend" aria-label="Demo-token" />
        </Field>
        <Button type="submit" disabled={!draftToken.trim() || customersBusy} className="self-end" variant="primary">Kết nối và tải khách</Button>
        {connected && <div className="self-end"><Button onClick={disconnect}><Unplug size={16} /> Xóa token / ngắt</Button></div>}
      </form>
      <p className="text-xs leading-5 text-slate-500">Local development dùng Vite proxy đến backend demo (mặc định cổng 8096). Bản build gọi khác origin cần CORS/reverse proxy. Trang không tự dùng Product V1 khi demo không kết nối được.</p>
    </section>
    <div className="rounded-lg bg-slate-100 px-4 py-3 text-xs leading-5 text-slate-600">Để xuất báo cáo BOM, mở màn Báo cáo AI → Xuất báo cáo AI → Theo BOM.</div>
    <div className="grid items-start gap-5 xl:grid-cols-[minmax(300px,380px)_minmax(0,1fr)]">
      <div className="space-y-5">
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4" aria-busy={customersBusy}>
          <h2 className="font-semibold text-slate-900">1. Chọn khách nghiên cứu</h2>
          {!connected && <p className="text-sm text-slate-500">Nhập demo-token để tải danh sách khách.</p>}
          {customersBusy && <p role="status" className="flex items-center gap-2 text-sm"><LoaderCircle size={15} className="animate-spin" /> Đang tải khách nghiên cứu…</p>}
          {customersError && <div role="alert" className="space-y-2 text-sm text-red-800"><p>{customersError}</p><Button onClick={() => void loadCustomers()}><RefreshCw size={15} /> Thử tải lại</Button></div>}
          {catalog && <>
            <Field label="Tìm theo Customer ID"><TextInput value={search} onChange={event => { setSearch(event.target.value); setCustomerPage(0); }} placeholder="Nhập một phần hoặc toàn bộ ID" /></Field>
            <Field label="Nhóm khách"><NativeSelect value={cohort} onChange={event => { setCohort(event.target.value); setCustomerPage(0); }}><option value="">Tất cả nhóm</option>{Object.entries(COHORTS).map(([key, label]) => <option key={key} value={key}>{label}</option>)}</NativeSelect></Field>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={coldOnly} onChange={event => { setColdOnly(event.target.checked); setCustomerPage(0); }} /> Chưa có lịch sử Train (cold-start)</label>
            {coldOnly && <p className="text-xs leading-5 text-slate-500">Cold-start được xác định tại 01/03/2026 UTC. Chọn mốc này để xem fallback khi chưa có lịch sử.</p>}
            <p className="text-xs text-slate-500">{filtered.length}/{catalog.customers.length} khách phù hợp · Trang {visiblePage + 1}/{pages}</p>
            {!visible.length ? <p role="status" className="py-4 text-sm">Không có khách phù hợp bộ lọc.</p> : <ul className="max-h-80 space-y-1 overflow-y-auto">{visible.map(customer => <li key={customer.customer_id}>
              <button type="button" aria-pressed={selectedId === customer.customer_id} onClick={() => { invalidateRecommendation(); setSelectedId(customer.customer_id); }}
                className={`w-full rounded-lg border p-3 text-left text-sm ${selectedId === customer.customer_id ? "border-emerald-600 bg-emerald-50 text-emerald-950" : "border-slate-100 hover:bg-slate-50"}`}>
                <span className="block font-semibold">Khách nghiên cứu · {shortResearchId(customer.customer_id)}</span><span className="text-xs">{COHORTS[customer.cohort]}{customer.cold_at_train_cutoff ? " · Cold tại Train" : ""}</span>
              </button>
            </li>)}</ul>}
            <div className="flex justify-between"><Button disabled={visiblePage === 0} onClick={() => setCustomerPage(visiblePage - 1)} size="sm">Trang trước</Button><Button disabled={visiblePage >= pages - 1} onClick={() => setCustomerPage(visiblePage + 1)} size="sm">Trang sau</Button></div>
          </>}
          {selected && <div className="space-y-2 border-t pt-3"><p className="text-sm font-semibold text-emerald-900">Đã chọn · {COHORTS[selected.cohort]}</p><ResearchId label="Customer ID" value={selected.customer_id} /></div>}
        </section>
        <section className="space-y-3 rounded-xl border border-slate-200 bg-white p-4">
          <h2 className="font-semibold text-slate-900">2. Điều kiện gợi ý</h2>
          <Field label="Chế độ"><NativeSelect value={mode} onChange={event => { invalidateRecommendation(); setMode(event.target.value as IngredientMode); }}><option value="PRODUCT">Product — sản phẩm</option><option value="CUSTOM_BOM">Custom BOM — công thức</option><option value="UNIFIED">Kết hợp (Unified)</option></NativeSelect></Field>
          <Field label="Chính sách"><NativeSelect value={policy} onChange={event => { invalidateRecommendation(); setPolicy(event.target.value as IngredientPolicy); }}><option value="DISCOVERY">DISCOVERY — ưu tiên nội dung chưa mua</option><option value="REPEAT">REPEAT — cho phép gợi ý lại</option></NativeSelect></Field>
          <Field label="Số kết quả"><NativeSelect value={topK} onChange={event => { invalidateRecommendation(); setTopK(Number(event.target.value)); }}>{[1, 2, 3, 4, 5].map(k => <option key={k} value={k}>Top-{k}</option>)}</NativeSelect></Field>
          <div className="grid grid-cols-2 gap-2"><Field label="Ngày (UTC)"><TextInput type="date" min="2026-03-01" max="2026-05-01" value={date} onChange={event => { invalidateRecommendation(); setDate(event.target.value); }} /></Field><Field label="Giờ (UTC)"><TextInput type="time" value={time} onChange={event => { invalidateRecommendation(); setTime(event.target.value); }} /></Field></div>
          <p className="text-xs text-slate-500">{readableDate} lúc {time || "—"} UTC · Cửa sổ 01/03–01/05/2026; ngày cuối chỉ đến 00:00.</p>
          <div className="flex flex-wrap gap-2">{["2026-03-01", "2026-04-01"].map(value => <Button key={value} size="sm" onClick={() => { invalidateRecommendation(); setDate(value); setTime("00:00"); }}>{value === "2026-03-01" ? "Mốc cold-start: 01/03" : "Mốc mặc định: 01/04"}</Button>)}</div>
          <Button variant="primary" disabled={!connected || !selectedId || recommendBusy || customersBusy} onClick={() => void recommend()}>{recommendBusy && <LoaderCircle size={16} className="animate-spin" />} {recommendBusy ? "Đang lấy gợi ý…" : "Xem gợi ý nguyên liệu"}</Button>
        </section>
      </div>
      <section className="min-w-0 space-y-4" aria-busy={recommendBusy} aria-label="Kết quả Ingredient AI">
        <h2 className="text-lg font-bold text-slate-900">Kết quả gợi ý</h2>
        {recommendError && <div role="alert" className="space-y-3 rounded-xl bg-red-50 p-4 text-sm text-red-800"><p>{recommendError}</p><Button disabled={!connected || !selectedId || recommendBusy} onClick={() => void recommend()}>Thử lại</Button></div>}
        {recommendBusy && <p role="status" className="flex items-center gap-2 rounded-xl bg-white p-5 text-sm"><LoaderCircle size={17} className="animate-spin" /> Đang gọi Ingredient Recommendation API…</p>}
        {!result && !recommendBusy && !recommendError && <p className="rounded-xl border border-dashed border-slate-300 bg-white p-6 text-sm text-slate-500">Chọn khách và điều kiện rồi bấm xem gợi ý. Kết quả chỉ phục vụ nghiên cứu, không có thao tác thêm vào giỏ hàng.</p>}
        {result && <>
          <div role="status" className="space-y-1 rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-600">
            <p>{result.returned_k}/{result.requested_k} kết quả · {result.mode} · {result.policy} · {result.fallback}</p>
            <p>As-of: {formatDemoInstant(result.as_of)} · Model: {result.model_version}</p>
            <p className="text-xs">Điểm chỉ làm tròn khi hiển thị; định lượng giữ nguyên theo API.</p>
          </div>
          {result.status === "INSUFFICIENT_ELIGIBLE_CANDIDATES" && <p role="status" className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Không đủ ứng viên hợp lệ; hiển thị đúng {result.returned_k} kết quả backend trả về.</p>}
          {!result.recommendations.length && <p className="rounded-xl bg-white p-5 text-sm">Không có gợi ý hợp lệ cho lựa chọn này.</p>}
          {result.recommendations.map(item => <IngredientResultCard key={`${item.recommendation_type}-${item.item_id}`} item={item} />)}
        </>}
      </section>
    </div>
  </div>;
}
