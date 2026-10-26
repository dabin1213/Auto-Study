package com.example.backend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    // 로그인 화면으로 이동
    @GetMapping("/login")
    public String loginPage() {
        return "login"; // templates/login.html 파일을 찾음
    }
}