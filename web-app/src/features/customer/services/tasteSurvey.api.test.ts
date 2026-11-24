import { beforeEach, expect, it, vi } from "vitest";

import { apiClient } from "@/api/api-client";
import { tasteSurveyApi } from "./tasteSurvey.api";

vi.mock("@/api/api-client", () => ({ apiClient: { get: vi.fn(), post: vi.fn() } }));

const input = {
    customerId: "customer",
    originInvoiceId: "invoice",
    taxonomyVersion: "tea-v1",
    taxonomySha256: "a".repeat(64),
    criteria: { hardGroups: [{ attribute: "FLAVOR", allowedValues: ["FLORAL"] }], softGroups: [] }
};
const keys = { create: "create-attempt", submit: "submit-attempt" };
const draft = { ...input, id: "survey", sessionId: "session", rowVersion: 0, status: "DRAFT" };
const submitted = { ...draft, rowVersion: 1, status: "SUBMITTED", submittedAt: "2026-11-22T07:00:00Z" };

beforeEach(() => vi.resetAllMocks());

it("keeps both idempotency keys when a submitted response is lost", async () => {
    vi.mocked(apiClient.post)
        .mockResolvedValueOnce(draft)
        .mockRejectedValueOnce(new Error("Timed out"))
        .mockResolvedValueOnce(submitted)
        .mockResolvedValueOnce(submitted);

    await expect(tasteSurveyApi.submit(input, keys)).rejects.toThrow("Timed out");
    await expect(tasteSurveyApi.submit(input, keys)).resolves.toEqual(submitted);

    const calls = vi.mocked(apiClient.post).mock.calls;
    expect(calls[0]).toEqual(["/customer/taste-surveys", input, { headers: { "Idempotency-Key": keys.create } }]);
    expect(calls[2]).toEqual(calls[0]);
    expect(calls[1]).toEqual([
        "/customer/taste-surveys/survey/submit",
        undefined,
        { headers: { "Idempotency-Key": keys.submit, "If-Match": '"0"', "X-Survey-Session-Id": "session" } }
    ]);
    expect(calls[3]?.[2]?.headers).toMatchObject({ "Idempotency-Key": keys.submit, "If-Match": '"1"' });
});

it("does not submit a draft bound to a different customer", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({ ...draft, customerId: "different" });

    await expect(tasteSurveyApi.submit(input, keys)).rejects.toThrow("không khớp khách hàng");
    expect(apiClient.post).toHaveBeenCalledTimes(1);
});

it("submits an explicit non-applicable answer independently of a NONE preference", async () => {
    const split = {
        ...input,
        taxonomyVersion: "hvtra-tea-v2",
        criteria: {
            hardGroups: [{ attribute: "NATURAL_SWEETNESS", allowedValues: ["NONE"] }],
            softGroups: [],
            notApplicableAttributes: ["ADDED_SWEETNESS"],
        },
    };
    vi.mocked(apiClient.post)
        .mockResolvedValueOnce({ ...draft, ...split })
        .mockResolvedValueOnce({ ...submitted, ...split });

    await expect(tasteSurveyApi.submit(split, keys)).resolves.toMatchObject(split);
    expect(apiClient.post).toHaveBeenNthCalledWith(1, "/customer/taste-surveys", split, expect.anything());
});

it("does not submit a draft bound to a different manifest hash", async () => {
    vi.mocked(apiClient.post).mockResolvedValueOnce({ ...draft, taxonomySha256: "b".repeat(64) });

    await expect(tasteSurveyApi.submit(input, keys)).rejects.toThrow("không khớp");
    expect(apiClient.post).toHaveBeenCalledTimes(1);
});

it("rejects draft taxonomy and reads paginated customer history", async () => {
    vi.mocked(apiClient.get)
        .mockResolvedValueOnce({ state: "DRAFT" })
        .mockResolvedValueOnce({ content: [submitted] });

    await expect(tasteSurveyApi.currentTaxonomy()).rejects.toThrow("không hợp lệ");
    await expect(tasteSurveyApi.history("customer", 1)).resolves.toEqual({ content: [submitted] });
    expect(apiClient.get).toHaveBeenNthCalledWith(1, "/catalog/taste-taxonomies/current");
    expect(apiClient.get).toHaveBeenNthCalledWith(2, "/customer/customer/taste-surveys", { query: { page: 1, size: 5 } });
});
