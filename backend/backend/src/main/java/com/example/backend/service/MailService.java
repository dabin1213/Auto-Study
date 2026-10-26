package com.example.backend.service;

import com.example.backend.User;
import com.example.backend.UserRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.javamail.MimeMessageHelper;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    public MailService(JavaMailSender mailSender, UserRepository userRepository) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    @Transactional
public void sendResetLink(String email) throws MessagingException {
    User user = userRepository.findByEmail(email)
        .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 이메일입니다."));

    String token = UUID.randomUUID().toString();
    user.setResetToken(token);
    user.setTokenExpiry(LocalDateTime.now().plusMinutes(30));
    userRepository.save(user);

    String resetLink = "http://localhost:8080/reset-password?token=" + token;

    MimeMessage message = mailSender.createMimeMessage();
    MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

    String htmlContent = "<p>비밀번호를 재설정하시려면 아래 링크를 클릭하세요:</p>"
            + "<a href='" + resetLink + "'>비밀번호 재설정하기</a>";

    helper.setFrom("gimdabin1013@gmail.com");
    helper.setTo(email);
    helper.setSubject("[AutoStudy] 비밀번호 재설정 안내");
    helper.setText(htmlContent, true);

    mailSender.send(message);
}
}