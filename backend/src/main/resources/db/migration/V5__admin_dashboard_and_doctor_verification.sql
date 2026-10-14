ALTER TABLE doctors
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'VERIFIED'
        CHECK (verification_status IN ('PENDING', 'VERIFIED', 'REJECTED'));

UPDATE doctors doctor
SET verification_status = 'PENDING'
FROM users account
WHERE doctor.user_id = account.id
  AND account.enabled = FALSE;

CREATE INDEX idx_doctors_verification_status ON doctors (verification_status);

CREATE TABLE admin_audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(60) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id UUID NOT NULL,
    result VARCHAR(20) NOT NULL CHECK (result IN ('SUCCESS', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_admin_audit_logs_created_at ON admin_audit_logs (created_at DESC);
CREATE INDEX idx_admin_audit_logs_actor ON admin_audit_logs (actor_user_id, created_at DESC);
