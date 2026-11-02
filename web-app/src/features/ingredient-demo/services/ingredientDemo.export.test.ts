import { afterEach, describe, expect, it, vi } from "vitest";
import { createIngredientDemoApi, demoErrorMessage } from "./ingredientDemo.api";

const mime = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
// Transport fixture only; the FE does not generate or alter workbook content.
const bytes = new Uint8Array([0x50, 0x4b, 3, 4, 0, 1, 2, 3]);
const token = "separate-research-token-fixture";
afterEach(() => vi.unstubAllGlobals());

describe("existing Ingredient Excel client for unified report export", () => {
  it("uses the dev proxy, separate Bearer and original bytes/Content-Disposition filename", async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(bytes, {headers: {"Content-Type": mime,
      "Content-Disposition": "attachment; filename*=UTF-8''BOM%20research.xlsx"}}));
    vi.stubGlobal("fetch", fetch);
    const result = await createIngredientDemoApi().excel(token);
    expect(fetch).toHaveBeenCalledTimes(1);
    const [url, options] = fetch.mock.calls[0];
    expect(url).toBe("/ingredient-demo-api/api/v1/ingredient-demo/reports/excel");
    expect(url).not.toContain(token);
    const headers = new Headers(options.headers);
    expect(headers.get("Authorization")).toBe(`Bearer ${token}`);
    expect(headers.has("X-Client-Type")).toBe(false);
    expect(options.credentials).toBe("omit"); expect(options.cache).toBe("no-store"); expect(options.redirect).toBe("error");
    expect(result.filename).toBe("BOM research.xlsx");
    expect(new Uint8Array(await result.blob.arrayBuffer())).toEqual(bytes);
  });
  it("rejects missing tokens before sending any request", async () => {
    const fetch = vi.fn(); vi.stubGlobal("fetch", fetch);
    await expect(createIngredientDemoApi().excel("  ")).rejects.toMatchObject({status: 401});
    expect(fetch).not.toHaveBeenCalled();
  });
  it.each([
    {body: '{"error":"failed"}', type: "application/json"},
    {body: '{"error":"failed"}', type: mime},
    {body: "", type: mime},
  ])("rejects JSON/empty workbook responses without creating a download ($type)", async ({body, type}) => {
    const fetch = vi.fn().mockResolvedValue(new Response(body, {headers: {"Content-Type": type}}));
    vi.stubGlobal("fetch", fetch);
    await expect(createIngredientDemoApi().excel(token)).rejects.toThrow();
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it.each([401, 404, 422, 503])("handles HTTP %s without exposing server messages or retrying", async status => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({message: token}), {
      status, headers: {"Content-Type": "application/json"},
    }));
    vi.stubGlobal("fetch", fetch);
    const failure = await createIngredientDemoApi().excel(token).catch(e => e);
    expect(failure.status).toBe(status); expect(demoErrorMessage(failure)).not.toContain(token);
    expect(fetch).toHaveBeenCalledTimes(1);
  });
  it("uses the existing safe filename fallback and reports network/CORS failures", async () => {
    const fetch = vi.fn().mockResolvedValueOnce(new Response(bytes, {headers: {"Content-Type": mime,
      "Content-Disposition": 'attachment; filename="bad?.xlsx"'}})).mockRejectedValueOnce(new TypeError(token));
    vi.stubGlobal("fetch", fetch);
    expect((await createIngredientDemoApi().excel(token)).filename).toBe("Ingredient_Recommendation_Research_20260401.xlsx");
    const failure = await createIngredientDemoApi().excel(token).catch(e => e);
    expect(demoErrorMessage(failure)).toContain("CORS"); expect(demoErrorMessage(failure)).not.toContain(token);
  });
});
