package com.gifiti.api.service;

public interface EmailService {

    void send(String to, String subject, String body);
}
