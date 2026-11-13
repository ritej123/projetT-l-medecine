import { Injectable, signal } from '@angular/core';
import { Appointment, AppointmentSlot, PatientDoctorDetail } from '../../core/telemed-api.service';

export interface BookingSelection {
  doctor: PatientDoctorDetail | null;
  dateKey: string;
  slot: AppointmentSlot | null;
  consultationReason: string;
  appointment: Appointment | null;
}

@Injectable({ providedIn: 'root' })
export class BookingFlowService {
  readonly selection = signal<BookingSelection>({
    doctor: null,
    dateKey: '',
    slot: null,
    consultationReason: '',
    appointment: null
  });

  setDoctor(doctor: PatientDoctorDetail): void {
    this.selection.update(current => current.doctor?.id === doctor.id
      ? { ...current, doctor }
      : { doctor, dateKey: '', slot: null, consultationReason: '', appointment: null });
  }

  setDate(dateKey: string): void {
    this.selection.update(current => ({
      ...current,
      dateKey,
      slot: current.slot?.startsAt.startsWith(dateKey) ? current.slot : null,
      appointment: current.dateKey === dateKey ? current.appointment : null
    }));
  }

  setSlot(slot: AppointmentSlot | null): void {
    this.selection.update(current => ({
      ...current,
      dateKey: slot ? slot.startsAt.slice(0, 10) : current.dateKey,
      slot,
      appointment: current.slot?.startsAt === slot?.startsAt ? current.appointment : null
    }));
  }

  setConsultationReason(consultationReason: string): void {
    this.selection.update(current => ({ ...current, consultationReason }));
  }

  setAppointment(appointment: Appointment): void {
    this.selection.update(current => ({ ...current, appointment }));
  }

  startNewBooking(): void {
    this.selection.update(current => ({
      ...current,
      dateKey: '',
      slot: null,
      consultationReason: '',
      appointment: null
    }));
  }
}
