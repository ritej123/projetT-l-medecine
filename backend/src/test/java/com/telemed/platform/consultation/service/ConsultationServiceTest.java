package com.telemed.platform.consultation.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import com.telemed.platform.appointment.domain.Appointment;
import com.telemed.platform.appointment.repository.AppointmentRepository;
import com.telemed.platform.common.exception.NotFoundException;
import com.telemed.platform.consultation.repository.ConsultationRepository;
import com.telemed.platform.consultation.repository.ConsultationRevisionRepository;
import com.telemed.platform.doctor.domain.Doctor;
import com.telemed.platform.doctor.repository.DoctorRepository;
import com.telemed.platform.notification.service.AppointmentNotificationService;
import com.telemed.platform.notification.service.DoctorNotificationService;
import com.telemed.platform.patient.domain.Patient;
import com.telemed.platform.user.domain.Role;
import com.telemed.platform.user.domain.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsultationServiceTest {

    @Mock private DoctorRepository doctorRepository;
    @Mock private AppointmentRepository appointmentRepository;
    @Mock private ConsultationRepository consultationRepository;
    @Mock private ConsultationRevisionRepository revisionRepository;
    @Mock private AppointmentNotificationService appointmentNotificationService;
    @Mock private DoctorNotificationService doctorNotificationService;

    @Test
    void hidesAnotherDoctorsAppointmentWhenLookingUpConsultation() {
        UUID appointmentId = UUID.randomUUID();
        Doctor requestingDoctor = doctor("doctor-a@example.test", UUID.randomUUID());
        Doctor assignedDoctor = doctor("doctor-b@example.test", UUID.randomUUID());
        Appointment appointment = new Appointment(
                new Patient(new User("patient@example.test", "hash", Role.PATIENT), null),
                assignedDoctor,
                java.time.LocalDateTime.now(),
                30);
        ReflectionTestUtils.setField(appointment, "id", appointmentId);
        appointment.setStatus(com.telemed.platform.appointment.domain.AppointmentStatus.CONFIRMED);
        when(doctorRepository.findByUserEmail("doctor-a@example.test"))
                .thenReturn(Optional.of(requestingDoctor));
        when(appointmentRepository.findWithDoctorUserById(appointmentId)).thenReturn(Optional.of(appointment));
        ConsultationService service = new ConsultationService(
                doctorRepository,
                appointmentRepository,
                consultationRepository,
                revisionRepository,
                Clock.systemUTC(),
                appointmentNotificationService,
                doctorNotificationService,
                ZoneId.of("Europe/Paris"));

        assertThatThrownBy(() -> service.findMine("doctor-a@example.test", appointmentId))
                .isInstanceOf(NotFoundException.class);
        verify(consultationRepository, never()).findByAppointmentId(appointmentId);
    }

    private Doctor doctor(String email, UUID id) {
        Doctor doctor = new Doctor(new User(email, "hash", Role.DOCTOR), "General", null);
        ReflectionTestUtils.setField(doctor, "id", id);
        return doctor;
    }
}
