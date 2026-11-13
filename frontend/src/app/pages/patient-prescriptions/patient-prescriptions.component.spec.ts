import { ComponentFixture, TestBed } from '@angular/core/testing';
import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { PatientPrescription, TelemedApiService } from '../../core/telemed-api.service';
import { PatientPrescriptionsComponent } from './patient-prescriptions.component';

describe('PatientPrescriptionsComponent', () => {
  let fixture: ComponentFixture<PatientPrescriptionsComponent>;

  registerLocaleData(localeFr);

  const prescriptions: PatientPrescription[] = [
    {
      id: 'prescription-1',
      appointmentId: 'appointment-1',
      doctorId: 'doctor-1',
      doctorName: 'Nadia Ben Salem',
      doctorSpecialty: 'Cardiologie',
      content: 'Traitement numéro un',
      date: '2026-11-01',
      consultationAt: '2026-11-01T10:00:00'
    },
    {
      id: 'prescription-2',
      appointmentId: 'appointment-2',
      doctorId: 'doctor-1',
      doctorName: 'Nadia Ben Salem',
      doctorSpecialty: 'Cardiologie',
      content: 'Traitement numéro deux',
      date: '2026-11-02',
      consultationAt: '2026-11-02T10:00:00'
    },
    {
      id: 'prescription-3',
      appointmentId: 'appointment-3',
      doctorId: 'doctor-2',
      doctorName: 'Sami Trabelsi',
      doctorSpecialty: 'Médecine générale',
      content: 'Traitement numéro trois',
      date: '2026-11-03',
      consultationAt: '2026-11-03T10:00:00'
    }
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PatientPrescriptionsComponent],
      providers: [
        provideRouter([]),
        {
          provide: TelemedApiService,
          useValue: {
            patientPrescriptions: () => of(prescriptions),
            prescriptionPdf: () => of(new Blob())
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(PatientPrescriptionsComponent);
    fixture.detectChanges();
  });

  it('groups the patient prescriptions by doctor and displays their contents', () => {
    const groups = fixture.nativeElement.querySelectorAll('.doctor-group');

    expect(groups.length).toBe(2);
    expect(groups[0].textContent).toContain('Nadia Ben Salem');
    expect(groups[0].querySelectorAll('.prescription').length).toBe(2);
    expect(groups[0].textContent).toContain('Traitement numéro un');
    expect(groups[1].textContent).toContain('Sami Trabelsi');
  });
});
