-- =============================================================================
-- V89 — Branch name no longer unique
--
-- branch_code stays the unique identifier per company (uk_branches_company_code,
-- untouched). branch_name was also unique per company since V9; direct instruction
-- to drop that — two branches in the same company may now share a display name.
-- =============================================================================

ALTER TABLE branches DROP INDEX uk_branches_company_name;
