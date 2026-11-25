import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import { ApiError } from "@/lib/api-client";
import { aiTrainingApi, type TrainingMode, type TrainingOverview } from "../services/aiTraining.api";

const states = {
  RUNNING: "Đang huấn luyện",
  SUCCEEDED: "Đã hoàn tất",
  FAILED: "Huấn luyện thất bại",
};
const timeFormat = new Intl.DateTimeFormat("vi-VN", {
  dateStyle: "short", timeStyle: "medium", timeZone: "Asia/Ho_Chi_Minh",
});
const jobErrors: Record<string, string> = {
  CAPTURE_CHECKSUM_MISMATCH: "Checksum dữ liệu huấn luyện không khớp.",
  INSUFFICIENT_TRAINING_OUTCOMES: "Dữ liệu huấn luyện chưa đủ đơn hợp lệ.",
  INSUFFICIENT_VALIDATION_OUTCOMES: "Dữ liệu kiểm định chưa đủ kết quả.",
  LOGISTIC_FIT_DID_NOT_CONVERGE: "Mô hình chưa hội tụ khi huấn luyện.",
  ISOLATED_TRAINING_WORKER_LOST: "Tiến trình huấn luyện đã dừng giữa chừng.",
  ISOLATED_TRAINING_WORKER_NOT_STARTED: "Tiến trình huấn luyện chưa khởi động.",
};

export function AiTrainingPanel() {
  const [mode, setMode] = useState<TrainingMode>("EXPLORE");
  const [overview, setOverview] = useState<TrainingOverview | null>(null);
  const [feedback, setFeedback] = useState("");
  const [unavailable, setUnavailable] = useState(false);
  const [loading, setLoading] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const submit = useRef<AbortController | null>(null);

  useEffect(() => {
    const request = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;

    async function load() {
      try {
        const data = await aiTrainingApi.overview(request.signal);
        if (request.signal.aborted) return;
        setUnavailable(false);
        setOverview(data);
        if (Object.values(data).some((item) => item.job?.status === "RUNNING")) {
          timer = setTimeout(() => void load(), 2_000);
        }
      } catch (error) {
        if (request.signal.aborted) return;
        setUnavailable(true);
        setOverview(null);
        setFeedback(error instanceof ApiError && error.status === 404
          ? "Huấn luyện cách ly chưa được bật."
          : "Không thể tải trạng thái huấn luyện.");
      }
    }
    void load();
    return () => { request.abort(); clearTimeout(timer); };
  }, [refresh]);

  useEffect(() => () => submit.current?.abort(), []);
  async function requestTraining() {
    if (submit.current) return;
    const request = new AbortController();
    submit.current = request;
    setLoading(true);
    setFeedback("");
    try {
      await aiTrainingApi.request(mode, request.signal);
      if (!request.signal.aborted) setRefresh((n) => n + 1);
    } catch (error) {
      if (!request.signal.aborted) {
        setFeedback(error instanceof ApiError && error.status === 409
          ? "Đã có một lượt huấn luyện đang chạy."
          : "Không gửi được yêu cầu huấn luyện.");
        setRefresh((n) => n + 1);
      }
    } finally {
      if (!request.signal.aborted) setLoading(false);
      if (submit.current === request) submit.current = null;
    }
  }

  const selected = overview?.[mode];
  const job = selected?.job;
  const busy = loading || Boolean(overview && Object.values(overview).some((item) => item.job?.status === "RUNNING"));
  const dataset = selected?.dataset === "CONFIGURED" ? "Đã cấu hình, kiểm tra trước khi chạy" :
    selected || unavailable ? "Chưa sẵn sàng" : "Đang tải";
  return (
    <section aria-label="Huấn luyện AI" className="grid gap-4 rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-base font-bold">Huấn luyện AI</h2>
      <div className="flex flex-wrap items-end gap-3">
        <label className="grid gap-1 text-sm font-medium">
          Chế độ
          <select className="rounded-lg border border-slate-300 px-3 py-2" value={mode}
            onChange={(event) => setMode(event.target.value === "FAMILIAR" ? "FAMILIAR" : "EXPLORE")}>
            <option value="EXPLORE">Muốn đổi vị</option>
            <option value="FAMILIAR">Không muốn đổi vị</option>
          </select>
        </label>
        <Button variant="primary" disabled={busy || selected?.dataset !== "CONFIGURED"}
          onClick={() => void requestTraining()}>{loading ? "Đang gửi…" : "Huấn luyện AI"}</Button>
        <Button onClick={() => setRefresh((n) => n + 1)}>Làm mới trạng thái</Button>
      </div>
      {feedback && <p role="status" className="text-sm text-slate-600">{feedback}</p>}
      <dl className="grid gap-2 text-sm sm:grid-cols-2">
        <div><dt className="font-semibold">Dữ liệu</dt><dd>{dataset}</dd></div>
        <div><dt className="font-semibold">Phiên bản đang phục vụ</dt><dd>{selected?.serving.version ?? "Chưa phục vụ"}</dd></div>
        <div><dt className="font-semibold">Lượt huấn luyện gần nhất</dt><dd>{job ? states[job.status] : "Chưa có"}</dd></div>
        <div><dt className="font-semibold">Phiên bản mới</dt><dd>{job?.model_version ?? "—"}</dd></div>
        {job && <div><dt className="font-semibold">Bắt đầu</dt><dd>{timeFormat.format(new Date(job.started_at))}</dd></div>}
        {job?.finished_at && <div><dt className="font-semibold">Kết thúc</dt><dd>{timeFormat.format(new Date(job.finished_at))}</dd></div>}
      </dl>
      {job?.error && <p role="alert" className="text-sm text-red-700">
        {jobErrors[job.error] ?? "Lượt huấn luyện gặp lỗi. Kiểm tra dữ liệu và thử lại."}
      </p>}
    </section>
  );
}
