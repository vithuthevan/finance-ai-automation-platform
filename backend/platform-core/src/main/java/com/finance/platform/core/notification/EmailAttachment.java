package com.finance.platform.core.notification;

public record EmailAttachment(String filename, String contentType, byte[] content) {
}
