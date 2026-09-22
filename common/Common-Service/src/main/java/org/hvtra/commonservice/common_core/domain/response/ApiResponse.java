package org.hvtra.commonservice.common_core.domain.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hvtra.commonservice.common_core.domain.constants.ApiStatus;
import org.hvtra.commonservice.common_core.domain.constants.CommonMessage;
import org.hvtra.commonservice.common_core.domain.constants.SuccessCode;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {
    private  String status ;
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .status(ApiStatus.SUCCESS)
                .code(SuccessCode.SUCCESS)
                .message(CommonMessage.SUCCESS)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .status(ApiStatus.SUCCESS)
                .code(SuccessCode.SUCCESS)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .status(ApiStatus.SUCCESS)
                .code(SuccessCode.CREATED)
                .message(message)
                .data(data)
                .build();
    }

    public static <T> ApiResponse<T> updated(String message, T data) {
        return ApiResponse.<T>builder()
                .status(ApiStatus.SUCCESS)
                .code(SuccessCode.UPDATED)
                .message(message)
                .data(data)
                .build();
    }

    public static ApiResponse<Void> deleted(String message) {
        return ApiResponse.<Void>builder()
                .status(ApiStatus.SUCCESS)
                .code(SuccessCode.DELETED)
                .message(message)
                .build();
    }

    public static ApiResponse<Void> successWithoutData(String message) {
        return ApiResponse.<Void>builder()
                .status(ApiStatus.SUCCESS)
                .code(SuccessCode.SUCCESS)
                .message(message)
                .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
                .status(ApiStatus.ERROR)
                .code(code)
                .message(message)
                .build();
    }
}