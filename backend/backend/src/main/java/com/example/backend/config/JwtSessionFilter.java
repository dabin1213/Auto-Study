package com.example.backend.config;

import com.example.backend.User;
import com.example.backend.UserRepository;
import com.example.backend.service.JwtService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * 세션 로그인에 JWT를 얹는 필터.
 *  - 세션으로 로그인된 상태: JWT 쿠키(액세스/리프레시)가 없으면 발급
 *  - 세션이 사라진 상태(서버 재시작, 세션 만료): 유효한 토큰이 있으면 세션을 복원
 *  - 로그아웃: 토큰 쿠키 삭제
 * 컨트롤러들은 기존처럼 session의 userId를 그대로 쓰면 된다.
 */
public class JwtSessionFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtSessionFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/css/") || path.startsWith("/js/") || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // 로그아웃: 토큰 쿠키를 지우고, 세션 복원은 하지 않는다 (로그아웃 자체는 그대로 진행)
        if (request.getRequestURI().equals("/logout")) {
            jwtService.clearCookies(request, response);
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Long sessionUserId = (session == null) ? null : (Long) session.getAttribute("userId");

        JwtService.Parsed access = jwtService.verify(
                jwtService.cookieValue(request, JwtService.ACCESS_COOKIE), JwtService.TYPE_ACCESS);
        JwtService.Parsed refresh = jwtService.verify(
                jwtService.cookieValue(request, JwtService.REFRESH_COOKIE), JwtService.TYPE_REFRESH);

        if (sessionUserId != null) {
            // 세션 로그인 상태: 토큰이 없거나 만료됐거나 다른 사용자 것이면 새로 발급
            if (!(access.isOk() && sessionUserId.equals(access.userId()))) {
                jwtService.addAccessCookie(request, response, sessionUserId);
            }
            if (!(refresh.isOk() && sessionUserId.equals(refresh.userId()))) {
                jwtService.addRefreshCookie(request, response, sessionUserId);
            }
        } else {
            // 세션이 없는 상태: 토큰으로 복원 시도
            Long userId = null;
            boolean needNewAccess = false;
            if (access.isOk()) {
                userId = access.userId();
            } else if (refresh.isOk()) {
                userId = refresh.userId();   // 액세스 토큰은 만료 → 리프레시 토큰으로 복원하고 새로 발급
                needNewAccess = true;
            }

            if (userId != null) {
                try {
                    Optional<User> user = userRepository.findById(userId);
                    if (user.isPresent()) {
                        HttpSession restored = request.getSession(true);
                        restored.setAttribute("userId", user.get().getUserId());
                        restored.setAttribute("nickname", user.get().getNickname());
                        if (needNewAccess) {
                            jwtService.addAccessCookie(request, response, userId);
                        }
                    } else {
                        jwtService.clearCookies(request, response);   // 탈퇴한 사용자 등
                    }
                } catch (Exception e) {
                    // DB 오류 등으로 복원하지 못해도 요청은 그대로 진행 (쿠키는 지우지 않아서 DB가 살아나면 다시 복원됨)
                    logger.warn("JWT 세션 복원 실패: " + e.getMessage());
                }
            } else if (access.result() != JwtService.Result.MISSING
                    || refresh.result() != JwtService.Result.MISSING) {
                // 만료됐거나 위조된 토큰: 쓸 수 없으니 쿠키 정리
                jwtService.clearCookies(request, response);
            }
        }

        chain.doFilter(request, response);
    }
}