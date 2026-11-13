package com.telemed.platform.doctor.domain;

import java.util.UUID;

import com.telemed.platform.user.domain.User;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String specialty;

    @Size(max = 4000)
    @Column(columnDefinition = "text")
    private String bio;

    @Size(max = 80)
    @Column(name = "first_name", length = 80)
    private String firstName;

    @Size(max = 80)
    @Column(name = "last_name", length = 80)
    private String lastName;

    @Size(max = 2048)
    @Column(name = "profile_photo_url", length = 2048)
    private String profilePhotoUrl;

    @Size(max = 40)
    @Column(name = "professional_phone", length = 40)
    private String professionalPhone;

    @Size(max = 500)
    @Column(name = "office_address", length = 500)
    private String officeAddress;

    @Size(max = 120)
    @Column(name = "office_city", length = 120)
    private String officeCity;

    @Size(max = 40)
    @Column(name = "professional_number", length = 40)
    private String professionalNumber;

    @Column(name = "professional_number_verified", nullable = false)
    private boolean professionalNumberVerified;

    @Size(max = 2048)
    @Column(name = "document_url", length = 2048)
    private String documentUrl;

    @Column(name = "document_uploaded_at")
    private java.time.Instant documentUploadedAt;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 20)
    private DoctorVerificationStatus verificationStatus = DoctorVerificationStatus.VERIFIED;

    protected Doctor() {
    }

    public Doctor(User user, String specialty, String bio) {
        this.user = user;
        this.specialty = specialty;
        this.bio = bio;
        this.verificationStatus =
                user.getRole() == com.telemed.platform.user.domain.Role.DOCTOR && !user.isEnabled()
                        ? DoctorVerificationStatus.PENDING
                        : DoctorVerificationStatus.VERIFIED;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getSpecialty() {
        return specialty;
    }

    public String getBio() {
        return bio;
    }

    public DoctorVerificationStatus getVerificationStatus() {
        return verificationStatus;
    }

    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getProfilePhotoUrl() { return profilePhotoUrl; }
    public String getProfessionalPhone() { return professionalPhone; }
    public String getOfficeAddress() { return officeAddress; }
    public String getOfficeCity() { return officeCity; }
    public String getProfessionalNumber() { return professionalNumber; }
    public String getDocumentUrl() { return documentUrl; }
    public java.time.Instant getDocumentUploadedAt() { return documentUploadedAt; }
    public boolean isProfessionalNumberVerified() { return professionalNumberVerified; }
    public boolean isEmailNotificationsEnabled() { return emailNotificationsEnabled; }

    public void updateProfile(
            String firstName,
            String lastName,
            String profilePhotoUrl,
            String professionalPhone,
            String officeAddress,
            String officeCity,
            String professionalNumber,
            String documentUrl,
            boolean emailNotificationsEnabled) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.profilePhotoUrl = profilePhotoUrl;
        this.professionalPhone = professionalPhone;
        this.officeAddress = officeAddress;
        this.officeCity = officeCity;
        this.professionalNumber = professionalNumber;
        this.documentUrl = documentUrl;
        this.emailNotificationsEnabled = emailNotificationsEnabled;
    }

    public void setDocumentUploadedAt(java.time.Instant documentUploadedAt) {
        this.documentUploadedAt = documentUploadedAt;
    }

    public void setVerificationStatus(DoctorVerificationStatus verificationStatus) {
        this.verificationStatus = verificationStatus;
    }
}
