package com.courier.modules.communication.domain;

/** The per-event-per-channel on/off switch — see {@code CommunicationTemplate}'s own doc
 *  for why this, not {@code CommunicationSetting.enabled}, is where "Company Admin can
 *  enable/disable each channel per event" actually lives. */
public enum TemplateStatus {
    ACTIVE,
    INACTIVE
}
