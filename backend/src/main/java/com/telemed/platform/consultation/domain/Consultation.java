package com.telemed.platform.consultation.domain;

import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "consultations")
public class Consultation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @Column(columnDefinition = "text")
    private String report;

    @Column(columnDefinition = "text")
    private String observations;

    @Column(columnDefinition = "text")
    private String diagnosis;

    @Column(nullable = false)
    private boolean finalized;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Consultation() {
    }

    public Consultation(Appointment appointment, Instant now) {
        this.appointment = appointment;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String report, String observations, String diagnosis, Instant now) {
        this.report = report;
        this.observations = observations;
        this.diagnosis = diagnosis;
        this.updatedAt = now;
    }

    public void finalizeConsultation(Instant now) {
        this.finalized = true;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public Appointment getAppointment() { return appointment; }
    public String getReport() { return report; }
    public String getObservations() { return observations; }
    public String getDiagnosis() { return diagnosis; }
    public boolean isFinalized() { return finalized; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
}
