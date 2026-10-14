package com.courier.modules.communication.application;

import com.courier.modules.communication.application.provider.WhatsAppProvider;
import com.courier.modules.communication.domain.CommunicationTemplate;
import com.courier.modules.communication.domain.TemplateButton;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an approved template plus a map of variable values into the exact parameters Meta wants:
 * body parameters, the header's one variable, and the value for every URL button that ends in a
 * variable. One place for both the shipment sender and the daily summary, so a template the admin
 * built with a header or a "Track shipment" button is sent the way Meta approved it.
 *
 * <p>Meta rejects an empty text parameter, so a blank value goes out as {@code "-"}.
 */
public final class WhatsAppPayloadBuilder {

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

    private WhatsAppPayloadBuilder() {
    }

    public static WhatsAppProvider.WhatsAppMessage build(String to, CommunicationTemplate template,
                                                          TemplateRenderer renderer, Map<String, String> vars,
                                                          ObjectMapper objectMapper) {
        List<String> body = renderer.positionalParameters(template.getContent(), vars);
        String header = singleVariable(template.getHeaderText(), vars);

        List<WhatsAppProvider.ButtonParameter> buttons = new ArrayList<>();
        List<TemplateButton> stored = TemplateComponentsCodec.readButtons(objectMapper, template.getButtonsJson());
        for (int i = 0; i < stored.size(); i++) {
            TemplateButton b = stored.get(i);
            if (b.type() == TemplateButton.Type.URL) {
                String value = singleVariable(b.url(), vars);
                if (value != null) {
                    buttons.add(new WhatsAppProvider.ButtonParameter(i, value));
                }
            }
        }
        return new WhatsAppProvider.WhatsAppMessage(to, template.getTemplateName(), body, template.getLanguage(),
                header, buttons);
    }

    /** The value of the one {{variable}} in {@code text}, "-" if blank; null when there is none. */
    private static String singleVariable(String text, Map<String, String> vars) {
        if (text == null) {
            return null;
        }
        Matcher m = VARIABLE.matcher(text);
        if (!m.find()) {
            return null;
        }
        String value = vars.get(m.group(1));
        return value == null || value.isBlank() ? "-" : value;
    }
}
