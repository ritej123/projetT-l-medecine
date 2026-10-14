ALTER TABLE prescriptions
    ADD COLUMN signed_by_user_id UUID REFERENCES users(id),
    ADD COLUMN signed_at TIMESTAMPTZ,
    ADD COLUMN content_sha256 VARCHAR(64),
    ADD COLUMN digital_signature VARCHAR(64),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT prescription_signature_consistency CHECK (
        (status = 'DRAFT' AND signed_by_user_id IS NULL AND signed_at IS NULL
            AND content_sha256 IS NULL AND digital_signature IS NULL)
        OR (status = 'VALIDATED' AND prescribed_on IS NOT NULL
            AND (
                (signed_by_user_id IS NOT NULL AND signed_at IS NOT NULL
                    AND content_sha256 IS NOT NULL AND digital_signature IS NOT NULL)
                OR (signed_by_user_id IS NULL AND signed_at IS NULL
                    AND content_sha256 IS NULL AND digital_signature IS NULL)
            ))
    );

CREATE TABLE prescription_revisions (
    id UUID PRIMARY KEY,
    prescription_id UUID NOT NULL REFERENCES prescriptions(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    content TEXT NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    change_type VARCHAR(30) NOT NULL CHECK (change_type IN ('CREATED', 'GENERATED', 'EDITED', 'SIGNED')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_prescription_revision_number UNIQUE (prescription_id, revision_number)
);

CREATE INDEX idx_prescription_revisions_history
    ON prescription_revisions (prescription_id, revision_number);

INSERT INTO prescription_revisions (
    id, prescription_id, revision_number, content, actor_user_id, change_type, created_at
)
SELECT gen_random_uuid(), prescription.id, 1, prescription.content, doctor.user_id, 'CREATED', now()
FROM prescriptions prescription
JOIN appointments appointment ON appointment.id = prescription.appointment_id
JOIN doctors doctor ON doctor.id = appointment.doctor_id;
