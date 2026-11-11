package com.rental.platform.exception;

import lombok.Getter;

/**
 * 业务异常 —— 统一的异常类型，贯穿 Controller -> Service 层
 * <p>
 * 使用方式：
 * <pre>{@code
 * throw new BusinessException("房源不存在");
 * throw new BusinessException(400, "标题不能为空");
 * }</pre>
 */
@Getter
public class BusinessException extends RuntimeException {

    /** 业务错误码，默认 400 */
    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /** 快捷创建 404 异常 */
    public static BusinessException notFound(String message) {
        return new BusinessException(404, message);
    }

    /** 快捷创建 401 异常 */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    /** 快捷创建 403 异常 */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }
}
