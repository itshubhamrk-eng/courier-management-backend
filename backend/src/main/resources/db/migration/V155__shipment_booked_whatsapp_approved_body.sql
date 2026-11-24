-- Meta approved the shipment_booked WhatsApp template with this exact body (5 variables:
-- customerName, shipmentNumber, pickupLocation, deliveryLocation, trackingUrl). Bring the stored
-- rows that still hold the old seed (or the old display name) in line so sends match the approval.
UPDATE communication_template
SET template_name = 'shipment_booked',
    content = CONCAT('Hi {{customerName}},', CHAR(10), CHAR(10),
                     'your shipment {{shipmentNumber}},', CHAR(10), CHAR(10),
                     'from {{pickupLocation}} to {{deliveryLocation}} has been booked with Ambox Logistics.',
                     CHAR(10), CHAR(10),
                     'Track it: {{trackingUrl}}', CHAR(10), CHAR(10),
                     'Thanks'),
    meta_template_id = NULL, meta_status = NULL, meta_status_detail = NULL, meta_category = NULL,
    meta_language = NULL, meta_submitted_at = NULL
WHERE event_type = 'SHIPMENT_BOOKED' AND channel = 'WHATSAPP' AND deleted = 0
  AND template_name IN ('Shipment Booked - WhatsApp', 'shipment_booked');
