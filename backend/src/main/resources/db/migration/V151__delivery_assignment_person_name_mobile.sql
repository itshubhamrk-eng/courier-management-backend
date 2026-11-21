ALTER TABLE delivery_assignment
    MODIFY COLUMN delivery_user_id BINARY(16) NULL,
    ADD COLUMN delivery_person_name VARCHAR(120) NULL AFTER delivery_user_id,
    ADD COLUMN delivery_person_mobile VARCHAR(20) NULL AFTER delivery_person_name;
