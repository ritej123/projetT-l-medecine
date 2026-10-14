package com.telemed.platform.prescription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.domain.AppointmentStatus;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.prescription.api.PrescriptionResponse;
import com.telemed.platform.prescription.domain.PrescriptionStatus;
import com.telemed.platform.prescription.repository.PrescriptionRepository;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import org.junit.jupiter.api.Test;

class PrescriptionDraftServiceTest {

    private final AppointmentRepository appointmentRepository = org.mockito.Mockito.mock(AppointmentRepository.class);
    private final PrescriptionRepository prescriptionRepository = org.mockito.Mockito.mock(PrescriptionRepository.class);
    private final MedicalNoteAnonymizer anonymizer = new MedicalNoteAnonymizer();
    private final PrescriptionDraftGenerator generator = org.mockito.Mockito.mock(PrescriptionDraftGenerator.class);
    private final PrescriptionService prescriptionService = org.mockito.Mockito.mock(PrescriptionService.class);
    private final PrescriptionDraftService service = new PrescriptionDraftService(
            appointmentRepository,
            prescriptionRepository,
            anonymizer,
            generator,
            prescriptionService,
            Clock.fixed(Instant.parse("2026-10-12T10:15:00Z"), ZoneId.of("Europe/Paris")),
            ZoneId.of("Europe/Paris"));

    @Test
    void anonymizesConsultationNotesBeforeCallingLlmAndPersistsOnlyGeneratedDraft() {
        UUID appointmentId = UUID.randomUUID();
        Doctor doctor = new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null);
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                doctor,
                LocalDateTime.of(2026, 10, 12, 12, 0),
                30);
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        String notes = "Patiente: Jeanne Dupont, joignable au 06 12 34 56 78. Toux sèche.";
        String anonymized = anonymizer.anonymize(notes);
        String draft = "BROUILLON NON VALIDÉ\nÀ compléter par le médecin.";
        PrescriptionResponse expected = new PrescriptionResponse(
                UUID.randomUUID(), appointmentId, draft, PrescriptionStatus.DRAFT, null,
                null, null, null, null);
        when(appointmentRepository.findWithDoctorUserById(appointmentId)).thenReturn(Optional.of(appointment));
        when(prescriptionRepository.findByAppointmentId(appointmentId)).thenReturn(Optional.empty());
        when(generator.generateDraft(anonymized)).thenReturn(draft);
        when(prescriptionService.createGeneratedDraft("doctor@example.test", appointmentId, draft))
                .thenReturn(expected);

        assertThat(service.generate("doctor@example.test", appointmentId, notes)).isEqualTo(expected);
        verify(generator).generateDraft(argThat(safeNotes ->
                !safeNotes.contains("Jeanne Dupont")
                        && !safeNotes.contains("06 12 34 56 78")
                        && safeNotes.contains("Toux sèche.")));
        verify(prescriptionService).createGeneratedDraft("doctor@example.test", appointmentId, draft);
    }

    @Test
    void doesNotSendNotesToLlmForAnotherDoctorsAppointment() {
        UUID appointmentId = UUID.randomUUID();
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                new Doctor(new User("doctor@example.test", "hash", Role.DOCTOR), "General", null),
                LocalDateTime.of(2026, 10, 12, 12, 0),
                30);
        when(appointmentRepository.findWithDoctorUserById(appointmentId)).thenReturn(Optional.of(appointment));

        assertThatThrownBy(() -> service.generate("other@example.test", appointmentId, "Private clinical notes"))
                .isInstanceOf(NotFoundException.class);
        verify(generator, never()).generateDraft(any());
    }
}
