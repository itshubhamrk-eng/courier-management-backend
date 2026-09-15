-- TO_PAY received at branch (SubTransactionType.TPY) is now allowed to overdraw a
-- wallet — see Wallet.applyDebitAllowingOverdraft's doc: the branch's liability for
-- freight it has physically received exists the instant the shipment lands, whether
-- or not the branch already holds that much float. The DB-level check constraint
-- enforced non-negative unconditionally (it cannot see which transaction type is
-- posting), so it blocked exactly that debit even after the application-level fix.
ALTER TABLE wallets DROP CONSTRAINT ck_wallets_available_non_negative;
