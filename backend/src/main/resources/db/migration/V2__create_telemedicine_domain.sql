CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('PATIENT', 'DOCTOR', 'ADMIN')),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE doctors (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    specialty VARCHAR(120) NOT NULL,
    bio TEXT
);

CREATE TABLE patients (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id),
    medical_record TEXT
);

CREATE TABLE availabilities (
    id UUID PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES doctors(id),
    day_of_week VARCHAR(12) NOT NULL CHECK (day_of_week IN
        ('MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY')),
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    CONSTRAINT availability_valid_time CHECK (end_time > start_time)
);

CREATE INDEX idx_availabilities_doctor_day
    ON availabilities (doctor_id, day_of_week, start_time);

CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL REFERENCES patients(id),
    doctor_id UUID NOT NULL REFERENCES doctors(id),
    scheduled_at TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'CANCELLED', 'DONE'))
);

CREATE INDEX idx_appointments_patient_date ON appointments (patient_id, scheduled_at DESC);
CREATE INDEX idx_appointments_doctor_date ON appointments (doctor_id, scheduled_at);
CREATE UNIQUE INDEX idx_appointments_active_doctor_time
    ON appointments (doctor_id, scheduled_at)
    WHERE status IN ('PENDING', 'CONFIRMED');

CREATE TABLE prescriptions (
    id UUID PRIMARY KEY,
    appointment_id UUID NOT NULL UNIQUE REFERENCES appointments(id),
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'VALIDATED')),
    prescribed_on DATE,
    CONSTRAINT prescription_validation_date CHECK (
        (status = 'DRAFT' AND prescribed_on IS NULL)
        OR (status = 'VALIDATED' AND prescribed_on IS NOT NULL)
    )
);

CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
