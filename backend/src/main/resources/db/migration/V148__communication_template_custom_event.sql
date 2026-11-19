-- CUSTOM is a new communication_template.event_type value (VARCHAR, no DDL needed): a template that is
-- not tied to any shipment event and is never sent automatically — created freely, submitted to Meta,
-- sent by name. The old unique key allowed one row per (company, event, channel), which would cap a
-- company at one custom template per channel. slot_key keeps that rule for every real event but gives
-- each CUSTOM row its own slot (its id), so existing data and the lookups by event are untouched.
ALTER TABLE communication_template
    ADD COLUMN slot_key VARCHAR(40) GENERATED ALWAYS AS (IF(event_type = 'CUSTOM', HEX(id), event_type)) STORED,
    DROP INDEX uk_communication_template_company_event_channel,
    ADD UNIQUE KEY uk_communication_template_company_slot_channel (company_id, slot_key, channel);
