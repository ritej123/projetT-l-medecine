ALTER TABLE appointment_email_notifications
    ALTER COLUMN appointment_id DROP NOT NULL,
    ALTER COLUMN appointment_at DROP NOT NULL;

-- In Postgres, UNIQUE (appointment_id, type, event_sequence) allows multiple NULL appointment_id
-- but we also ensure there is no strict constraint blocking null appointment_id.
