package com.example.backend.controller;

import com.example.backend.User;
import com.example.backend.UserRepository;
import com.example.backend.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@RestController
public class AuthTokenController {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public AuthTokenController(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    // 액세스 토큰 재발급 (리프레시 토큰 쿠키 사용)
    @PostMapping("/auth/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        JwtService.Parsed refresh = jwtService.verify(
                jwtService.cookieValue(request, JwtService.REFRESH_COOKIE), JwtService.TYPE_REFRESH);

        if (refresh.result() == JwtService.Result.EXPIRED) {
            jwtService.clearCookies(request, response);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "ERR_REFRESH_TOKEN_EXPIRED",
                    "message", "로그인 유지 기간이 만료되었어요. 다시 로그인해주세요."));
        }
        if (!refresh.isOk()) {
            jwtService.clearCookies(request, response);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "ERR_INVALID_TOKEN",
                    "message", "유효하지 않은 토큰이에요. 다시 로그인해주세요."));
        }

        Optional<User> user = userRepository.findById(refresh.userId());
        if (user.isEmpty()) {
            jwtService.clearCookies(request, response);
            return ResponseEntity.status(401).body(Map.of(
                    "error", "ERR_INVALID_TOKEN",
                    "message", "유효하지 않은 토큰이에요. 다시 로그인해주세요."));
        }

        jwtService.addAccessCookie(request, response, refresh.userId());
        HttpSession session = request.getSession(true);
        session.setAttribute("userId", user.get().getUserId());
        session.setAttribute("nickname", user.get().getNickname());

        return ResponseEntity.ok(Map.of(
                "message", "토큰이 갱신되었습니다.",
                "expires_in", jwtService.getAccessSeconds()));
    }

    // 로그인 상태와 토큰 남은 시간 확인 (테스트, 시연용)
    @GetMapping("/auth/me")
    public ResponseEntity<?> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long userId = (session == null) ? null : (Long) session.getAttribute("userId");
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("logged_in", false));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("logged_in", true);
        body.put("user_id", userId);
        body.put("nickname", session.getAttribute("nickname"));
        body.put("access_token", tokenInfo(jwtService.verify(
                jwtService.cookieValue(request, JwtService.ACCESS_COOKIE), JwtService.TYPE_ACCESS)));
        body.put("refresh_token", tokenInfo(jwtService.verify(
                jwtService.cookieValue(request, JwtService.REFRESH_COOKIE), JwtService.TYPE_REFRESH)));
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> tokenInfo(JwtService.Parsed p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", p.result().name());   // OK / MISSING / EXPIRED / INVALID
        m.put("expires_in_seconds",
                p.isOk() ? Math.max(0, (p.expiresAt().getTime() - System.currentTimeMillis()) / 1000) : null);
        return m;
    }
}