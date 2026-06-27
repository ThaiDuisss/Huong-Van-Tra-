import { apiClient } from "./api-client";

export type FilePurpose =
    | "USER_AVATAR"
    | "USER_DOCUMENT"
    | "PAYMENT_PROOF"
    | "INVOICE"
    | "PRODUCT_IMAGE"
    | "ITEM_IMAGE"
    | "CATEGORY_IMAGE"
    | "PROMOTION_IMAGE"
    | "BANNER_IMAGE";

export type FileVisibility = "PRIVATE" | "PUBLIC";

export interface InitUploadRequest {
    originalFilename: string;
    contentType: string;
    sizeBytes: number;
    purpose: FilePurpose;
    ownerType?: string;
    ownerId?: string;
    refType?: string;
    refId?: string;
}

export interface InitUploadResponse {
    fileId: string;
    visibility: FileVisibility;
    bucket: string;
    objectKey: string;
    uploadUrl: string;
    publicUrl: string | null;
    expiresInSeconds: number;
}

export interface DownloadUrlResponse {
    url: string;
    expiresInSeconds: number | null;
    visibility: FileVisibility;
}

export const fileApi = {
    /**
     * Bước 1: Khởi tạo upload file.
     * Tạo metadata file trong database với status = PENDING và trả về presigned upload URL.
     */
    initUpload(input: InitUploadRequest) {
        return apiClient.post<InitUploadResponse, InitUploadRequest>(
            "/file/init-upload",
            input
        );
    },

    /**
     * Bước 2 (Gọi sau khi đã PUT file lên MinIO): Xác nhận upload hoàn tất.
     * Kiểm tra file trên MinIO và chuyển status từ PENDING sang ACTIVE.
     */
    completeUpload(fileId: string) {
        return apiClient.post<string, void>(
            `/file/${encodeURIComponent(fileId)}/complete`
        );
    },

    /**
     * Bước 3 (Khi cần xem/tải): Lấy presigned download URL để tải/xem file.
     */
    downloadUrl(fileId: string) {
        return apiClient.get<DownloadUrlResponse>(
            `/file/${encodeURIComponent(fileId)}/download-url`
        );
    },

    /**
     * Helper orchestrator để thực hiện toàn bộ luồng upload file:
     * 1. Khởi tạo upload metadata & lấy uploadUrl (presigned URL).
     * 2. Upload file trực tiếp lên MinIO bằng HTTP PUT.
     * 3. Xác nhận hoàn tất upload với backend.
     *
     * @returns Thông tin khởi tạo upload ban đầu (chứa fileId, publicUrl nếu có, v.v.)
     */
    async uploadFile(
        file: File,
        purpose: FilePurpose,
        options?: Omit<InitUploadRequest, "originalFilename" | "contentType" | "sizeBytes" | "purpose">
    ): Promise<InitUploadResponse> {
        // 1. Init upload
        const initResponse = await this.initUpload({
            originalFilename: file.name,
            contentType: file.type,
            sizeBytes: file.size,
            purpose,
            ...options
        });

        // 2. Upload directly to MinIO using HTTP PUT
        const uploadRes = await fetch(initResponse.uploadUrl, {
            method: "PUT",
            headers: {
                "Content-Type": file.type
            },
            body: file
        });

        if (!uploadRes.ok) {
            throw new Error(`Failed to upload file to storage: ${uploadRes.statusText}`);
        }

        // 3. Complete upload
        await this.completeUpload(initResponse.fileId);

        return initResponse;
    }
};
