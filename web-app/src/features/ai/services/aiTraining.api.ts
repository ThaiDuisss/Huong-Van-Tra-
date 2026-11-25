import { apiClient } from "@/api/api-client";

export type TrainingMode = "EXPLORE" | "FAMILIAR";

export interface TrainingJob {
  id: string;
  mode: TrainingMode;
  status: "RUNNING" | "SUCCEEDED" | "FAILED";
  started_at: string;
  finished_at: string | null;
  model_version: string | null;
  error: string | null;
}

export type TrainingOverview = Record<TrainingMode, {
  dataset: "CONFIGURED" | "UNAVAILABLE";
  job: TrainingJob | null;
  serving: { version: string | null; status: string; active: string | null; previous: string | null };
}>;

const route = "/recommendation/recommendations/training/jobs";

export const aiTrainingApi = {
  overview: (signal: AbortSignal) => apiClient.get<TrainingOverview>(route, {
    signal, cache: "no-store", retry: false,
  }),
  request: (mode: TrainingMode, signal: AbortSignal) => apiClient.post<TrainingJob>(route, { mode }, {
    signal, cache: "no-store", retry: false,
  }),
};
