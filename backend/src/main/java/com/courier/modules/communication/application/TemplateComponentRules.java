package com.courier.modules.communication.application;

import com.courier.modules.communication.domain.TemplateButton;
import com.courier.shared.exception.BusinessRuleException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Meta's limits on the parts of a template, checked when the admin saves rather than when Meta
 * rejects the submission hours later. Messages are written for the person typing, not for a log.
 */
public final class TemplateComponentRules {

    public static final Set<String> CATEGORIES = Set.of("UTILITY", "MARKETING", "AUTHENTICATION");
    /** The languages the app offers. A template approved in one code does not match a send in another. */
    public static final Set<String> LANGUAGES = Set.of("en_US", "en", "hi", "mr");

    static final int HEADER_MAX = 60;
    static final int FOOTER_MAX = 60;
    static final int BUTTON_TEXT_MAX = 25;
    static final int URL_MAX = 2000;
    static final int MAX_URL_BUTTONS = 2;
    static final int MAX_CALL_BUTTONS = 1;
    static final int SAMPLE_MAX = 200;

    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

    private TemplateComponentRules() {
    }

    public static String category(String value) {
        String v = value == null ? "" : value.trim().toUpperCase();
        if (!CATEGORIES.contains(v)) {
            throw new BusinessRuleException("Category must be UTILITY, MARKETING or AUTHENTICATION.");
        }
        return v;
    }

    public static String language(String value) {
        String v = value == null ? "" : value.trim();
        if (!LANGUAGES.contains(v)) {
            throw new BusinessRuleException("Language must be one of " + String.join(", ", LANGUAGES.stream().sorted().toList()) + ".");
        }
        return v;
    }

    /** @return the cleaned header, or null when blank */
    public static String header(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        if (v.length() > HEADER_MAX) {
            throw new BusinessRuleException("The header can be at most " + HEADER_MAX + " characters; this one is " + v.length() + ".");
        }
        if (v.contains("\n")) {
            throw new BusinessRuleException("The header must be a single line.");
        }
        if (count(VARIABLE, v) > 1) {
            throw new BusinessRuleException("Meta allows at most one {{variable}} in the header.");
        }
        return v;
    }

    public static String footer(String value) {
        String v = blankToNull(value);
        if (v == null) {
            return null;
        }
        if (v.length() > FOOTER_MAX) {
            throw new BusinessRuleException("The footer can be at most " + FOOTER_MAX + " characters; this one is " + v.length() + ".");
        }
        if (v.contains("\n")) {
            throw new BusinessRuleException("The footer must be a single line.");
        }
        if (VARIABLE.matcher(v).find()) {
            throw new BusinessRuleException("Meta does not allow {{variables}} in the footer.");
        }
        return v;
    }

    public static List<TemplateButton> buttons(List<TemplateButton> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        int urls = 0;
        int calls = 0;
        java.util.ArrayList<TemplateButton> clean = new java.util.ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            TemplateButton b = raw.get(i);
            String where = "Button " + (i + 1);
            if (b == null || b.type() == null) {
                throw new BusinessRuleException(where + ": choose Link or Call.");
            }
            String text = blankToNull(b.text());
            if (text == null) {
                throw new BusinessRuleException(where + ": the label is required.");
            }
            if (text.length() > BUTTON_TEXT_MAX) {
                throw new BusinessRuleException(where + ": the label can be at most " + BUTTON_TEXT_MAX + " characters.");
            }
            if (b.type() == TemplateButton.Type.URL) {
                urls++;
                String url = blankToNull(b.url());
                if (url == null || !url.startsWith("https://")) {
                    throw new BusinessRuleException(where + ": a link must start with https://");
                }
                if (url.length() > URL_MAX) {
                    throw new BusinessRuleException(where + ": the link is too long.");
                }
                checkUrlVariable(where, url);
                clean.add(new TemplateButton(b.type(), text, url, null));
            } else {
                calls++;
                String phone = blankToNull(b.phoneNumber());
                String compact = phone == null ? null : phone.replaceAll("[\\s-]", "");
                if (compact == null || !PHONE.matcher(compact).matches()) {
                    throw new BusinessRuleException(where + ": enter the number with its country code, e.g. +919876543210.");
                }
                clean.add(new TemplateButton(b.type(), text, null, compact));
            }
        }
        if (urls > MAX_URL_BUTTONS) {
            throw new BusinessRuleException("A template can have at most " + MAX_URL_BUTTONS + " link buttons.");
        }
        if (calls > MAX_CALL_BUTTONS) {
            throw new BusinessRuleException("A template can have at most " + MAX_CALL_BUTTONS + " call button.");
        }
        return List.copyOf(clean);
    }

    /** Only variable names we can fill; values trimmed, single-line, bounded; blanks dropped. */
    public static Map<String, String> samples(Map<String, String> raw) {
        Map<String, String> clean = new LinkedHashMap<>();
        if (raw == null) {
            return clean;
        }
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (!TemplateSampleValues.all().containsKey(e.getKey())) {
                throw new BusinessRuleException("\"" + e.getKey() + "\" is not a variable this app can fill.");
            }
            String v = blankToNull(e.getValue());
            if (v == null) {
                continue;
            }
            if (v.contains("\n")) {
                throw new BusinessRuleException("The sample for {{" + e.getKey() + "}} must be a single line (Meta rejects line breaks in examples).");
            }
            if (v.length() > SAMPLE_MAX) {
                throw new BusinessRuleException("The sample for {{" + e.getKey() + "}} is too long.");
            }
            clean.put(e.getKey(), v);
        }
        return clean;
    }

    /** A URL button may carry one variable, and only as its final segment (Meta's rule). */
    private static void checkUrlVariable(String where, String url) {
        Matcher m = VARIABLE.matcher(url);
        int n = 0;
        int lastEnd = -1;
        while (m.find()) {
            n++;
            lastEnd = m.end();
        }
        if (n > 1) {
            throw new BusinessRuleException(where + ": a link can contain at most one {{variable}}.");
        }
        if (n == 1 && lastEnd != url.length()) {
            throw new BusinessRuleException(where + ": the {{variable}} must be at the very end of the link, e.g. https://example.com/track/{{trackingNumber}}");
        }
    }

    private static int count(Pattern p, String s) {
        Matcher m = p.matcher(s);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
