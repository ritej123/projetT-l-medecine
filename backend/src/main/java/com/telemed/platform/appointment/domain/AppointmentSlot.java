package com.telemed.platform.appointment.domain;

import java.time.LocalDateTime;
import java.util.UUID;

import com.telemed.platform.doctor.domain.Doctor;
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
import jakarta.persistence.Version;

@Entity
@Table(name = "appointment_slots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_appointment_slot_doctor_start", columnNames = {"doctor_id", "scheduled_at"}))
public class AppointmentSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    @Column(nullable = false)
    private boolean reserved;

    @Version
    @Column(nullable = false)
    private long version;

    protected AppointmentSlot() {
    }

    public AppointmentSlot(Doctor doctor, LocalDateTime scheduledAt) {
        this.doctor = doctor;
        this.scheduledAt = scheduledAt;
    }

    public boolean isReserved() {
        return reserved;
    }

    public void reserve() {
        reserved = true;
    }

    public void release() {
        reserved = false;
    }
}
