package com.todolab.notification.service;

public record ExpoPushTicket(
        boolean successful,
        String providerMessageId,
        String errorCode,
        String errorMessage
) {

    public static ExpoPushTicket success(String providerMessageId) {
        return new ExpoPushTicket(true, providerMessageId, null, null);
    }

    public static ExpoPushTicket failure(String errorCode, String errorMessage) {
        return new ExpoPushTicket(false, null, errorCode, errorMessage);
    }
}
