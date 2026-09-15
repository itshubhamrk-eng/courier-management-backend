-- A qty-level SLAB charge (e.g. "Hamali") prices on average per-piece weight
-- (total actual weight / number of packages) rather than the shipment's total
-- chargeable weight, then multiplies the matched slab's value by the package count.
ALTER TABLE charges
    ADD COLUMN is_qty_level TINYINT(1) NOT NULL DEFAULT 0 AFTER status;
