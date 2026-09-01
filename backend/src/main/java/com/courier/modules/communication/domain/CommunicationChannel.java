package com.courier.modules.communication.domain;

/** The three supported outbound channels. Kept a closed set — a fourth channel is a real
 *  design decision (a new provider abstraction, new settings-screen card), not a config
 *  toggle. */
public enum CommunicationChannel {
    WHATSAPP,
    SMS,
    EMAIL
}
