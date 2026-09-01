package com.courier.modules.communication.application;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** "Stop after configurable retry count" — bound from {@code app.communication.retry.*}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.communication.retry")
public class CommunicationRetryProperties {

    /** A row stops retrying once {@code attemptCount} reaches this. */
    private int maxAttempts = 5;

    /** Backoff between attempts, in minutes — flat, not exponential: message delivery
     *  failures in this domain (a bad number, a down gateway) are rarely load-related, so
     *  there is no thundering-herd risk this needs to back off from. */
    private int backoffMinutes = 15;
}
