package com.example.backend.controller;

import com.example.backend.User;          // model 패키지 없음
import com.example.backend.UserRepository; // repository 패키지 없음

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;

@Controller
public class DashboardController {
    
    @Autowired
    private UserRepository userRepository;

    @GetMapping("/home")
    public String dashboard(Model model, HttpSession session) {
        model.addAttribute("currentPage", "home");
        String nickname = (String) session.getAttribute("nickname");
        if (nickname == null) nickname = "DB";
        model.addAttribute("nickname", nickname);
        return "dashboard";
    }

    @GetMapping("/quiz")
    public String quiz(Model model, HttpSession session) {
        model.addAttribute("currentPage", "quiz");
        String nickname = (String) session.getAttribute("nickname");
        if (nickname == null) nickname = "DB";
        model.addAttribute("nickname", nickname);
        return "quiz";
    }

    @GetMapping("/history")
    public String history(Model model, HttpSession session) {
        model.addAttribute("currentPage", "history");
        String nickname = (String) session.getAttribute("nickname");
        if (nickname == null) nickname = "DB";
        model.addAttribute("nickname", nickname);
        return "history";
    }

    @GetMapping("/team")
    public String team(Model model, HttpSession session) {
        model.addAttribute("currentPage", "team");
        String nickname = (String) session.getAttribute("nickname");
        if (nickname == null) nickname = "DB";
        model.addAttribute("nickname", nickname);
        return "team";
    }

    @GetMapping("/team/space")
    public String teamSpace(Model model, HttpSession session) {
        model.addAttribute("currentPage", "teamspace");
        String nickname = (String) session.getAttribute("nickname");
        if (nickname == null) nickname = "DB";
        model.addAttribute("nickname", nickname);
        return "team-space";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/find-password")
    public String findPassword() {
        return "find-password";
    }

    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

}