import { ApiError } from "@/lib/api-client";

/** Private capability PUT: no JWT/internal headers; cross-origin credentials disabled. */
export function putAiReportOriginal(url: string, file: File, contentType: string,
  onProgress?: (percent: number) => void): Promise<void> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open("PUT", url);
    xhr.withCredentials = false;
    xhr.timeout = 300_000;
    xhr.setRequestHeader("Content-Type", contentType);
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable) onProgress?.(Math.min(100, Math.round(event.loaded * 100 / event.total)));
    };
    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) resolve();
      else reject(new ApiError({status: xhr.status, code: "UPLOAD_FAILED", message: "Không thể tải file lên."}));
    };
    xhr.onerror = xhr.ontimeout = () => reject(new ApiError({status: 0, code: "UPLOAD_NETWORK_ERROR", message: "Kết nối tải file bị gián đoạn."}));
    xhr.send(file);
  });
}
