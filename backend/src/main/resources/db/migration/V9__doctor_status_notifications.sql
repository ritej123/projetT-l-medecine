ALTER TABLE appointment_email_notifications
    DROP CONSTRAINT IF EXISTS appointment_email_notifications_type_check;

ALTER TABLE appointment_email_notifications
    ADD CONSTRAINT appointment_email_notifications_type_check
        CHECK (type IN (
            'APPOINTMENT_CONFIRMATION',
            'APPOINTMENT_REMINDER',
            'APPOINTMENT_RESCHEDULED',
            'DOCTOR_APPOINTMENT_NOTICE',
            'DOCTOR_APPROVED',
            'DOCTOR_REJECTED',
            'DOCTOR_SUSPENDED',
            'DOCTOR_REACTIVATED'
        ));

ALTER TABLE doctor_notifications
    ADD COLUMN IF NOT EXISTS reason VARCHAR(1000);