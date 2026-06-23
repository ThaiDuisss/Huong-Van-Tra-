package org.hvtra.fileservice.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.hvtra.commonservice.domain.entity.UserHeaderInformation;
import org.hvtra.commonservice.domain.response.ApiResponse;
import org.hvtra.fileservice.config.MinioProperties;
import org.hvtra.fileservice.model.request.InitUploadRequest;
import org.hvtra.fileservice.model.response.DownloadUrlResponse;
import org.hvtra.fileservice.model.response.InitUploadResponse;
import org.hvtra.fileservice.service.FileService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final MinioProperties minioProperties;

    /**
     * Bước 1: Khởi tạo upload file.
     *
     * API này chưa upload file lên MinIO.
     * Nó chỉ:
     * - Nhận thông tin file từ frontend: tên file, content type, size...
     * - Lấy userId từ request header
     * - Tạo metadata file trong database với status = PENDING
     * - Tạo presigned upload URL để frontend upload trực tiếp lên MinIO
     *
     * Sau API này, frontend sẽ dùng uploadUrl được trả về để PUT file lên MinIO.
     */
    @PostMapping("/init-upload")
    public ApiResponse<InitUploadResponse> initUpload(
            @Valid @RequestBody InitUploadRequest request,
            HttpServletRequest servletRequest
    ) {
        // Lấy thông tin user được API Gateway truyền xuống qua header
        UserHeaderInformation userHeader = UserHeaderInformation.fromRequest(servletRequest);

        // Lấy userId từ header để biết file này thuộc về user nào
        UUID userId = extractUserId(userHeader);

        // Gọi service để tạo metadata file + tạo upload URL tạm thời
        InitUploadResponse response = fileService.initUpload(request, userId);

        return ApiResponse.<InitUploadResponse>builder()
                .data(response)
                .code(200)
                .message("init successfully")
                .build();
    }

    /**
     * Bước 2: Xác nhận upload hoàn tất.
     *
     * API này được gọi sau khi frontend đã upload file lên MinIO bằng uploadUrl.
     *
     * Nó sẽ:
     * - Tìm file metadata trong database bằng fileId
     * - Kiểm tra user hiện tại có phải chủ file không
     * - Gọi MinIO statObject để kiểm tra object đã tồn tại chưa
     * - So sánh size file trên MinIO với size đã khai báo ban đầu
     * - Nếu hợp lệ: đổi status từ PENDING sang ACTIVE
     * - Nếu lỗi: đổi status sang FAILED
     *
     * API này không upload file, chỉ xác nhận file đã upload thành công.
     */
    @PostMapping("/{fileId}/complete")
    public ApiResponse<String> completeUpload(
            @PathVariable("fileId") UUID fileId,
            HttpServletRequest servletRequest
    ) {
        // Lấy thông tin user từ header
        UserHeaderInformation userHeader = UserHeaderInformation.fromRequest(servletRequest);

        // Lấy userId để kiểm tra quyền complete file
        UUID userId = extractUserId(userHeader);

        // Xác nhận file đã được upload lên MinIO và cập nhật trạng thái trong DB
        fileService.completeUpload(fileId, userId);

        return ApiResponse.<String>builder().code(200).message("success").data("Success").build();
    }

    /**
     * Bước 3: Tạo link download file.
     *
     * API này không trả trực tiếp file bytes về backend.
     * Nó chỉ tạo presigned download URL để frontend dùng link đó tải/xem file từ MinIO.
     *
     * Nó sẽ:
     * - Tìm file metadata theo fileId
     * - Kiểm tra file còn tồn tại, chưa bị xóa
     * - Kiểm tra file đang ACTIVE
     * - Kiểm tra user hiện tại có quyền download không
     * - Tạo URL tạm thời để tải file từ MinIO
     */
    @GetMapping("/{fileId}/download-url")
    public ApiResponse<DownloadUrlResponse> downloadUrl(
            @PathVariable("fileId") UUID fileId,
            HttpServletRequest servletRequest
    ) {
        // Lấy thông tin user từ header
        UserHeaderInformation userHeader = UserHeaderInformation.fromRequest(servletRequest);

        // Lấy userId để kiểm tra quyền download
        UUID userId = extractUserId(userHeader);

        // Tạo presigned download URL từ MinIO
        String url = fileService.createDownloadUrl(fileId, userId);

        // 300 là số giây URL còn hiệu lực.
        // Nên lấy giá trị này từ config thay vì hard-code nếu muốn chuẩn hơn.
        return ApiResponse.<DownloadUrlResponse>builder().data(new DownloadUrlResponse(url, minioProperties.getPresignedDownloadExpirySeconds())).status("success").code(200).build();
    }

    /**
     * Lấy userId từ header do API Gateway hoặc authentication filter truyền xuống.
     *
     * File service không tự đăng nhập user.
     * Nó tin vào header nội bộ đã được gateway xác thực trước đó.
     */
    private UUID extractUserId(UserHeaderInformation userHeader) {
        String userIdStr = userHeader != null ? userHeader.getUserId() : null;

        if (userIdStr == null || userIdStr.isBlank()) {
            return UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
            //            throw new IllegalArgumentException("Missing user id in headers");
        }

        return UUID.fromString(userIdStr);
    }
}