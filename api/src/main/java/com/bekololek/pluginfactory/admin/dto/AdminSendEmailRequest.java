package com.bekololek.pluginfactory.admin.dto;

public record AdminSendEmailRequest(String recipientEmail, String template, String customMessage) {}
