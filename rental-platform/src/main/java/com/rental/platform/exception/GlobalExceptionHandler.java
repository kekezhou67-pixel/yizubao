package com.rental.platform.exception;

import com.rental.platform.utils.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;

/**
 * 全局异常处理器 —— 统一拦截所有 Controller 抛出的异常，返回规范的 Result 响应
 * <p>
 * 优先级规则（Spring 按最具体的 handler 匹配）：
 * 1. BusinessException → 返回对应的 code + message
 * 2. 参数校验失败 (MethodArgumentNotValidException / BindException) → 400
 * 3. 其他未知异常 → 500
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ==================== 业务异常 ====================

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    // ==================== 参数校验异常 ====================

    /** @Valid 校验 RequestBody 失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String detail = extractFieldErrors(e.getBindingResult());
        log.warn("参数校验失败: {}", detail);
        return Result.error(400, detail);
    }

    /** @Valid 校验 QueryString/PathVar 失败 */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String detail = extractFieldErrors(e.getBindingResult());
        log.warn("参数绑定失败: {}", detail);
        return Result.error(400, detail);
    }

    /** @Validated Service 层约束校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String detail = e.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("约束校验失败: {}", detail);
        return Result.error(400, detail);
    }

    // ==================== 兜底异常 ====================

    /** 未预期的运行时异常 */
    @ExceptionHandler(RuntimeException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleRuntimeException(RuntimeException e) {
        log.error("未预期的运行时异常", e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }

    /** 所有其他异常 */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e) {
        log.error("未知异常", e);
        return Result.error(500, "服务器内部错误，请稍后重试");
    }

    // ==================== 辅助方法 ====================

    private String extractFieldErrors(BindingResult result) {
        return result.getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
    }
}
