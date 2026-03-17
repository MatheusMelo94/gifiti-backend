package com.gifiti.api.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Profile("test")
public class NoOpEmailService implements EmailService {

    @Override
    public void send(String to, String subject, String body) {
        log.info("NoOp email — to: {}, subject: {}", to, subject);
    }
}
