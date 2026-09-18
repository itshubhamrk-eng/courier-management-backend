-- Package Type is no longer picked at Shipment Booking — direct request. It never
-- priced anything once Route/Rate's old branch-pair matching stopped running (delivery
-- branch is never known at booking any more, see V62/0.62.0), so dropping the
-- requirement changes nothing about how a shipment is priced.
ALTER TABLE shipments MODIFY COLUMN package_type_id BINARY(16) NULL;
