package com.telemed.platform.prescription.domain;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "prescription_revisions", uniqueConstraints = {
    @UniqueConstraint(name = "uk_prescription_revision_number", columnNames = {"prescription_id", "revision_number"})
})
public class PrescriptionRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "prescription_id", nullable = false)
    private Prescription prescription;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "change_type", nullable = false, length = 30)
    private String changeType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PrescriptionRevision() {
    }

    public PrescriptionRevision(
            Prescription prescription,
            int revisionNumber,
            String content,
            UUID actorUserId,
            String changeType,
            Instant createdAt) {
        this.prescription = prescription;
        this.revisionNumber = revisionNumber;
        this.content = content;
        this.actorUserId = actorUserId;
        this.changeType = changeType;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public Prescription getPrescription() { return prescription; }
    public int getRevisionNumber() { return revisionNumber; }
    public String getContent() { return content; }
    public UUID getActorUserId() { return actorUserId; }
    public String getChangeType() { return changeType; }
    public Instant getCreatedAt() { return createdAt; }
}
