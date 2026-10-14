import { Routes } from '@angular/router';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { TeleconsultationComponent } from './pages/teleconsultation/teleconsultation.component';
import { AuthComponent } from './pages/auth/auth.component';
import { PatientDashboardComponent } from './pages/patient-dashboard/patient-dashboard.component';
import { DoctorDashboardComponent } from './pages/doctor-dashboard/doctor-dashboard.component';
import { DoctorWorkspaceLayoutComponent } from './pages/doctor-dashboard/doctor-workspace-layout.component';
import { BookingComponent } from './pages/booking/booking.component';
import { AdminDashboardComponent } from './pages/admin-dashboard/admin-dashboard.component';
import { roleGuard } from './core/auth.guard';

export const routes: Routes = [
  { path: '', component: DashboardComponent },
  { path: 'connexion', component: AuthComponent, data: { mode: 'login' } },
  { path: 'inscription', component: AuthComponent, data: { mode: 'register' } },
  { path: 'patient', component: PatientDashboardComponent, canActivate: [roleGuard], data: { role: 'PATIENT' } },
  {
    path: 'medecin',
    component: DoctorWorkspaceLayoutComponent,
    canActivate: [roleGuard],
    data: { role: 'DOCTOR' },
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', component: DoctorDashboardComponent, data: { doctorPage: 'dashboard' } },
      { path: 'agenda', component: DoctorDashboardComponent, data: { doctorPage: 'agenda' } },
      { path: 'disponibilites', component: DoctorDashboardComponent, data: { doctorPage: 'disponibilites' } },
      { path: 'patients', component: DoctorDashboardComponent, data: { doctorPage: 'patients' } },
      { path: 'consultations', component: DoctorDashboardComponent, data: { doctorPage: 'consultations' } },
      { path: 'ordonnances', component: DoctorDashboardComponent, data: { doctorPage: 'ordonnances' } },
      { path: 'notifications', component: DoctorDashboardComponent, data: { doctorPage: 'notifications' } },
      { path: 'profil', component: DoctorDashboardComponent, data: { doctorPage: 'profil' } },
      { path: '**', redirectTo: 'dashboard' }
    ]
  },
  { path: 'admin', component: AdminDashboardComponent, canActivate: [roleGuard], data: { role: 'ADMIN' } },
  { path: 'reservation', component: BookingComponent, canActivate: [roleGuard], data: { role: 'PATIENT' } },
  {
    path: 'teleconsultation/:appointmentId',
    component: TeleconsultationComponent,
    canActivate: [roleGuard],
    data: { roles: ['PATIENT', 'DOCTOR'] }
  },
  { path: '**', redirectTo: '' }
];
