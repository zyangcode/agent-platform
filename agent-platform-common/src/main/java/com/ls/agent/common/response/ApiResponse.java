package com.ls.agent.common.response;

import java.util.Objects;

/**
 * Web 管理 API 的标准响应体。
 *
 * @param <T> 响应数据的类型
 * @param success 是否处理成功
 * @param code 响应状态码
 * @param message 响应消息
 * @param data 响应数据
 */
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data
) {

    /**
     * 成功响应的默认状态码。
     */
    public static final String OK_CODE = "OK";

    /**
     * 成功响应的默认消息。
     */
    public static final String OK_MESSAGE = "success";

    /**
     * ApiResponse 的规范构造函数。
     *
     * @param success 是否处理成功
     * @param code 响应状态码，不能为空
     * @param message 响应消息，不能为空
     * @param data 响应数据
     * @throws NullPointerException 如果 code 或 message 为空
     */
    public ApiResponse {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(message, "message must not be null");
    }

    /**
     * 创建一个成功的响应。
     *
     * @param <T> 数据类型
     * @param data 响应数据
     * @return 包含数据的成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, OK_CODE, OK_MESSAGE, data);
    }

    /**
     * 创建一个不带数据的成功响应。
     *
     * @return 不带数据的成功响应
     */
    public static ApiResponse<Void> ok() {
        return success(null);
    }

    /**
     * 创建一个失败的响应。
     *
     * @param <T> 数据类型
     * @param code 错误码
     * @param message 错误消息
     * @return 失败的响应
     */
    public static <T> ApiResponse<T> failure(String code, String message) {
        return new ApiResponse<>(false, code, message, null);
    }
}
