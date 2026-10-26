package com.example.backend.controller;

import com.example.backend.User; // 본인의 User 엔티티 패키지 경로에 맞게 수정하세요
import com.example.backend.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;

@Controller
public class ResetController {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public ResetController(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    @GetMapping("/reset-password")
    public String showResetPage(@RequestParam String token, Model model) {
        model.addAttribute("token", token);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    @ResponseBody
    public String resetPassword(@RequestBody ResetRequest request) {
        User user = userRepository.findByResetToken(request.getToken())
            .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 링크입니다."));

        if (user.getTokenExpiry() == null || user.getTokenExpiry().isBefore(LocalDateTime.now())) {
            return "링크가 만료되었습니다.";
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        
        user.setResetToken(null);
        user.setTokenExpiry(null);
        
        userRepository.save(user);
        
        return "비밀번호 변경 성공! 이제 로그인해주세요.";
    }
}

class ResetRequest {
    private String token;
    private String newPassword;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getNewPassword() { return newPassword; }
    public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}