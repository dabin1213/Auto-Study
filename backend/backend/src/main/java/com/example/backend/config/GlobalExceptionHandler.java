package com.example.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Value("${spring.servlet.multipart.max-file-size:100MB}")
    private String maxFileSize;

    private ResponseEntity<Map<String, Object>> body(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }

    // 업로드 용량 초과 (FILE_002, 413)
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException e) {
        return body(413, "FILE_002", "파일 크기는 " + maxFileSize + " 이하여야 합니다.");
    }

    // 그 밖의 업로드 요청 오류 (파일 없이 전송, 깨진 요청 등)
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<Map<String, Object>> badUpload(MultipartException e) {
        log.warn("업로드 요청 오류: {}", e.getMessage());
        return body(400, "FILE_004", "파일 업로드 요청이 올바르지 않습니다. 파일을 다시 선택해주세요.");
    }

    // 요청 본문(JSON)이 깨졌거나 비어 있음
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException e) {
        return body(400, "REQ_001", "요청 형식이 올바르지 않습니다.");
    }

    // DB 오류: 연결 실패(503) / 중복·참조 무결성 위반(409, 400) / 그 외(500)
    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    public ResponseEntity<Map<String, Object>> database(Exception e) {
        if (e instanceof CannotCreateTransactionException
                || e instanceof DataAccessResourceFailureException
                || e instanceof TransientDataAccessResourceException
                || causeNameIs(e, "SQLTransientConnectionException", "SQLNonTransientConnectionException",
                        "JDBCConnectionException", "CommunicationsException",
                        "CJCommunicationsException", "ConnectException")) {
            log.error("DB 연결 실패", e);
            return body(503, "DB_003", "데이터베이스에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.");
        }
        if (causeMessageHas(e, "Duplicate entry")) {
            return body(409, "DB_001", "이미 존재하는 값입니다.");
        }
        if (causeMessageHas(e, "foreign key")) {
            return body(400, "DB_002", "연결된 데이터를 찾을 수 없습니다.");
        }
        log.error("DB 처리 오류", e);
        return body(500, "SERVER_001", "데이터 처리 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.");
    }

    // 그 밖의 예외: 로그인 화면으로 튕기지 않고 JSON으로 안내
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> fallback(RuntimeException e) {
        if (e instanceof ErrorResponse er) {   // 스프링이 정한 상태 코드(404 등)는 그대로 유지
            return body(er.getStatusCode().value(), "REQ_002", "요청을 처리할 수 없습니다.");
        }
        if (e instanceof TypeMismatchException) {   // 주소의 숫자 자리에 글자가 들어온 경우 등
            return body(400, "REQ_001", "요청 값이 올바르지 않습니다.");
        }
        log.error("처리되지 않은 예외", e);
        return body(500, "SERVER_001", "서버에서 오류가 발생했어요. 잠시 후 다시 시도해주세요.");
    }

    // ---- 예외의 원인(cause)을 따라가며 검사 ----

    private boolean causeNameIs(Throwable e, String... names) {
        Throwable t = e;
        for (int depth = 0; t != null && depth < 15; depth++) {
            String n = t.getClass().getSimpleName();
            for (String name : names) {
                if (n.equals(name)) return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private boolean causeMessageHas(Throwable e, String text) {
        String lower = text.toLowerCase();
        Throwable t = e;
        for (int depth = 0; t != null && depth < 15; depth++) {
            String m = t.getMessage();
            if (m != null && m.toLowerCase().contains(lower)) return true;
            t = t.getCause();
        }
        return false;
    }
}