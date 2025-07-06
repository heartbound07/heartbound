-- Drop Credit Audit Table Migration
DROP INDEX IF EXISTS idx_credit_audit_user_id;
DROP INDEX IF EXISTS idx_credit_audit_timestamp;
DROP INDEX IF EXISTS idx_credit_audit_source;
DROP INDEX IF EXISTS idx_credit_audit_user_timestamp;
DROP INDEX IF EXISTS idx_credit_audit_recipient;
DROP INDEX IF EXISTS idx_credit_audit_admin;
DROP INDEX IF EXISTS idx_credit_audit_flagged;
DROP INDEX IF EXISTS idx_credit_audit_risk_score;
DROP INDEX IF EXISTS idx_credit_audit_transaction_id;
DROP TABLE IF EXISTS credit_audit;