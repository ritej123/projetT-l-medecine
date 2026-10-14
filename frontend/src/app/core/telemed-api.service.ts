import { Inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_BASE_URL } from './api.config';

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface UserClaims {
  sub: string;
  email: string;
  roles: string[];
}

export interface Appointment {
  id: string;
  patientId: string;
  doctorId: string;
  scheduledAt: string;
  durationMinutes: number;
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'DONE';
}

export interface DoctorProfile {
  id: string;
  specialty: string;
  bio: string | null;
}

export type DayOfWeek =
  | 'MONDAY'
  | 'TUESDAY'
  | 'WEDNESDAY'
  | 'THURSDAY'
  | 'FRIDAY'
  | 'SATURDAY'
  | 'SUNDAY';

export interface DoctorAvailability {
  id: string;
  doctorId: string;
  dayOfWeek: DayOfWeek;
  startTime: string;
  endTime: string;
  slotDurationMinutes: number;
}

export interface DoctorDashboardAppointment {
  id: string;
  patientId: string;
  patientEmail: string;
  scheduledAt: string;
  durationMinutes: number;
  status: Appointment['status'];
}

export interface DoctorDashboardStats {
  scheduledToday: number;
  upcomingAppointments: number;
  completedToday: number;
  pendingToday: number;
  cancelledToday: number;
  followedPatients: number;
  nextAppointments: DoctorDashboardAppointment[];
  recentActivities: DoctorDashboardAppointment[];
}

export interface DoctorPatient {
  patientId: string;
  email: string;
  lastAppointment: string | null;
  nextAppointment: string | null;
}

export interface DoctorPatientDetail {
  patientId: string;
  email: string;
  medicalRecord: string | null;
  appointmentHistory: Array<{
    appointmentId: string;
    scheduledAt: string;
    durationMinutes: number;
    status: Appointment['status'];
  }>;
  consultationHistory: Array<{
    appointmentId: string;
    scheduledAt: string;
    report: string | null;
    observations: string | null;
    diagnosis: string | null;
  }>;
}

export interface DoctorConsultation {
  id: string;
  appointmentId: string;
  report: string | null;
  observations: string | null;
  diagnosis: string | null;
  finalized: boolean;
  createdAt: string;
  updatedAt: string;
  revisions: Array<{
    revisionNumber: number;
    report: string | null;
    observations: string | null;
    diagnosis: string | null;
    actorUserId: string;
    createdAt: string;
  }>;
}

export interface DoctorProfessionalProfile {
  doctorId: string;
  email: string;
  firstName: string | null;
  lastName: string | null;
  profilePhotoUrl: string | null;
  specialty: string;
  bio: string | null;
  professionalPhone: string | null;
  officeAddress: string | null;
  professionalNumber: string | null;
  professionalNumberVerified: boolean;
  emailNotificationsEnabled: boolean;
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED';
}

export interface AvailabilityException {
  id: string;
  date: string;
  startTime: string | null;
  endTime: string | null;
  reason: string | null;
}

export interface DoctorNotification {
  id: string;
  appointmentId: string | null;
  type: 'NEW_APPOINTMENT' | 'APPOINTMENT_CANCELLED' | 'APPOINTMENT_UPDATED' | 'ADMIN_NOTICE';
  message: string;
  createdAt: string;
  readAt: string | null;
}

export interface AppointmentSlot {
  doctorId: string;
  startsAt: string;
  endsAt: string;
}

export interface Prescription {
  id: string;
  appointmentId: string;
  content: string;
  status: 'DRAFT' | 'VALIDATED';
  date: string | null;
}

export interface PrescriptionRevision {
  id: string;
  revisionNumber: number;
  content: string;
  actorUserId: string;
  changeType: string;
  createdAt: string;
}

export interface AdminDoctor {
  id: string;
  email: string;
  specialty: string;
  bio: string | null;
  verificationStatus: 'PENDING' | 'VERIFIED' | 'REJECTED';
  enabled: boolean;
  createdAt: string;
}

export interface AdminPatient {
  id: string;
  email: string;
  enabled: boolean;
  createdAt: string;
}

export interface AdminAppointment {
  id: string;
  patientEmail: string;
  doctorEmail: string;
  specialty: string;
  scheduledAt: string;
  durationMinutes: number;
  status: Appointment['status'];
}

export interface AdminAuditLog {
  id: string;
  actorEmail: string;
  action: string;
  targetType: string;
  targetId: string;
  result: 'SUCCESS' | 'REJECTED';
  createdAt: string;
}

export interface AdminMonthlyMetric {
  month: string;
  registrations: number;
  appointments: number;
}

export interface AdminDashboardStats {
  totalPatients: number;
  totalDoctors: number;
  pendingDoctors: number;
  verifiedActiveDoctors: number;
  suspendedAccounts: number;
  totalAppointments: number;
  appointmentsToday: number;
  upcomingAppointments: number;
  cancelledAppointments: number;
  monthlyActivity: AdminMonthlyMetric[];
  recentDoctorApplications: AdminDoctor[];
  recentAppointments: AdminAppointment[];
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

@Injectable({ providedIn: 'root' })
export class TelemedApiService {
  private readonly baseUrl: string;

  constructor(private readonly http: HttpClient, @Inject(API_BASE_URL) baseUrl: string) {
    this.baseUrl = `${baseUrl.replace(/\/$/, '')}/api/v1`;
  }

  login(email: string, password: string): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${this.baseUrl}/auth/login`, { email, password });
  }

  registerPatient(email: string, password: string): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${this.baseUrl}/auth/register`, { email, password });
  }

  registerDoctor(email: string, password: string, specialty: string, bio: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/register/doctor`, {
      email,
      password,
      specialty,
      bio: bio || null
    });
  }

  refresh(refreshToken: string): Observable<AuthTokens> {
    return this.http.post<AuthTokens>(`${this.baseUrl}/auth/refresh`, { refreshToken });
  }

  logout(refreshToken: string): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/auth/logout`, { refreshToken });
  }

  patientAppointments(): Observable<Appointment[]> {
    return this.http.get<Appointment[]>(`${this.baseUrl}/patient/appointments`);
  }

  doctorAppointments(): Observable<Appointment[]> {
    return this.http.get<Appointment[]>(`${this.baseUrl}/doctor/appointments`);
  }

  doctorDashboardStats(): Observable<DoctorDashboardStats> {
    return this.http.get<DoctorDashboardStats>(`${this.baseUrl}/doctor/dashboard/stats`);
  }

  doctorPatients(search = '', page = 0, size = 20): Observable<PageResponse<DoctorPatient>> {
    const params = new HttpParams().set('search', search).set('page', page).set('size', size);
    return this.http.get<PageResponse<DoctorPatient>>(`${this.baseUrl}/doctor/patients`, { params });
  }

  doctorPatient(patientId: string): Observable<DoctorPatientDetail> {
    return this.http.get<DoctorPatientDetail>(`${this.baseUrl}/doctor/patients/${patientId}`);
  }

  doctorConsultation(appointmentId: string): Observable<DoctorConsultation> {
    return this.http.get<DoctorConsultation>(
      `${this.baseUrl}/doctor/appointments/${appointmentId}/consultation`
    );
  }

  saveDoctorConsultation(
    appointmentId: string,
    consultation: Pick<DoctorConsultation, 'report' | 'observations' | 'diagnosis' | 'finalized'>
  ): Observable<DoctorConsultation> {
    return this.http.put<DoctorConsultation>(
      `${this.baseUrl}/doctor/appointments/${appointmentId}/consultation`,
      consultation
    );
  }

  createDoctorPrescription(appointmentId: string, content: string): Observable<Prescription> {
    return this.http.post<Prescription>(
      `${this.baseUrl}/doctor/appointments/${appointmentId}/prescription`,
      { content }
    );
  }

  doctorPrescription(appointmentId: string): Observable<Prescription> {
    return this.http.get<Prescription>(
      `${this.baseUrl}/doctor/appointments/${appointmentId}/prescription`
    );
  }

  validateDoctorPrescription(prescriptionId: string): Observable<Prescription> {
    return this.http.patch<Prescription>(
      `${this.baseUrl}/doctor/prescriptions/${prescriptionId}/validate`,
      {}
    );
  }

  doctorPrescriptionHistory(prescriptionId: string): Observable<PrescriptionRevision[]> {
    return this.http.get<PrescriptionRevision[]>(
      `${this.baseUrl}/doctor/prescriptions/${prescriptionId}/history`
    );
  }

  updateDoctorPrescription(prescriptionId: string, content: string): Observable<Prescription> {
    return this.http.put<Prescription>(`${this.baseUrl}/doctor/prescriptions/${prescriptionId}`, { content });
  }

  doctorPrescriptionPdf(prescriptionId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/doctor/prescriptions/${prescriptionId}/pdf`, {
      responseType: 'blob'
    });
  }

  doctorProfile(): Observable<DoctorProfessionalProfile> {
    return this.http.get<DoctorProfessionalProfile>(`${this.baseUrl}/doctor/profile`);
  }

  updateDoctorProfile(
    profile: Pick<DoctorProfessionalProfile, 'firstName' | 'lastName' | 'profilePhotoUrl' | 'professionalPhone'
      | 'officeAddress' | 'professionalNumber' | 'emailNotificationsEnabled'>
  ): Observable<DoctorProfessionalProfile> {
    return this.http.put<DoctorProfessionalProfile>(`${this.baseUrl}/doctor/profile`, profile);
  }

  doctorNotifications(): Observable<DoctorNotification[]> {
    return this.http.get<DoctorNotification[]>(`${this.baseUrl}/doctor/notifications`);
  }

  markDoctorNotificationRead(notificationId: string): Observable<DoctorNotification> {
    return this.http.patch<DoctorNotification>(
      `${this.baseUrl}/doctor/notifications/${notificationId}/read`,
      {}
    );
  }

  doctorAvailabilities(): Observable<DoctorAvailability[]> {
    return this.http.get<DoctorAvailability[]>(`${this.baseUrl}/doctor/availabilities`);
  }

  createDoctorAvailability(
    availability: Omit<DoctorAvailability, 'id' | 'doctorId'>
  ): Observable<DoctorAvailability> {
    return this.http.post<DoctorAvailability>(`${this.baseUrl}/doctor/availabilities`, availability);
  }

  updateDoctorAvailability(
    id: string,
    availability: Omit<DoctorAvailability, 'id' | 'doctorId'>
  ): Observable<DoctorAvailability> {
    return this.http.put<DoctorAvailability>(`${this.baseUrl}/doctor/availabilities/${id}`, availability);
  }

  deleteDoctorAvailability(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/doctor/availabilities/${id}`);
  }

  doctorAvailabilityExceptions(from: string, to: string): Observable<AvailabilityException[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<AvailabilityException[]>(
      `${this.baseUrl}/doctor/availability-exceptions`,
      { params }
    );
  }

  createDoctorAvailabilityException(exception: Omit<AvailabilityException, 'id'>): Observable<AvailabilityException> {
    return this.http.post<AvailabilityException>(
      `${this.baseUrl}/doctor/availability-exceptions`,
      exception
    );
  }

  deleteDoctorAvailabilityException(id: string): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/doctor/availability-exceptions/${id}`);
  }


  patientDoctors(): Observable<DoctorProfile[]> {
    return this.http.get<DoctorProfile[]>(`${this.baseUrl}/patient/doctors`);
  }

  patientPrescriptions(): Observable<Prescription[]> {
    return this.http.get<Prescription[]>(`${this.baseUrl}/patient/prescriptions`);
  }

  availableSlots(doctorId: string, from: string, to: string): Observable<AppointmentSlot[]> {
    const params = new HttpParams().set('from', from).set('to', to);
    return this.http.get<AppointmentSlot[]>(
      `${this.baseUrl}/patient/appointments/doctors/${doctorId}/slots`,
      { params }
    );
  }

  bookAppointment(doctorId: string, scheduledAt: string): Observable<Appointment> {
    return this.http.post<Appointment>(`${this.baseUrl}/patient/appointments`, { doctorId, scheduledAt });
  }

  updateAppointmentStatus(appointmentId: string, status: Appointment['status']): Observable<Appointment> {
    return this.http.patch<Appointment>(
      `${this.baseUrl}/doctor/appointments/${appointmentId}/status`,
      { status }
    );
  }

  rescheduleDoctorAppointment(appointmentId: string, scheduledAt: string): Observable<Appointment> {
    return this.http.patch<Appointment>(
      `${this.baseUrl}/doctor/appointments/${appointmentId}/reschedule`,
      { scheduledAt }
    );
  }

  cancelAppointment(appointmentId: string): Observable<Appointment> {
    return this.http.patch<Appointment>(
      `${this.baseUrl}/patient/appointments/${appointmentId}/cancel`,
      {}
    );
  }

  prescriptionPdf(prescriptionId: string): Observable<Blob> {
    return this.http.get(`${this.baseUrl}/patient/prescriptions/${prescriptionId}/pdf`, {
      responseType: 'blob'
    });
  }

  adminDashboardStats(): Observable<AdminDashboardStats> {
    return this.http.get<AdminDashboardStats>(`${this.baseUrl}/admin/dashboard/stats`);
  }

  adminDoctors(filters: {
    search?: string;
    status?: string;
    enabled?: string;
    from?: string;
    to?: string;
    page: number;
    size: number;
  }): Observable<PageResponse<AdminDoctor>> {
    return this.http.get<PageResponse<AdminDoctor>>(`${this.baseUrl}/admin/doctors`, {
      params: this.adminParams(filters)
    });
  }

  verifyAdminDoctor(id: string): Observable<AdminDoctor> {
    return this.http.patch<AdminDoctor>(`${this.baseUrl}/admin/doctors/${id}/verify`, {});
  }

  rejectAdminDoctor(id: string): Observable<AdminDoctor> {
    return this.http.patch<AdminDoctor>(`${this.baseUrl}/admin/doctors/${id}/reject`, {});
  }

  setAdminDoctorEnabled(id: string, enabled: boolean): Observable<AdminDoctor> {
    return this.http.patch<AdminDoctor>(`${this.baseUrl}/admin/doctors/${id}/status`, { enabled });
  }

  adminPatients(filters: {
    search?: string;
    enabled?: string;
    from?: string;
    to?: string;
    page: number;
    size: number;
  }): Observable<PageResponse<AdminPatient>> {
    return this.http.get<PageResponse<AdminPatient>>(`${this.baseUrl}/admin/patients`, {
      params: this.adminParams(filters)
    });
  }

  setAdminPatientEnabled(id: string, enabled: boolean): Observable<AdminPatient> {
    return this.http.patch<AdminPatient>(`${this.baseUrl}/admin/patients/${id}/status`, { enabled });
  }

  adminAppointments(filters: {
    search?: string;
    status?: string;
    from?: string;
    to?: string;
    page: number;
    size: number;
  }): Observable<PageResponse<AdminAppointment>> {
    return this.http.get<PageResponse<AdminAppointment>>(`${this.baseUrl}/admin/appointments`, {
      params: this.adminParams(filters)
    });
  }

  adminAuditLogs(page: number, size = 50): Observable<PageResponse<AdminAuditLog>> {
    return this.http.get<PageResponse<AdminAuditLog>>(`${this.baseUrl}/admin/audit-logs`, {
      params: this.adminParams({ page, size })
    });
  }

  private adminParams(filters: Record<string, string | number | undefined>): HttpParams {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) {
      if (value !== undefined && value !== '') {
        params = params.set(key, String(value));
      }
    }
    return params;
  }
}
