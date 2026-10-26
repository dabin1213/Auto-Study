package com.example.backend.config;

import com.example.backend.User;
import com.example.backend.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.Optional;

/**
 * 구글 로그인 성공 직후, 이메일 로그인과 똑같이 세션에 userId·nickname을 넣는다.
 * 이후 컨트롤러와 JWT 필터는 이메일 로그인과 동일하게 동작한다.
 */
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;

    public OAuth2LoginSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        Object principal = authentication.getPrincipal();
        String email = (principal instanceof OAuth2User oauth) ? oauth.getAttribute("email") : null;

        Optional<User> user = (email == null) ? Optional.empty() : userRepository.findByEmail(email);
        if (user.isEmpty()) {
            // 구글 이메일 정보를 못 받았거나 DB에 사용자가 없으면 로그인 화면으로
            response.sendRedirect("/login");
            return;
        }

        HttpSession session = request.getSession(true);
        session.setAttribute("userId", user.get().getUserId());
        session.setAttribute("nickname", user.get().getNickname());

        response.sendRedirect("/home");
    }
}