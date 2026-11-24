import { useId, useRef, useState, type FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

import { useAuthorization } from "@/auth/useAuthorization";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api-client";
import {
    tasteSurveyApi,
    type TasteAttribute,
    type TasteCriteria
} from "../services/tasteSurvey.api";

interface SurveyContext {
    invoiceId: string;
    customerId: string;
}

interface Selection {
    values: string[];
    hard: boolean;
    notApplicable?: boolean;
}

type Submission = Parameters<typeof tasteSurveyApi.submit>;

const ordered = <T extends { displayOrder: number }>(items: T[]) =>
    [...items].sort((a, b) => a.displayOrder - b.displayOrder);

const submittedDate = new Intl.DateTimeFormat("vi-VN", {
    dateStyle: "short",
    timeStyle: "short",
    timeZone: "Asia/Ho_Chi_Minh"
});

function message(error: Error) {
    return error instanceof ApiError ? error.message : "Vui lòng tải lại hoặc thử gửi lại.";
}

export function InvoiceTasteSurvey(props: SurveyContext) {
    const { hasAnyRole } = useAuthorization();
    const [visibility, setVisibility] = useState<"open" | "closed" | null>(null);
    const sectionId = useId();
    if (!hasAnyRole(["SALE_STAFF", "MANAGER", "SUPER_ADMIN"])) return null;
    const open = visibility === "open";

    return (
        <section className="border-b border-[#edf0f4] py-5">
            <Button
                type="button"
                variant="secondary"
                aria-expanded={open}
                aria-controls={sectionId}
                onClick={() => setVisibility(open ? "closed" : "open")}
            >
                {open ? "Thu gọn khảo sát khẩu vị" : "Khảo sát khẩu vị"}
            </Button>
            <div id={sectionId} hidden={!open}>
                {visibility ? <SurveyForm {...props} /> : null}
            </div>
        </section>
    );
}

function SurveyForm({ invoiceId, customerId }: SurveyContext) {
    const queryClient = useQueryClient();
    const [page, setPage] = useState(0);
    const [selections, setSelections] = useState<Record<string, Selection>>({});
    const [selectionVersion, setSelectionVersion] = useState("");
    const [submitted, setSubmitted] = useState(false);
    const [pending, setPending] = useState<Submission | null>(null);
    const sending = useRef(false);
    const historyKey = ["customer-taste-surveys", customerId];
    const taxonomy = useQuery({
        queryKey: ["current-taste-taxonomy"],
        queryFn: tasteSurveyApi.currentTaxonomy,
        retry: false
    });
    const history = useQuery({
        queryKey: [...historyKey, page],
        queryFn: () => tasteSurveyApi.history(customerId, page),
        retry: false
    });
    const save = useMutation({
        retry: false,
        mutationFn: ([input, keys]: Submission) => tasteSurveyApi.submit(input, keys),
        onSuccess: () => {
            setPending(null);
            setSelections({});
            setSubmitted(true);
            setPage(0);
            void queryClient.invalidateQueries({ queryKey: historyKey });
        },
        onSettled: () => { sending.current = false; }
    });

    const release = taxonomy.isSuccess ? taxonomy.data : undefined;
    const identity = release ? `${release.versionCode}:${release.manifestSha256}` : "";
    const answers = selectionVersion === identity ? selections : {};
    const hasAnswers = Object.values(answers).some(({ values }) => values.length > 0);

    function update(attribute: TasteAttribute, selection: Selection) {
        setSubmitted(false);
        setSelectionVersion(identity);
        setSelections({ ...answers, [attribute.attributeCode]: selection });
    }

    function send(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (sending.current || (!pending && (!release || !hasAnswers || taxonomy.isFetching))) return;

        let submission = pending;
        if (!submission && release) {
            const criteria: TasteCriteria = { hardGroups: [], softGroups: [] };
            const notApplicableAttributes: string[] = [];
            for (const [attribute, { values, hard, notApplicable }] of Object.entries(answers)) {
                if (notApplicable) {
                    notApplicableAttributes.push(attribute);
                    continue;
                }
                if (!values.length) continue;
                if (hard) {
                    criteria.hardGroups.push({ attribute, allowedValues: values });
                } else {
                    criteria.softGroups.push({ attribute, priorities: values.map((value) => ({ value, weight: 1 })) });
                }
            }
            if (notApplicableAttributes.length) criteria.notApplicableAttributes = notApplicableAttributes;
            if (tooManyGroups) return;
            submission = [{
                customerId,
                originInvoiceId: invoiceId,
                taxonomyVersion: release.versionCode,
                taxonomySha256: release.manifestSha256,
                criteria
            }, { create: crypto.randomUUID(), submit: crypto.randomUUID() }];
            setPending(submission);
        }
        if (submission) {
            sending.current = true;
            save.mutate(submission);
        }
    }

    const rejection = save.error instanceof ApiError && [400, 403, 404, 409, 412, 422, 428].includes(save.error.status);
    const hardGroups = Object.values(answers).filter(({ values, hard }) => values.length && hard).length;
    const softGroups = Object.values(answers).filter(({ values, hard }) => values.length && !hard).length;
    const tooManyGroups = hardGroups > 32 || softGroups > 32;

    return (
        <div className="mt-4 space-y-5 text-sm">
            {taxonomy.isPending ? <p role="status">Đang tải tiêu chí khẩu vị…</p> : null}
            {taxonomy.isError ? (
                <div className="flex flex-wrap items-center gap-3">
                    <p role="status">
                        {taxonomy.error instanceof ApiError && taxonomy.error.isNotFound
                            ? "Chưa có bộ tiêu chí khẩu vị đã công bố."
                            : "Không tải được bộ tiêu chí khẩu vị."}
                    </p>
                    <Button type="button" variant="secondary" size="sm" onClick={() => void taxonomy.refetch()}>
                        Tải lại
                    </Button>
                </div>
            ) : null}
            {release || pending ? (
                <form className="space-y-4" onSubmit={send}>
                    {release ? (
                        <fieldset className="grid gap-3 sm:grid-cols-2" disabled={pending !== null}>
                            <legend className="mb-2 font-semibold">Chọn khẩu vị của khách</legend>
                            {ordered(release.manifest.attributes).map((attribute) => (
                                <TasteQuestion
                                    key={attribute.attributeCode}
                                    attribute={attribute}
                                    selection={answers[attribute.attributeCode] ?? { values: [], hard: false }}
                                    onChange={(selection) => update(attribute, selection)}
                                />
                            ))}
                        </fieldset>
                    ) : null}
                    {tooManyGroups ? <p role="alert">Chọn tối đa 32 nhóm cho mỗi kiểu tiêu chí.</p> : null}
                    {save.isError ? <p role="alert">{message(save.error)}</p> : null}
                    {rejection ? (
                        <Button
                            type="button"
                            variant="secondary"
                            onClick={() => {
                                setPending(null);
                                save.reset();
                                void taxonomy.refetch();
                            }}
                        >
                            Quay lại chỉnh câu trả lời
                        </Button>
                    ) : null}
                    <Button
                        type="submit"
                        disabled={save.isPending || rejection || (!pending && (!hasAnswers || tooManyGroups || taxonomy.isFetching))}
                    >
                        {save.isPending ? "Đang gửi…" : pending ? "Gửi lại khảo sát" : "Gửi khảo sát"}
                    </Button>
                    {submitted ? <p role="status">Đã gửi khảo sát khẩu vị.</p> : null}
                </form>
            ) : null}
            <div className="space-y-2">
                <h3 className="font-semibold">Lịch sử khảo sát</h3>
                {history.isPending ? <p role="status">Đang tải lịch sử…</p> : null}
                {history.isError ? (
                    <div className="flex flex-wrap items-center gap-3">
                        <p role="alert">Không tải được lịch sử khảo sát.</p>
                        <Button type="button" variant="secondary" size="sm" onClick={() => void history.refetch()}>
                            Tải lại lịch sử
                        </Button>
                    </div>
                ) : null}
                {history.data?.content.length === 0 ? <p>Khách chưa gửi khảo sát.</p> : null}
                {history.data?.content.length ? (
                    <ul className="space-y-2">
                        {history.data.content.map((survey) => (
                            <li key={survey.id} className="border-t border-[#edf0f4] py-2">
                                <details>
                                    <summary className="cursor-pointer">
                                        {survey.submittedAt ? submittedDate.format(new Date(survey.submittedAt)) : "Đã gửi"}
                                        {" · "}{survey.taxonomyVersion}
                                    </summary>
                                    <ul className="mt-2 space-y-1 pl-4">
                                        {survey.criteria.hardGroups.map((group) => (
                                            <li key={`hard-${group.attribute}`}>{group.attribute} · Bắt buộc · {group.allowedValues.join(", ")}</li>
                                        ))}
                                        {survey.criteria.softGroups.map((group) => (
                                            <li key={`soft-${group.attribute}`}>{group.attribute} · Ưu tiên · {group.priorities.map(({ value }) => value).join(", ")}</li>
                                        ))}
                                        {survey.criteria.notApplicableAttributes?.map((attribute) => (
                                            <li key={`not-applicable-${attribute}`}>{attribute} · Không áp dụng</li>
                                        ))}
                                    </ul>
                                </details>
                            </li>
                        ))}
                    </ul>
                ) : null}
                {history.data && (!history.data.first || !history.data.last) ? (
                    <div className="flex gap-2">
                        <Button type="button" variant="secondary" size="sm" disabled={history.data.first} onClick={() => setPage(page - 1)}>
                            Trang trước
                        </Button>
                        <Button type="button" variant="secondary" size="sm" disabled={history.data.last} onClick={() => setPage(page + 1)}>
                            Trang sau
                        </Button>
                    </div>
                ) : null}
            </div>
        </div>
    );
}

function TasteQuestion({ attribute, selection, onChange }: {
    attribute: TasteAttribute;
    selection: Selection;
    onChange: (value: Selection) => void;
}) {
    const multiple = attribute.selectionKind === "ENUM_MULTI";
    const limit = Math.min(attribute.maxSelections, 32);

    function toggle(value: string) {
        if (selection.values.includes(value)) {
            onChange({ ...selection, values: selection.values.filter((item) => item !== value) });
        } else if (!multiple || value === "NONE" || selection.values.includes("NONE")) {
            onChange({ ...selection, values: [value], notApplicable: false });
        } else if (selection.values.length < limit) {
            onChange({ ...selection, values: [...selection.values, value], notApplicable: false });
        }
    }

    return (
        <fieldset className="rounded-lg border border-[#edf0f4] p-3">
            <legend className="px-1 font-medium">
                {attribute.label}{multiple ? ` (tối đa ${limit})` : ""}
            </legend>
            <div className="flex flex-wrap gap-x-4 gap-y-2">
                {ordered(attribute.values).map((option) => (
                    <label key={option.valueCode} className="flex items-center gap-1.5">
                        <input
                            type="checkbox"
                            checked={selection.values.includes(option.valueCode)}
                            onChange={() => toggle(option.valueCode)}
                        />
                        {option.label}
                    </label>
                ))}
                {attribute.allowsNotApplicable === true ? (
                    <label className="flex items-center gap-1.5">
                        <input
                            type="checkbox"
                            checked={selection.notApplicable === true}
                            onChange={(event) => onChange({
                                values: [], hard: false, notApplicable: event.target.checked
                            })}
                        />
                        Không áp dụng
                    </label>
                ) : null}
            </div>
            {selection.values.length ? (
                <label className="mt-2 flex items-center gap-1.5">
                    <input
                        type="checkbox"
                        checked={selection.hard}
                        onChange={(event) => onChange({ ...selection, hard: event.target.checked })}
                    />
                    Bắt buộc
                </label>
            ) : null}
        </fieldset>
    );
}
