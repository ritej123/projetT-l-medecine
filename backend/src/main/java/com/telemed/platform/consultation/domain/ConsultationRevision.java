package com.telemed.platform.consultation.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "consultation_revisions")
public class ConsultationRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consultation_id", nullable = false)
    private Consultation consultation;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(columnDefinition = "text")
    private String report;

    @Column(columnDefinition = "text")
    private String observations;

    @Column(columnDefinition = "text")
    private String diagnosis;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ConsultationRevision() {
    }

    public ConsultationRevision(
            Consultation consultation, int revisionNumber, UUID actorUserId, Instant createdAt) {
        this.consultation = consultation;
        this.revisionNumber = revisionNumber;
        this.report = consultation.getReport();
        this.observations = consultation.getObservations();
        this.diagnosis = consultation.getDiagnosis();
        this.actorUserId = actorUserId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getReport() { return report; }
    public String getObservations() { return observations; }
    public String getDiagnosis() { return diagnosis; }
    public UUID getActorUserId() { return actorUserId; }
    public Instant getCreatedAt() { return createdAt; }
}
