import { ApiError, createApiClient } from "@/lib/api-client";
import type { IngredientQuery, IngredientRecommendations, ResearchCustomers } from "../types/ingredientDemo.types";

export const INGREDIENT_DEMO_ORIGIN = import.meta.env.VITE_INGREDIENT_DEMO_API_URL || "http://127.0.0.1:8096";
const PREFIX = "/api/v1/ingredient-demo";
const XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function isResearch(value: unknown): value is Record<string, unknown> {
  return isRecord(value) && value.source_kind === "SYNTHETIC" && value.research_only === true
    && value.production_approved === false && typeof value.dataset_id === "string" && !!value.dataset_id;
}

function invalidResponse(): never {
  throw new Error("Phản hồi demo không đúng contract nghiên cứu.");
}

export function demoAsOf(date: string, time: string): string {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time)) {
    throw new Error("Chọn ngày và giờ UTC hợp lệ.");
  }
  const iso = `${date}T${time}:00Z`;
  const value = Date.parse(iso);
  if (!Number.isFinite(value) || new Date(value).toISOString().slice(0, 16) !== `${date}T${time}`
      || value < Date.parse("2026-03-01T00:00:00Z") || value > Date.parse("2026-05-01T00:00:00Z")) {
    throw new Error("Thời điểm phải từ 01/03/2026 00:00 đến 01/05/2026 00:00 UTC.");
  }
  return iso;
}

export function demoErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) return "Demo-token sai hoặc thiếu. Hãy nhập lại token riêng của backend demo.";
    if (error.status === 404) return "Backend demo chưa bật hoặc khách nghiên cứu không còn tồn tại. Hãy tải lại danh sách.";
    if (error.status === 422) return "Tham số không hợp lệ. Kiểm tra khách, chế độ và thời điểm trong cửa sổ nghiên cứu.";
    if (error.status === 503) return "Dataset/model artifact của backend demo chưa sẵn sàng hoặc không hợp lệ.";
    if (error.status === 0 || error.code === "NETWORK_ERROR" || error.isTimeout) {
      return "Backend demo chưa chạy, không truy cập được hoặc bị CORS chặn. Kiểm tra cổng 8096 và cấu hình proxy/CORS.";
    }
    if (error.status === 502) return "Không kết nối được backend demo. Kiểm tra backend riêng ở cổng 8096.";
  }
  // Never echo transport errors, headers, server messages or credentials.
  return "Không thể hoàn tất yêu cầu Ingredient demo. Kiểm tra backend và thử lại.";
}

export function createIngredientDemoApi(baseUrl = import.meta.env.DEV ? "/ingredient-demo-api" : INGREDIENT_DEMO_ORIGIN) {
  const client = createApiClient({ baseUrl, timeoutMs: 30_000, longTimeoutMs: 300_000, retry: false, unwrapData: false });
  function options(token: string, signal?: AbortSignal) {
    if (baseUrl !== "/ingredient-demo-api") {
      try {
        const url = new URL(baseUrl);
        if (!["http:", "https:"].includes(url.protocol) || url.username || url.password || url.search || url.hash
            || url.pathname !== "/") throw new Error();
      } catch { throw new Error("Cấu hình origin Ingredient demo không hợp lệ."); }
    }
    if (!token.trim()) throw new ApiError({ status: 401, code: "DEMO_TOKEN_REQUIRED", message: "Thiếu demo-token." });
    return { headers: { Authorization: `Bearer ${token}` }, signal, authenticated: false,
      retryOnUnauthorized: false, credentials: "omit" as const, cache: "no-store" as const, redirect: "error" as const };
  }
  return {
    async customers(token: string, signal?: AbortSignal): Promise<ResearchCustomers> {
      const value = await client.get<unknown>(`${PREFIX}/customers`, options(token, signal));
      if (!isResearch(value) || !Array.isArray(value.customers) || !value.customers.every(row => isRecord(row)
        && typeof row.customer_id === "string" && !!row.customer_id
        && ["PRODUCT_ONLY", "BOM_ONLY", "MIXED", "NO_HISTORY"].includes(String(row.cohort))
        && typeof row.cohort_as_of === "string" && typeof row.cold_at_train_cutoff === "boolean")
        || new Set(value.customers.map(row => row.customer_id)).size !== value.customers.length) invalidResponse();
      return value as unknown as ResearchCustomers;
    },
    async recommendations(token: string, query: IngredientQuery, signal?: AbortSignal): Promise<IngredientRecommendations> {
      const value = await client.get<unknown>(`${PREFIX}/recommendations`, { ...options(token, signal),
        query: { customer_id: query.customer_id, as_of: query.as_of, mode: query.mode, policy: query.policy, top_k: query.top_k } });
      if (!isResearch(value) || value.customer_id !== query.customer_id || value.mode !== query.mode
        || value.policy !== query.policy || value.requested_k !== query.top_k
        || typeof value.as_of !== "string" || Date.parse(value.as_of) !== Date.parse(query.as_of)
        || typeof value.model_version !== "string" || !value.model_version
        || !["OK", "INSUFFICIENT_ELIGIBLE_CANDIDATES"].includes(String(value.status))
        || !Array.isArray(value.recommendations) || value.returned_k !== value.recommendations.length
        || typeof value.fallback !== "string" || value.recommendations.length > query.top_k
        || (value.status === "OK" && value.returned_k !== query.top_k)
        || (value.status === "INSUFFICIENT_ELIGIBLE_CANDIDATES" && value.returned_k === query.top_k)
        || !value.recommendations.every((row, n) => isRecord(row)
          && row.rank === n + 1 && ["PRODUCT", "CUSTOM_BOM"].includes(String(row.recommendation_type))
          && (query.mode === "UNIFIED" || row.recommendation_type === query.mode)
          && typeof row.item_id === "string" && !!row.item_id && typeof row.recipe_id === "string" && !!row.recipe_id
          && (row.recommendation_type !== "PRODUCT" || (typeof row.variant_id === "string" && !!row.variant_id))
          && (row.recommendation_type !== "CUSTOM_BOM" || row.item_id === row.recipe_id)
          && typeof row.score === "number" && Number.isFinite(row.score) && row.score >= 0 && row.score <= 1
          && typeof row.reason === "string" && typeof row.strategy === "string" && typeof row.recipe_family === "string"
          && (row.profile_signals === undefined || (isRecord(row.profile_signals)
            && [row.profile_signals.orders, row.profile_signals.product_inferred_orders, row.profile_signals.custom_synthetic_orders]
              .every(n => typeof n === "number" && Number.isInteger(n) && n >= 0)))
          && row.model_version === value.model_version && Array.isArray(row.ingredient_summary)
          && row.ingredient_summary.every(item => isRecord(item) && typeof item.ingredient === "string"
            && typeof item.quantity === "string" && typeof item.unit === "string" && typeof item.role === "string"))) invalidResponse();
      return value as unknown as IngredientRecommendations;
    },
    async excel(token: string, signal?: AbortSignal): Promise<{ blob: Blob; filename: string }> {
      const blob = await client.download(`${PREFIX}/reports/excel`, options(token, signal));
      if (!blob.size || blob.type.split(";")[0].toLowerCase() !== XLSX) invalidResponse();
      const signature = new Uint8Array(await blob.slice(0, 4).arrayBuffer());
      if (signature.length !== 4 || signature[0] !== 0x50 || signature[1] !== 0x4b
          || signature[2] !== 3 || signature[3] !== 4) invalidResponse();
      const name = (blob as Blob & { name?: string }).name;
      const valid = name && name.length <= 200 && name.toLowerCase().endsWith(".xlsx")
        && !/[<>:"/\\|?*]/.test(name) && !Array.from(name).some(c => c.charCodeAt(0) < 32 || c.charCodeAt(0) === 127)
        && !/^(con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(name);
      return { blob, filename: valid ? name : "Ingredient_Recommendation_Research_20260401.xlsx" };
    }
  };
}

export const ingredientDemoApi = createIngredientDemoApi();
