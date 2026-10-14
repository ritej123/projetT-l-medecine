CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE availabilities
    ADD COLUMN slot_duration_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT availability_slot_duration_valid
        CHECK (slot_duration_minutes BETWEEN 5 AND 240);

ALTER TABLE availabilities
    ADD CONSTRAINT availabilities_no_overlapping_windows
    EXCLUDE USING gist (
        doctor_id WITH =,
        day_of_week WITH =,
        tsrange(
            DATE '2000-01-03' + start_time,
            DATE '2000-01-03' + end_time,
            '[)'
        ) WITH &&
    );

ALTER TABLE appointments
    ADD COLUMN duration_minutes INTEGER NOT NULL DEFAULT 30,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD CONSTRAINT appointment_duration_valid
        CHECK (duration_minutes BETWEEN 5 AND 240);

CREATE TABLE appointment_slots (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES doctors(id),
    scheduled_at TIMESTAMP NOT NULL,
    reserved BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_appointment_slot_doctor_start UNIQUE (doctor_id, scheduled_at)
);

INSERT INTO appointment_slots (id, doctor_id, scheduled_at, reserved, version)
SELECT gen_random_uuid(), doctor_id, scheduled_at, TRUE, 0
FROM appointments
WHERE status IN ('PENDING', 'CONFIRMED');

ALTER TABLE appointments
    ADD CONSTRAINT appointments_no_active_overlaps
    EXCLUDE USING gist (
        doctor_id WITH =,
        tsrange(scheduled_at, scheduled_at + make_interval(mins => duration_minutes), '[)') WITH &&
    )
    WHERE (status IN ('PENDING', 'CONFIRMED'));

CREATE TABLE appointment_email_notifications (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL REFERENCES appointments(id) ON DELETE CASCADE,
    recipient VARCHAR(254) NOT NULL,
    type VARCHAR(32) NOT NULL CHECK (type IN ('APPOINTMENT_CONFIRMATION', 'APPOINTMENT_REMINDER')),
    scheduled_at TIMESTAMPTZ NOT NULL,
    appointment_at TIMESTAMP NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_appointment_notification_type UNIQUE (appointment_id, type)
);

CREATE INDEX idx_appointment_notifications_due
    ON appointment_email_notifications (next_attempt_at)
    WHERE status = 'PENDING';
