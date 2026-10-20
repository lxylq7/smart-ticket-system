package com.example.ticket.common.exception;

import com.example.ticket.common.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({BindException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "请求参数或请求内容不合法");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException exception) {
        return failure(HttpStatus.NOT_FOUND, "NOT_FOUND", "请求的资源不存在");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .allow(exception.getSupportedHttpMethods() == null
                        ? new HttpMethod[0] : exception.getSupportedHttpMethods().toArray(HttpMethod[]::new))
                .body(ApiResponse.failure("METHOD_NOT_ALLOWED", "请求方法不支持"));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception) {
        return failure(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "请求内容类型不支持");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDatabaseError(DataAccessException exception) {
        log.warn("Database access failed ({})", exception.getClass().getSimpleName());
        return failure(HttpStatus.SERVICE_UNAVAILABLE, "DATABASE_UNAVAILABLE", "数据库访问失败，请检查数据库服务与连接配置");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpectedError(Exception exception) {
        log.error("Unhandled request exception", exception);
        return failure(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "服务内部异常，请稍后重试");
    }

    private ResponseEntity<ApiResponse<Void>> failure(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.failure(code, message));
    }
}
