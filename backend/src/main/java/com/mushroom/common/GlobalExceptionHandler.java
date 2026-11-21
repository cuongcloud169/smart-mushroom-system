package com.mushroom.common;

import java.sql.SQLException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Chuyển mọi lỗi thành JSON {"error": "..."} với mã HTTP phù hợp. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> api(ApiException e) {
        return body(e.getStatus(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
        var fe = e.getBindingResult().getFieldError();
        String msg = fe != null ? fe.getField() + ": " + fe.getDefaultMessage() : "Dữ liệu không hợp lệ";
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingRequestHeaderException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> badFormat(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "Sai định dạng dữ liệu");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> noEndpoint(NoResourceFoundException e) {
        return body(HttpStatus.NOT_FOUND, "Không tìm thấy endpoint");
    }

    /** Lỗi ràng buộc của PostgreSQL: trùng dữ liệu, khóa ngoại, CHECK... */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integrity(DataIntegrityViolationException e) {
        String state = e.getMostSpecificCause() instanceof SQLException s ? s.getSQLState() : null;
        if ("23505".equals(state)) return body(HttpStatus.CONFLICT, "Dữ liệu bị trùng");
        if ("23503".equals(state)) return body(HttpStatus.CONFLICT, "Dữ liệu liên quan không tồn tại hoặc đang được sử dụng");
        if ("23514".equals(state)) return body(HttpStatus.BAD_REQUEST, "Giá trị không hợp lệ");
        if ("23502".equals(state)) return body(HttpStatus.BAD_REQUEST, "Thiếu trường bắt buộc");
        return body(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> other(Exception e) {
        log.error("Lỗi không xử lý được", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi máy chủ");
    }
}
