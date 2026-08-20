package com.courier.modules.pod.application.provider;

/** Thrown by a {@link PodVerificationProvider} that cannot analyze right now. The service
 *  layer must catch this and route to manual review — never assume success on failure. */
public class PodProviderUnavailableException extends RuntimeException {

    public PodProviderUnavailableException(String message) {
        super(message);
    }

    public PodProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
