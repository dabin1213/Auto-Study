package com.example.backend.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

// 토큰 발급 , 검증 , 쿠키 처리 코드 
@Service
public class JwtService {

    public static final String ACCESS_COOKIE = "AS_ACCESS";
    public static final String REFRESH_COOKIE = "AS_REFRESH";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    // 쿠키는 토큰보다 60초 더 오래 둔다.
    // 그래야 서버가 '만료된 토큰'을 직접 확인하고 만료 코드를 돌려줄 수 있다.
    private static final int COOKIE_GRACE_SECONDS = 60;

    public enum Result { OK, MISSING, EXPIRED, INVALID }

    // 토큰 검증 결과: 상태, 사용자 ID, 만료 시각
    public record Parsed(Result result, Long userId, Date expiresAt) {
        public boolean isOk() { return result == Result.OK; }
    }

    private final SecretKey key;
    private final JwtParser parser;
    private final long accessSeconds;
    private final long refreshSeconds;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.access-seconds:3600}") long accessSeconds,
                      @Value("${jwt.refresh-seconds:604800}") long refreshSeconds) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("jwt.secret은 32자(32바이트) 이상이어야 합니다.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.parser = Jwts.parser().verifyWith(key).build();
        this.accessSeconds = accessSeconds;
        this.refreshSeconds = refreshSeconds;
    }

    public long getAccessSeconds() { return accessSeconds; }

    // ---------------- 토큰 발급 ----------------

    public String createAccessToken(Long userId) {
        return create(userId, TYPE_ACCESS, accessSeconds);
    }

    public String createRefreshToken(Long userId) {
        return create(userId, TYPE_REFRESH, refreshSeconds);
    }

    private String create(Long userId, String type, long seconds) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + seconds * 1000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    // ---------------- 토큰 검증 ----------------

    // 서명, 만료, 토큰 종류(access/refresh)를 모두 검사한다.
    public Parsed verify(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return new Parsed(Result.MISSING, null, null);
        }
        try {
            Claims claims = parser.parseSignedClaims(token).getPayload();
            if (!expectedType.equals(claims.get("type", String.class))) {
                return new Parsed(Result.INVALID, null, null);   // 리프레시 토큰을 액세스 자리에 쓰는 경우 등
            }
            return new Parsed(Result.OK, Long.valueOf(claims.getSubject()), claims.getExpiration());
        } catch (ExpiredJwtException e) {
            return new Parsed(Result.EXPIRED, null, null);       // 서명은 맞지만 기간이 지남
        } catch (JwtException | IllegalArgumentException e) {
            return new Parsed(Result.INVALID, null, null);       // 위조, 형식 오류
        }
    }

    // ---------------- 쿠키 ----------------

    public String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    public void addAccessCookie(HttpServletRequest request, HttpServletResponse response, Long userId) {
        addCookie(request, response, ACCESS_COOKIE, createAccessToken(userId),
                (int) (accessSeconds + COOKIE_GRACE_SECONDS));
    }

    public void addRefreshCookie(HttpServletRequest request, HttpServletResponse response, Long userId) {
        addCookie(request, response, REFRESH_COOKIE, createRefreshToken(userId),
                (int) (refreshSeconds + COOKIE_GRACE_SECONDS));
    }

    public void clearCookies(HttpServletRequest request, HttpServletResponse response) {
        addCookie(request, response, ACCESS_COOKIE, "", 0);
        addCookie(request, response, REFRESH_COOKIE, "", 0);
    }

    private void addCookie(HttpServletRequest request, HttpServletResponse response,
                           String name, String value, int maxAgeSeconds) {
        Cookie cookie = new Cookie(name, value);
        cookie.setHttpOnly(true);                 // JS에서 못 읽음 (XSS 방어)
        cookie.setSecure(request.isSecure());     // https일 때는 Secure
        cookie.setPath("/");
        cookie.setMaxAge(maxAgeSeconds);
        cookie.setAttribute("SameSite", "Lax");   // 다른 사이트에서 오는 POST에는 쿠키를 안 보냄
        response.addCookie(cookie);
    }
}