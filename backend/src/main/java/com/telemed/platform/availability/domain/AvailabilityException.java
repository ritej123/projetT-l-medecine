package com.telemed.platform.availability.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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

@Entity
@Table(name = "availability_exceptions")
public class AvailabilityException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "exception_date", nullable = false)
    private LocalDate exceptionDate;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(length = 240)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AvailabilityException() {
    }

    public AvailabilityException(
            Doctor doctor, LocalDate exceptionDate, LocalTime startTime, LocalTime endTime, String reason, Instant now) {
        this.doctor = doctor;
        this.exceptionDate = exceptionDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
        this.createdAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDoctorId() { return doctor.getId(); }
    public LocalDate getExceptionDate() { return exceptionDate; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getReason() { return reason; }
}
