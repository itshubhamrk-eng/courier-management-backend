package com.courier.modules.support.application.command;

/**
 * @param internalNote true for a staff-only note, invisible to the requester — refused
 *                      for a caller who is not staff on this ticket
 */
public record ReplyCommand(String body, boolean internalNote) {
}
