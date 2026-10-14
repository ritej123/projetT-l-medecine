package com.telemed.platform.prescription.domain;

import java.time.LocalDate;
import java.time.Instant;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "prescriptions")
public class Prescription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    @NotBlank
    @Size(max = 10000)
    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PrescriptionStatus status;

    @Column(name = "prescribed_on")
    private LocalDate date;

    @Column(name = "signed_by_user_id")
    private UUID signedByUserId;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "digital_signature", length = 64)
    private String digitalSignature;

    @Version
    @Column(nullable = false)
    private long version;

    protected Prescription() {
    }

    public Prescription(Appointment appointment, String content) {
        this.appointment = appointment;
        this.content = content;
        this.status = PrescriptionStatus.DRAFT;
    }

    public UUID getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public String getContent() {
        return content;
    }

    public PrescriptionStatus getStatus() {
        return status;
    }

    public LocalDate getDate() {
        return date;
    }

    public UUID getSignedByUserId() {
        return signedByUserId;
    }

    public Instant getSignedAt() {
        return signedAt;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public String getDigitalSignature() {
        return digitalSignature;
    }

    public long getVersion() {
        return version;
    }

    public void updateDraft(String content) {
        if (status != PrescriptionStatus.DRAFT) {
            throw new IllegalStateException("Only a draft prescription can be edited.");
        }
        this.content = content;
    }

    public void validateOn(
            LocalDate date,
            UUID signedByUserId,
            Instant signedAt,
            String contentSha256,
            String digitalSignature) {
        this.status = PrescriptionStatus.VALIDATED;
        this.date = date;
        this.signedByUserId = signedByUserId;
        this.signedAt = signedAt;
        this.contentSha256 = contentSha256;
        this.digitalSignature = digitalSignature;
    }
}
