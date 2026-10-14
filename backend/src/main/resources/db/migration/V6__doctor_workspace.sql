ALTER TABLE doctors
    ADD COLUMN first_name VARCHAR(80),
    ADD COLUMN last_name VARCHAR(80),
    ADD COLUMN profile_photo_url VARCHAR(2048),
    ADD COLUMN professional_phone VARCHAR(40),
    ADD COLUMN office_address VARCHAR(500),
    ADD COLUMN professional_number VARCHAR(40),
    ADD COLUMN professional_number_verified BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN email_notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE consultations (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL UNIQUE REFERENCES appointments(id),
    report TEXT,
    observations TEXT,
    diagnosis TEXT,
    finalized BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE consultation_revisions (
    id UUID PRIMARY KEY,
    consultation_id UUID NOT NULL REFERENCES consultations(id) ON DELETE CASCADE,
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    report TEXT,
    observations TEXT,
    diagnosis TEXT,
    actor_user_id UUID NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_consultation_revision_number UNIQUE (consultation_id, revision_number)
);

CREATE INDEX idx_consultation_revisions_history
    ON consultation_revisions (consultation_id, revision_number);

CREATE TABLE doctor_notifications (
    id UUID PRIMARY KEY,
    doctor_user_id UUID NOT NULL REFERENCES users(id),
    appointment_id UUID REFERENCES appointments(id),
    type VARCHAR(32) NOT NULL CHECK (type IN (
        'NEW_APPOINTMENT', 'APPOINTMENT_CANCELLED', 'APPOINTMENT_UPDATED', 'APPOINTMENT_REMINDER', 'ADMIN_NOTICE')),
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    read_at TIMESTAMPTZ
);

CREATE INDEX idx_doctor_notifications_inbox
    ON doctor_notifications (doctor_user_id, created_at DESC);

CREATE UNIQUE INDEX uk_doctor_notification_appointment_reminder
    ON doctor_notifications (appointment_id)
    WHERE type = 'APPOINTMENT_REMINDER';

CREATE TABLE availability_exceptions (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES doctors(id),
    exception_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    reason VARCHAR(240),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT availability_exception_valid_time CHECK (
        (start_time IS NULL AND end_time IS NULL)
        OR (start_time IS NOT NULL AND end_time IS NOT NULL AND end_time > start_time)
    )
);

CREATE INDEX idx_availability_exceptions_doctor_date
    ON availability_exceptions (doctor_id, exception_date);

ALTER TABLE availability_exceptions
    ADD CONSTRAINT availability_exceptions_no_overlap
    EXCLUDE USING gist (
        doctor_id WITH =,
        tsrange(
            exception_date + COALESCE(start_time, TIME '00:00'),
            exception_date + COALESCE(end_time, TIME '24:00'),
            '[)'
        ) WITH &&
    );
