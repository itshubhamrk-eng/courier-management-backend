package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.TemplateButton;
import com.courier.shared.exception.BusinessRuleException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns our {@code {{customerName}}}-style content into what Meta wants: positional
 * {@code {{1}}..{{n}}} in order of first appearance — the very order
 * {@link TemplateRenderer#positionalParameters} sends them in, which is what keeps an approved
 * template and a send in lock-step. Also enforces the Meta body rules we can check locally so an
 * admin gets a clear message instead of a Meta error code.
 */
public final class MetaTemplateConverter {

    static final int MAX_BODY = 1024;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final Pattern NAME = Pattern.compile("^[a-z0-9_]{1,512}$");

    private MetaTemplateConverter() {
    }

    public record Converted(String bodyText, List<String> variables, List<String> examples) {
    }

    public static void requireValidName(String name) {
        if (name == null || !NAME.matcher(name).matches()) {
            throw new BusinessRuleException("Meta template names may only use lowercase letters, digits and "
                    + "underscores (e.g. shipment_booked). Rename \"" + name + "\" first, then submit.");
        }
    }

    public static Converted convert(String content, Map<String, String> samples) {
        if (content == null || content.isBlank()) {
            throw new BusinessRuleException("Template content is empty.");
        }
        Map<String, Integer> index = new LinkedHashMap<>();
        StringBuilder body = new StringBuilder();
        Matcher matcher = PLACEHOLDER.matcher(content);
        int last = 0;
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!samples.containsKey(name)) {
                throw new BusinessRuleException("Unknown variable {{" + name + "}} — Meta needs a sample value "
                        + "for every variable, and this one has none.");
            }
            int position = index.computeIfAbsent(name, k -> index.size() + 1);
            body.append(content, last, matcher.start()).append("{{").append(position).append("}}");
            last = matcher.end();
        }
        body.append(content.substring(last));

        String text = body.toString().strip();
        if (text.length() > MAX_BODY) {
            throw new BusinessRuleException("Meta allows at most " + MAX_BODY + " characters in a template body; "
                    + "this one is " + text.length() + ".");
        }
        if (text.startsWith("{{") || text.endsWith("}}")) {
            throw new BusinessRuleException("Meta does not accept a template that starts or ends with a variable. "
                    + "Add a few words of text before the first and after the last {{variable}}.");
        }
        List<String> variables = new ArrayList<>(index.keySet());
        List<String> examples = new ArrayList<>();
        for (String variable : variables) {
            examples.add(samples.get(variable));
        }
        return new Converted(text, variables, examples);
    }

    /** @param example the sample for the header's single variable; null when it has none */
    public record ConvertedHeader(String text, String example) {
    }

    /** A button ready for Meta: a URL ending in a variable is {@code prefix{{1}}} plus an example. */
    public record ConvertedButton(TemplateButton.Type type, String text, String url, String urlExample,
                                  String phoneNumber) {
    }

    public static ConvertedHeader convertHeader(String header, Map<String, String> samples) {
        if (header == null || header.isBlank()) {
            return null;
        }
        Matcher m = PLACEHOLDER.matcher(header);
        if (!m.find()) {
            return new ConvertedHeader(header.strip(), null);
        }
        String name = m.group(1);
        if (!samples.containsKey(name)) {
            throw new BusinessRuleException("Unknown variable {{" + name + "}} in the header.");
        }
        return new ConvertedHeader(header.substring(0, m.start()) + "{{1}}" + header.substring(m.end()), samples.get(name));
    }

    public static List<ConvertedButton> convertButtons(List<TemplateButton> buttons, Map<String, String> samples) {
        List<ConvertedButton> out = new ArrayList<>();
        if (buttons == null) {
            return out;
        }
        for (TemplateButton b : buttons) {
            if (b.type() == TemplateButton.Type.PHONE_NUMBER) {
                out.add(new ConvertedButton(b.type(), b.text(), null, null, b.phoneNumber()));
                continue;
            }
            Matcher m = PLACEHOLDER.matcher(b.url());
            if (!m.find()) {
                out.add(new ConvertedButton(b.type(), b.text(), b.url(), null, null));
                continue;
            }
            String name = m.group(1);
            if (!samples.containsKey(name)) {
                throw new BusinessRuleException("Unknown variable {{" + name + "}} in the button \"" + b.text() + "\" link.");
            }
            String prefix = b.url().substring(0, m.start());
            out.add(new ConvertedButton(b.type(), b.text(), prefix + "{{1}}", prefix + samples.get(name), null));
        }
        return out;
    }
}
