-- =============================================================================
-- V122 — The rest of a WhatsApp template: category, language, header, footer, buttons, and the
-- sample value shown for each {{variable}}.
--
-- Until now a template was only a body: category (UTILITY) and language (en_US) were hard-coded
-- defaults and a send always used one deployment-wide language. These columns make them the
-- template's own:
--   category / language    chosen per template; the send uses `language`, Meta is submitted with both
--   header_text            optional TEXT header (<= 60 chars, at most one {{variable}})
--   footer_text            optional footer line (<= 60 chars, no variables)
--   buttons_json           JSON list of {type: URL|PHONE_NUMBER, text, url, phoneNumber}; a URL may end
--                          in one {{variable}} (e.g. https://x.in/track/{{trackingNumber}})
--   variable_samples_json  JSON {variable: sample} overriding the built-in example for that variable —
--                          what Meta's reviewer sees and what the live preview shows
--
-- Existing rows get UTILITY / en_US, exactly what they were submitted and sent with before.
-- Quick-reply buttons and image headers are deliberately not modelled: the first needs an inbound
-- webhook this app does not have, the second a separate Meta media-upload step.
-- =============================================================================

ALTER TABLE communication_template
    ADD COLUMN category              VARCHAR(20) NOT NULL DEFAULT 'UTILITY' AFTER status,
    ADD COLUMN language              VARCHAR(10) NOT NULL DEFAULT 'en_US'   AFTER category,
    ADD COLUMN header_text           VARCHAR(60) NULL AFTER language,
    ADD COLUMN footer_text           VARCHAR(60) NULL AFTER header_text,
    ADD COLUMN buttons_json          TEXT NULL AFTER footer_text,
    ADD COLUMN variable_samples_json TEXT NULL AFTER buttons_json;
