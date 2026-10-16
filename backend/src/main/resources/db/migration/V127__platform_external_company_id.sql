-- Link a courier company to the company record held by the OCCPL SaaS platform.
-- NULL for every company created inside this app; set only by the platform
-- provisioning endpoint (/provisioning/v1/tenants/{companyId}). Unique so a repeated
-- provisioning call finds the existing company instead of creating a second one.
ALTER TABLE companies
    ADD COLUMN external_company_id BINARY(16) NULL AFTER company_id,
    ADD CONSTRAINT uk_companies_external_company_id UNIQUE (external_company_id);
