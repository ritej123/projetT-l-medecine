ALTER TABLE appointment_email_notifications
    DROP CONSTRAINT uk_appointment_notification_type,
    DROP CONSTRAINT appointment_email_notifications_type_check;

ALTER TABLE appointment_email_notifications
    ADD COLUMN event_sequence INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT appointment_email_notifications_type_check
        CHECK (type IN (
            'APPOINTMENT_CONFIRMATION',
            'APPOINTMENT_REMINDER',
            'APPOINTMENT_RESCHEDULED',
            'DOCTOR_APPOINTMENT_NOTICE'
        )),
    ADD CONSTRAINT uk_appointment_notification_event
        UNIQUE (appointment_id, type, event_sequence);
