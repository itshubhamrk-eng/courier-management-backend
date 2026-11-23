package com.courier.modules.communication.application;

/** The small pure bits the daily and branch report senders share. */
final class ReportSendSupport {

    static final int MAX_ATTEMPTS = 3;
    private static final int MAX_ERROR_LENGTH = 1000;

    private ReportSendSupport() {
    }

    /** The exception's message (or its class name), cut to the log column's 1000 characters. */
    static String errorText(RuntimeException e) {
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
    }

    /** "***3210": never log a full phone number. */
    static String maskPhone(String phone) {
        if (phone == null) {
            return "***";
        }
        return "***" + (phone.length() <= 4 ? phone : phone.substring(phone.length() - 4));
    }
}
