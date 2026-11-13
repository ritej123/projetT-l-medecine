ALTER TABLE appointment_email_notifications
    ADD COLUMN IF NOT EXISTS message_body VARCHAR(4000);
