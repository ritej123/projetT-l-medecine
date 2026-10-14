package com.telemed.platform.patient.domain;

import java.util.UUID;

import com.telemed.platform.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Size(max = 10000)
    @Column(name = "medical_record", columnDefinition = "text")
    private String medicalRecord;

    protected Patient() {
    }

    public Patient(User user, String medicalRecord) {
        this.user = user;
        this.medicalRecord = medicalRecord;
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getMedicalRecord() {
        return medicalRecord;
    }
}
