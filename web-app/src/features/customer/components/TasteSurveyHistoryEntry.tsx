import { useQuery } from "@tanstack/react-query";
import { tasteSurveyApi, type TasteSurvey, type TasteTaxonomy } from "../services/tasteSurvey.api";

export function TasteSurveyHistoryEntry({ survey, current }: { survey: TasteSurvey; current?: TasteTaxonomy }) {
  const { taxonomyVersion, taxonomySha256, criteria } = survey;
  const exact = current?.versionCode === taxonomyVersion && current.manifestSha256 === taxonomySha256;
  const release = useQuery({
    queryKey: ["taste-taxonomy-release", taxonomyVersion, taxonomySha256],
    queryFn: ({ signal }) => tasteSurveyApi.taxonomyRelease(taxonomyVersion, taxonomySha256, signal),
    enabled: !exact,
    retry: false,
    staleTime: Infinity,
  });
  const attributes = (exact ? current : release.data)?.manifest.attributes ?? [];
  const lookup = (code: string) => attributes.find((attribute) => attribute.attributeCode === code);
  const label = (code: string) => lookup(code)?.label ?? code;
  const values = (code: string, selected: string[]) => selected.map((value) =>
    lookup(code)?.values.find((option) => option.valueCode === value)?.label ?? value).join(", ");
  const groups = [
    { label: "Tiêu chí bắt buộc", items: criteria.hardGroups.map((group) => ({ code: group.attribute, values: group.allowedValues })) },
    { label: "Tiêu chí ưu tiên", items: criteria.softGroups.map((group) => ({
      code: group.attribute, values: group.priorities.map((priority) => priority.value),
    })) },
  ];
  return (
    <article className="space-y-2 rounded-xl border border-neutral-200 bg-white p-4 text-xs text-neutral-600 shadow-xs">
      <p className="border-b border-neutral-100 pb-2 text-neutral-500">
        {survey.submittedAt ? new Date(survey.submittedAt).toLocaleString("vi-VN") : "Chưa ghi nhận ngày"}
      </p>
      {!exact && release.isPending && <p role="status">Đang tải nhãn khẩu vị gốc…</p>}
      {!exact && release.isError && <p role="alert">Không tải được nhãn gốc. Đang hiển thị mã đã lưu.</p>}
      {groups.filter((group) => group.items.length > 0).map((group) => (
        <div key={group.label}>
          <p className="font-semibold text-emerald-800">{group.label}</p>
          <ul className="mt-1 list-disc pl-5">
            {group.items.map((item) => <li key={item.code}><strong>{label(item.code)}:</strong> {values(item.code, item.values)}</li>)}
          </ul>
        </div>
      ))}
      {Boolean(criteria.notApplicableAttributes?.length) &&
        <p><strong>Không áp dụng:</strong> {criteria.notApplicableAttributes?.map(label).join(", ")}</p>}
    </article>
  );
}
