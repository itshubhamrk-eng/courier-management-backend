package com.courier.modules.communication.application;

import java.time.LocalDate;

/**
 * The morning business summary: for every company that has WhatsApp enabled and lists at least
 * one {@code dailyReportPhones} number in its channel config, send that number the figures for
 * {@code reportDate} using the company's {@code DAILY_BUSINESS_SUMMARY} WhatsApp template (edited,
 * submitted to Meta and switched on/off in Communication > Templates). No
 * {@code @PreAuthorize} — called only from {@code DailyReportJob} on a scheduler thread with no
 * authenticated caller, never exposed through a controller.
 */
public interface DailyReportService {

    /** Config key (inside the WhatsApp {@code config_json}): comma/space separated phone numbers. */
    String CONFIG_PHONES = "dailyReportPhones";

    /** @return how many sends were attempted this pass (already-sent recipients don't count) */
    int sendReportsFor(LocalDate reportDate);
}
