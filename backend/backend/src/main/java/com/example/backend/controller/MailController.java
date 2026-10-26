package com.example.backend.controller;

import com.example.backend.service.MailService;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class MailController {
    private final MailService mailService;

    public MailController(MailService mailService) {
        this.mailService = mailService;
    }

    @PostMapping("/password/reset")
    public ResponseEntity<?> sendResetMail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        try {
            mailService.sendResetLink(email);
            return ResponseEntity.ok(Map.of("message", "이메일로 재설정 링크를 발송했습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", "존재하지 않는 이메일입니다."));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "메일 전송 실패: " + e.getMessage()));
        }
    }
}