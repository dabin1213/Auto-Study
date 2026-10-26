package com.example.backend.config;

import com.example.backend.UserRepository;
import com.example.backend.service.CustomOAuth2UserService;
import com.example.backend.service.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomOAuth2UserService customOAuth2UserService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    public SecurityConfig(CustomOAuth2UserService customOAuth2UserService,
                          JwtService jwtService,
                          UserRepository userRepository) {
        this.customOAuth2UserService = customOAuth2UserService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            // 세션 로그인에 JWT를 얹는 필터 (로그아웃 필터보다 먼저 실행)
            .addFilterBefore(new JwtSessionFilter(jwtService, userRepository), LogoutFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/", "/login", "/signup", "/home", "/dashboard", "/quiz", "/quiz/**", "/history", "/history/**",
                    "/team", "/team/**",
                    "/find-password", "/reset-password", "/error",
                    "/css/**", "/js/**", "/auth/**", "/api/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                // 구글 로그인 성공 시 세션에 userId·nickname을 넣고 홈으로 이동
                .successHandler(new OAuth2LoginSuccessHandler(userRepository))
            );
        return http.build();
    }
}