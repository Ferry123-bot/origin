package com.yunshu.zhixue.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yunshu.zhixue.common.exception.ErrorCode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(int code, String message, T data, List<FieldErrorItem> fieldErrors) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(0, "ok", data, null);
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static ApiResponse<Void> fail(ErrorCode error) {
        return fail(error, error.defaultMessage(), null);
    }

    public static ApiResponse<Void> fail(ErrorCode error, String message) {
        return fail(error, message, null);
    }

    public static ApiResponse<Void> fail(ErrorCode error, String message, List<FieldErrorItem> fieldErrors) {
        return new ApiResponse<>(error.code(), message, null, fieldErrors);
    }
}
