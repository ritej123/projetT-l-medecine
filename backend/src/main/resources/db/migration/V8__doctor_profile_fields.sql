
ALTER TABLE doctors
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(80),
    ADD COLUMN IF NOT EXISTS office_address VARCHAR(500),
    ADD COLUMN IF NOT EXISTS office_city VARCHAR(120),
    ADD COLUMN IF NOT EXISTS professional_number VARCHAR(40),
    ADD COLUMN IF NOT EXISTS professional_phone VARCHAR(40),
    ADD COLUMN IF NOT EXISTS document_url VARCHAR(2048),
    ADD COLUMN IF NOT EXISTS document_uploaded_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_doctors_last_name
    ON doctors (last_name);

CREATE INDEX IF NOT EXISTS idx_doctors_office_city
    ON doctors (office_city);
