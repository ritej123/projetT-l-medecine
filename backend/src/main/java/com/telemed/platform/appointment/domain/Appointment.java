package com.telemed.platform.appointment.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.patient.domain.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(name = "consultation_reason", length = 500)
    private String consultationReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    protected Appointment() {
    }

    public Appointment(Patient patient, Doctor doctor, LocalDateTime scheduledAt, int durationMinutes) {
        this(patient, doctor, scheduledAt, durationMinutes, null);
    }

    public Appointment(
            Patient patient, Doctor doctor, LocalDateTime scheduledAt, int durationMinutes, String consultationReason) {
        this.patient = patient;
        this.doctor = doctor;
        this.scheduledAt = scheduledAt;
        this.durationMinutes = durationMinutes;
        this.consultationReason = consultationReason;
        this.status = AppointmentStatus.PENDING;
    }

    public UUID getId() {
        return id;
    }

    public Patient getPatient() {
        return patient;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getConsultationReason() {
        return consultationReason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public void setStatus(AppointmentStatus status) {
        this.status = status;
    }

    public void reschedule(LocalDateTime scheduledAt, int durationMinutes) {
        this.scheduledAt = scheduledAt;
        this.durationMinutes = durationMinutes;
    }
}
