import { BookingFlowService } from './booking-flow.service';
import { PatientDoctorDetail } from '../../core/telemed-api.service';

describe('BookingFlowService', () => {
  const doctor = (id: string): PatientDoctorDetail => ({
    id,
    firstName: 'Nadia',
    lastName: 'Ben Salem',
    specialty: 'Cardiologie',
    officeAddress: null,
    officeCity: 'Tunis',
    professionalNumber: null,
    bio: null,
    availabilities: []
  });

  it('keeps the selection and optional reason while moving between steps', () => {
    const flow = new BookingFlowService();
    const selectedDoctor = doctor('doctor-1');
    const slot = {
      doctorId: selectedDoctor.id,
      startsAt: '2026-11-18T10:00:00',
      endsAt: '2026-11-18T10:30:00'
    };

    flow.setDoctor(selectedDoctor);
    flow.setDate('2026-11-18');
    flow.setSlot(slot);
    flow.setConsultationReason('Contrôle annuel');
    flow.setDoctor({ ...selectedDoctor, bio: 'Présentation mise à jour' });

    expect(flow.selection().dateKey).toBe('2026-11-18');
    expect(flow.selection().slot).toEqual(slot);
    expect(flow.selection().consultationReason).toBe('Contrôle annuel');
  });

  it('clears a previous booking selection when another doctor is selected', () => {
    const flow = new BookingFlowService();
    flow.setDoctor(doctor('doctor-1'));
    flow.setDate('2026-11-18');
    flow.setConsultationReason('Contrôle annuel');

    flow.setDoctor(doctor('doctor-2'));

    expect(flow.selection().doctor?.id).toBe('doctor-2');
    expect(flow.selection().dateKey).toBe('');
    expect(flow.selection().slot).toBeNull();
    expect(flow.selection().consultationReason).toBe('');
  });
});
