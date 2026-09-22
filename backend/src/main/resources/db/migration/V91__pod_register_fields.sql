-- Manual POD register fields for the company-level single POD upload screen — a paper-
-- register-style log (delivery date, who delivered it, when the physical POD came back to
-- the office, its status, a free-text remark), independent of the shipment's own DELIVERED
-- status machine and of the AI verification score/reasons already on this table.
ALTER TABLE pod_verification
    ADD COLUMN delivery_date DATE NULL AFTER detected_date,
    ADD COLUMN delivered_by VARCHAR(150) NULL AFTER delivery_date,
    ADD COLUMN pod_date DATE NULL AFTER delivered_by,
    ADD COLUMN pod_time TIME NULL AFTER pod_date,
    ADD COLUMN entry_status VARCHAR(20) NULL AFTER pod_time,
    ADD COLUMN remark VARCHAR(1000) NULL AFTER entry_status;
