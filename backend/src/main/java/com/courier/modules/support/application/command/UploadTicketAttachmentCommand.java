package com.courier.modules.support.application.command;

import java.util.UUID;

/** @param messageId null = attached at ticket creation, not to a specific reply */
public record UploadTicketAttachmentCommand(
        byte[] content, String filename, String contentType, UUID messageId) {
}
