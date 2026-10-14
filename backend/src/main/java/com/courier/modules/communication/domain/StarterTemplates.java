package com.courier.modules.communication.domain;

import com.courier.shared.exception.BusinessRuleException;

import java.util.Locale;

/**
 * What a brand-new template starts as, and what "Reset to default" puts back. For the events that
 * are seeded ({@link DefaultCommunicationTemplates}) it is exactly the seed; for the ones that are
 * not (arrived at branch, cancelled, the two return-to-origin events) it is a short, plain message
 * written to pass Meta's rules as it stands — a name Meta accepts, and a body that does not start
 * or end with a variable — so a new WhatsApp template can be submitted without first being fixed.
 */
public final class StarterTemplates {

    private StarterTemplates() {
    }

    /** The only event with no SMS/Email sibling. */
    public static boolean isAllowed(CommunicationEventType event, CommunicationChannel channel) {
        return event != CommunicationEventType.DAILY_BUSINESS_SUMMARY || channel == CommunicationChannel.WHATSAPP;
    }

    public static DefaultCommunicationTemplates.Seed starter(CommunicationEventType event, CommunicationChannel channel) {
        if (!isAllowed(event, channel)) {
            throw new BusinessRuleException("The daily business summary is sent on WhatsApp only.");
        }
        for (var seed : DefaultCommunicationTemplates.all()) {
            if (seed.eventType() == event && seed.channel() == channel) {
                return seed;
            }
        }
        String title = title(event);
        return switch (channel) {
            case WHATSAPP -> new DefaultCommunicationTemplates.Seed(event, channel,
                    event.name().toLowerCase(Locale.ROOT), null, whatsApp(event));
            case SMS -> new DefaultCommunicationTemplates.Seed(event, channel,
                    title + " - SMS", null, sms(event));
            case EMAIL -> new DefaultCommunicationTemplates.Seed(event, channel,
                    title + " - Email", emailSubject(event), emailBody(event));
        };
    }

    /** True when this event+channel has a seeded default (as opposed to a generated starter). */
    public static boolean isSeeded(CommunicationEventType event, CommunicationChannel channel) {
        return DefaultCommunicationTemplates.all().stream()
                .anyMatch(s -> s.eventType() == event && s.channel() == channel);
    }

    private static String title(CommunicationEventType event) {
        StringBuilder out = new StringBuilder();
        for (String w : event.name().toLowerCase(Locale.ROOT).split("_")) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return out.toString();
    }

    private static String whatsApp(CommunicationEventType event) {
        return switch (event) {
            case SHIPMENT_RECEIVED -> "Hi {{receiverName}}, your shipment {{trackingNumber}} has arrived at "
                    + "{{deliveryLocation}}. Track it here: {{trackingUrl}} - thank you!";
            case SHIPMENT_CANCELLED -> "Hi {{customerName}}, your shipment {{shipmentNumber}} (AWB {{trackingNumber}}) "
                    + "has been cancelled. Please contact {{companyName}} if this was not expected. Thank you!";
            case RTO_INITIATED -> "Hi {{customerName}}, your shipment {{trackingNumber}} could not be delivered and is "
                    + "being returned to {{pickupLocation}}. We will keep you updated. Thank you!";
            case RTO_DELIVERED -> "Hi {{customerName}}, your shipment {{trackingNumber}} has been returned to "
                    + "{{pickupLocation}}. Please contact {{companyName}} for any questions. Thank you!";
            default -> "Hi {{customerName}}, an update on your shipment {{trackingNumber}} from {{companyName}}. Thank you!";
        };
    }

    private static String sms(CommunicationEventType event) {
        return switch (event) {
            case SHIPMENT_RECEIVED -> "{{companyName}}: Shipment {{trackingNumber}} arrived at {{deliveryLocation}}. "
                    + "Track: {{trackingUrl}}";
            case SHIPMENT_CANCELLED -> "{{companyName}}: Shipment {{shipmentNumber}} (AWB {{trackingNumber}}) was cancelled.";
            case RTO_INITIATED -> "{{companyName}}: Shipment {{trackingNumber}} is being returned to {{pickupLocation}}.";
            case RTO_DELIVERED -> "{{companyName}}: Shipment {{trackingNumber}} was returned to {{pickupLocation}}.";
            default -> "{{companyName}}: update on shipment {{trackingNumber}}. Track: {{trackingUrl}}";
        };
    }

    private static String emailSubject(CommunicationEventType event) {
        return switch (event) {
            case SHIPMENT_RECEIVED -> "Your shipment {{shipmentNumber}} has arrived";
            case SHIPMENT_CANCELLED -> "Your shipment {{shipmentNumber}} was cancelled";
            case RTO_INITIATED -> "Your shipment {{shipmentNumber}} is being returned";
            case RTO_DELIVERED -> "Your shipment {{shipmentNumber}} was returned";
            default -> "An update on your shipment {{shipmentNumber}}";
        };
    }

    private static String emailBody(CommunicationEventType event) {
        String lead = "<p>Hi {{customerName}},</p>";
        return switch (event) {
            case SHIPMENT_RECEIVED -> "<p>Hi {{receiverName}},</p><p>Your shipment <b>{{shipmentNumber}}</b> "
                    + "(AWB {{trackingNumber}}) has arrived at {{deliveryLocation}}.</p>"
                    + "<p><a href=\"{{trackingUrl}}\">Track your shipment</a></p>";
            case SHIPMENT_CANCELLED -> lead + "<p>Your shipment <b>{{shipmentNumber}}</b> (AWB {{trackingNumber}}) "
                    + "has been cancelled. Please contact {{companyName}} if this was not expected.</p>";
            case RTO_INITIATED -> lead + "<p>Your shipment <b>{{shipmentNumber}}</b> could not be delivered and is "
                    + "being returned to {{pickupLocation}}.</p>";
            case RTO_DELIVERED -> lead + "<p>Your shipment <b>{{shipmentNumber}}</b> has been returned to "
                    + "{{pickupLocation}}.</p>";
            default -> lead + "<p>An update on your shipment <b>{{shipmentNumber}}</b> from {{companyName}}.</p>";
        };
    }
}
