package com.example.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost("smtp.gmail.com");
        mailSender.setPort(587);
        
        mailSender.setUsername("gimdabin1013@gmail.com".trim());
        mailSender.setPassword("anclqgjtprzdzmgb".trim()); 

        Properties props = mailSender.getJavaMailProperties();
       props.put("mail.mime.charset", "UTF-8");
props.put("mail.smtp.auth", "true");
props.put("mail.smtp.starttls.enable", "true");
props.put("mail.smtp.starttls.required", "true");
props.put("mail.smtp.localhost", "localhost");
props.put("mail.debug", "true");

        return mailSender;
    }
}