package com.example.backend;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class UploadController {

    private static final String UPLOAD_DIR = "uploads/";
    private final StudyContentRepository studyContentRepository;

    public UploadController(StudyContentRepository studyContentRepository) {
        this.studyContentRepository = studyContentRepository;
    }

    private ResponseEntity<?> fail(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("error", code, "message", message));
    }

    // 경로 부분(../ 등)을 떼고 파일 이름만 남김
    private String safeName(String original) {
        String name = original.replace("\\", "/");
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        return name;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@RequestParam("file") MultipartFile file, HttpSession session) {
        Long userId = (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "로그인이 필요합니다."));
        }

        // 1) 파일 이름·확장자 검사 (FILE_001, 400)
        String original = file.getOriginalFilename();
        if (original == null || safeName(original).isEmpty()
                || !original.toLowerCase().endsWith(".pdf")) {
            return fail(400, "FILE_001", "PDF 파일만 업로드 가능합니다.");
        }
        String originalName = safeName(original);

        // 2) 빈 파일 검사 (FILE_003, 422)
        if (file.isEmpty()) {
            return fail(422, "FILE_003", "텍스트 추출 불가 파일입니다. (빈 파일)");
        }

        // 3) 메모리에서 먼저 PDF를 열어 글자를 추출 → 실패하면 디스크에 아무것도 남기지 않음
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            return fail(500, "SERVER_001", "파일을 읽는 중 오류가 발생했어요. 다시 시도해주세요.");
        }

        String extractedText;
        try (PDDocument document = Loader.loadPDF(bytes)) {
            extractedText = new PDFTextStripper().getText(document);
        } catch (InvalidPasswordException e) {
            return fail(422, "FILE_003", "암호가 걸린 PDF는 읽을 수 없어요. 암호를 해제한 파일을 올려주세요.");
        } catch (IOException e) {
            // 이름만 .pdf로 바꾼 파일, 깨진 파일 등
            return fail(422, "FILE_003", "텍스트 추출 불가 파일입니다. 올바른 PDF 파일인지 확인해주세요.");
        }

        // 4) 글자가 없는 PDF (스캔 이미지 등) (FILE_003, 422)
        if (extractedText == null || extractedText.isBlank()) {
            return fail(422, "FILE_003", "텍스트 추출 불가 파일입니다. (스캔한 이미지 PDF는 지원하지 않아요)");
        }

        // 5) 여기까지 통과한 파일만 디스크에 저장
        String storedName = System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8) + ".pdf";
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            Files.write(Paths.get(UPLOAD_DIR + storedName), bytes);
        } catch (IOException e) {
            return fail(500, "SERVER_001", "파일을 저장하는 중 오류가 발생했어요. 잠시 후 다시 시도해주세요.");
        }

        StudyContent content = new StudyContent();
        content.setUserId(userId);
        content.setFileName(originalName);
        content.setExtractedText(extractedText);
        content.setCreatedAt(LocalDateTime.now());
        studyContentRepository.save(content);

        return ResponseEntity.ok(Map.of(
                "message", "업로드 성공",
                "contentId", content.getContentId(),
                "fileName", originalName,
                "textLength", extractedText.length()
        ));
    }
}