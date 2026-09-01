package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.CommunicationEventType;
import com.courier.modules.communication.domain.ShipmentSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fills every {@code {{variable}}} the brief lists — {@code customerName}, {@code
 * companyName}, {@code shipmentNumber}, {@code trackingNumber}, {@code pickupLocation},
 * {@code deliveryLocation}, {@code amount}, {@code deliveryDate}, {@code receiverName},
 * {@code trackingUrl}, {@code podUrl}. An unrecognised {@code {{x}}} in a company-edited
 * template is left verbatim rather than silently dropped — the same "flag, don't guess"
 * discipline as everywhere else in this project.
 */
@Component
public class TemplateRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM);

    private final String trackingUrlTemplate;

    public TemplateRenderer(
            @Value("${app.communication.tracking-url-template:http://localhost:4200/track/{trackingNumber}}")
            String trackingUrlTemplate) {
        this.trackingUrlTemplate = trackingUrlTemplate;
    }

    /**
     * @param recipientName the resolved recipient's own display name — fills
     *                      {@code {{customerName}}}, distinct from {@code {{receiverName}}}
     *                      which is always the shipment's own receiver regardless of who
     *                      the message is actually addressed to
     */
    public String render(String content, ShipmentSnapshot shipment, CommunicationEventType eventType,
                          String recipientName) {
        return substitute(content, variables(shipment, recipientName));
    }

    public Map<String, String> variables(ShipmentSnapshot shipment, String recipientName) {
        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("customerName", blank(recipientName));
        vars.put("companyName", blank(shipment.companyName()));
        vars.put("shipmentNumber", blank(shipment.shipmentNumber()));
        vars.put("trackingNumber", blank(shipment.trackingNumber()));
        vars.put("pickupLocation", blank(shipment.pickupLocation()));
        vars.put("deliveryLocation", blank(shipment.deliveryLocation()));
        vars.put("amount", formatAmount(shipment.amount()));
        vars.put("deliveryDate", formatDate(shipment.deliveryDate()));
        vars.put("receiverName", blank(shipment.receiver() == null ? null : shipment.receiver().name()));
        vars.put("trackingUrl", trackingUrlTemplate.replace("{trackingNumber}", blank(shipment.trackingNumber())));
        vars.put("podUrl", blank(shipment.podUrl()));
        return vars;
    }

    private String substitute(String content, Map<String, String> vars) {
        if (content == null) {
            return "";
        }
        Matcher matcher = PLACEHOLDER.matcher(content);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = vars.get(key);
            matcher.appendReplacement(out, Matcher.quoteReplacement(value != null ? value : matcher.group(0)));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String blank(String value) {
        return value == null ? "" : value;
    }

    private static String formatAmount(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }

    private static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE);
    }
}
