import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import {
  AvailabilityException,
  Appointment,
  DayOfWeek,
  DoctorConsultation,
  DoctorDashboardStats,
  DoctorAvailability,
  DoctorNotification,
  DoctorPatient,
  DoctorPatientDetail,
  DoctorProfessionalProfile,
  PageResponse,
  Prescription,
  PrescriptionRevision,
  TelemedApiService
} from '../../core/telemed-api.service';

type DoctorPage =
  | 'dashboard'
  | 'agenda'
  | 'disponibilites'
  | 'patients'
  | 'consultations'
  | 'ordonnances'
  | 'notifications'
  | 'profil';

const doctorPageTitles: Record<DoctorPage, string> = {
  dashboard: "Vue d'ensemble",
  agenda: 'Mon agenda',
  disponibilites: 'Mes disponibilités',
  patients: 'Mes patients',
  consultations: 'Consultations',
  ordonnances: 'Ordonnances',
  notifications: 'Notifications',
  profil: 'Mon profil'
};

function isDoctorPage(value: unknown): value is DoctorPage {
  return typeof value === 'string' && value in doctorPageTitles;
}

@Component({
  selector: 'app-doctor-dashboard',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <main class="content">
      <header class="topbar"><div class="breadcrumb">Mon espace <span>/</span> {{ pageTitle }}</div><div class="user-pill"><span class="avatar">{{ initials }}</span><span>{{ email }}</span></div></header>
      @if (activePage() === 'dashboard') {
        <section id="overview" class="welcome">
          <div><span class="eyebrow">VOTRE JOURNÉE</span><h1>Bonjour, Docteur <span>✦</span></h1><p>Voici le programme de vos consultations et vos prochains patients.</p></div>
          <button type="button" class="today-button" (click)="selectDate(today)"><span class="material-icons">today</span>Aujourd’hui</button>
        </section>
        @if (error()) { <div class="notice" role="alert">{{ error() }}</div> }
        <section class="summary-grid" aria-label="Résumé de votre agenda">
          <article class="summary-card"><div class="summary-icon mint"><span class="material-icons">event</span></div><p>Rendez-vous aujourd’hui</p><strong>{{ stats()?.scheduledToday ?? '—' }}</strong><small>tous statuts</small></article>
          <article class="summary-card"><div class="summary-icon blue"><span class="material-icons">upcoming</span></div><p>À venir</p><strong>{{ stats()?.upcomingAppointments ?? '—' }}</strong><small>confirmés ou en attente</small></article>
          <article class="summary-card"><div class="summary-icon lilac"><span class="material-icons">check_circle</span></div><p>Terminés aujourd’hui</p><strong>{{ stats()?.completedToday ?? '—' }}</strong><small>consultations clôturées</small></article>
          <article class="summary-card"><div class="summary-icon blue"><span class="material-icons">hourglass_top</span></div><p>En attente aujourd’hui</p><strong>{{ stats()?.pendingToday ?? '—' }}</strong><small>à traiter</small></article>
          <article class="summary-card"><div class="summary-icon lilac"><span class="material-icons">event_busy</span></div><p>Annulés aujourd’hui</p><strong>{{ stats()?.cancelledToday ?? '—' }}</strong><small>rendez-vous</small></article>
          <article class="summary-card"><div class="summary-icon mint"><span class="material-icons">groups</span></div><p>Patients suivis</p><strong>{{ stats()?.followedPatients ?? '—' }}</strong><small>consultation confirmée ou passée</small></article>
        </section>

        <section class="overview-panels">
          <article class="overview-panel"><div class="section-heading"><div><span class="eyebrow">À VENIR</span><h2>Prochains rendez-vous</h2></div></div>
            @if (loadingStats()) { <div class="empty"><span class="loader"></span><p>Chargement des statistiques…</p></div> }
            @else if (stats()?.nextAppointments?.length) {
              @for (appointment of stats()!.nextAppointments; track appointment.id) {
                <button class="activity-row" type="button" (click)="openConsultation(appointment.id, appointment.patientId)">
                  <span class="activity-time">{{ appointmentDate(appointment.scheduledAt) | date:'EEE d MMM · HH:mm':'UTC':'fr' }}</span>
                  <span>{{ appointment.patientEmail }}</span><span class="status" [class.done]="appointment.status === 'DONE'">{{ statusLabel(appointment.status) }}</span>
                </button>
              }
            } @else { <div class="empty small"><p>Aucun rendez-vous à venir.</p></div> }
          </article>
          <article class="overview-panel"><div class="section-heading"><div><span class="eyebrow">SUIVI</span><h2>Activité récente</h2></div></div>
            @if (stats()?.recentActivities?.length) {
              @for (activity of stats()!.recentActivities; track activity.id) {
                <div class="activity-row"><span class="activity-time">{{ appointmentDate(activity.scheduledAt) | date:'d MMM · HH:mm':'UTC':'fr' }}</span><span>{{ activity.patientEmail }}</span><span class="status">{{ statusLabel(activity.status) }}</span></div>
              }
            } @else { <div class="empty small"><p>Aucune activité récente.</p></div> }
          </article>
        </section>
      }
      @if (error() && activePage() !== 'dashboard') { <div class="notice" role="alert">{{ error() }}</div> }

        <section id="disponibilites" class="availability-section" [hidden]="activePage() !== 'disponibilites'">
          <div class="section-heading"><div><span class="eyebrow">ORGANISATION DU CABINET</span><h2>Mes disponibilités hebdomadaires</h2></div></div>
          <form class="availability-form" (ngSubmit)="saveAvailability()">
            <label>Jour
              <select name="availabilityDay" [(ngModel)]="availabilityDay">
                @for (day of weekdays; track day.value) { <option [ngValue]="day.value">{{ day.label }}</option> }
              </select>
            </label>
            <label>De
              <input name="availabilityStart" type="time" required [(ngModel)]="availabilityStart">
            </label>
            <label>À
              <input name="availabilityEnd" type="time" required [(ngModel)]="availabilityEnd">
            </label>
            <label>Durée d’un créneau
              <select name="slotDurationMinutes" [(ngModel)]="slotDurationMinutes">
                <option [ngValue]="15">15 minutes</option>
                <option [ngValue]="20">20 minutes</option>
                <option [ngValue]="30">30 minutes</option>
                <option [ngValue]="45">45 minutes</option>
                <option [ngValue]="60">60 minutes</option>
              </select>
            </label>
            <button type="submit" [disabled]="savingAvailability()">
              {{ savingAvailability() ? 'Enregistrement…' : editingAvailabilityId ? 'Enregistrer les modifications' : 'Ajouter cette plage' }}
            </button>
            @if (editingAvailabilityId) { <button class="secondary-action" type="button" (click)="cancelAvailabilityEdit()">Annuler la modification</button> }
          </form>
          @if (availabilities().length === 0) {
            <div class="availability-empty">Aucune plage hebdomadaire configurée. Ajoutez vos heures de consultation pour ouvrir des créneaux aux patients.</div>
          } @else {
            <div class="availability-list">
              @for (availability of availabilities(); track availability.id) {
                <div class="availability-item">
                  <strong>{{ dayLabel(availability.dayOfWeek) }}</strong>
                  <span>{{ formatTime(availability.startTime) }} – {{ formatTime(availability.endTime) }}</span>
                  <small>Créneaux de {{ availability.slotDurationMinutes }} min</small>
                  <button class="text-action" type="button" (click)="editAvailability(availability)">Modifier</button>
                  <button class="text-action danger" type="button" (click)="deleteAvailability(availability)">Supprimer</button>
                </div>
              }
              @if (availabilities().length > 0) {
                <div class="availability-calendar" aria-label="Vue calendrier des disponibilités hebdomadaires">
                  @for (day of weekdays; track day.value) {
                    <div class="availability-day"><strong>{{ day.label }}</strong>
                      @for (availability of availabilitiesForDay(day.value); track availability.id) {
                        <span>{{ formatTime(availability.startTime) }}–{{ formatTime(availability.endTime) }} · {{ availability.slotDurationMinutes }} min</span>
                      }
                      @if (availabilitiesForDay(day.value).length === 0) { <small>Indisponible</small> }
                    </div>
                  }
                </div>
              }
            </div>
          }
          <div class="section-heading exception-heading"><div><span class="eyebrow">EXCEPTIONS AU CALENDRIER</span><h2>Congés et indisponibilités</h2></div></div>
          <form class="availability-form exception-form" (ngSubmit)="saveAvailabilityException()">
            <label>Date<input name="exceptionDate" type="date" required [(ngModel)]="exceptionDate"></label>
            <label>De (vide = journée entière)<input name="exceptionStart" type="time" [(ngModel)]="exceptionStart"></label>
            <label>À<input name="exceptionEnd" type="time" [(ngModel)]="exceptionEnd"></label>
            <label>Motif<input name="exceptionReason" maxlength="240" [(ngModel)]="exceptionReason" placeholder="Congé, pause…"></label>
            <button type="submit" [disabled]="savingException()">Enregistrer l’indisponibilité</button>
          </form>
          @if (availabilityExceptions().length === 0) {
            <div class="availability-empty">Aucune exception enregistrée sur les douze prochains mois.</div>
          } @else {
            <div class="availability-list exception-list">
              @for (exception of availabilityExceptions(); track exception.id) {
                <div class="availability-item"><strong>{{ dateOnly(exception.date) | date:'EEE d MMM y':'UTC':'fr' }}</strong>
                  <span>{{ exception.startTime ? formatTime(exception.startTime) + ' – ' + formatTime(exception.endTime ?? '') : 'Journée entière' }}</span>
                  <small>{{ exception.reason || 'Indisponibilité' }}</small>
                  <button class="text-action danger" type="button" (click)="deleteAvailabilityException(exception)">Supprimer</button>
                </div>
              }
            </div>
          }
        </section>

        <section id="agenda" class="agenda-section" [hidden]="activePage() !== 'agenda'">
          <div class="section-heading"><div><span class="eyebrow">VOTRE PLANNING</span><h2>Agenda des consultations</h2></div>
            <div class="agenda-controls">
              <div class="view-switch"><button type="button" [class.chosen]="agendaView() === 'day'" (click)="setAgendaView('day')">Jour</button><button type="button" [class.chosen]="agendaView() === 'week'" (click)="setAgendaView('week')">Semaine</button><button type="button" [class.chosen]="agendaView() === 'month'" (click)="setAgendaView('month')">Mois</button></div>
              <div class="week-nav"><button type="button" aria-label="Période précédente" (click)="changePeriod(-1)">‹</button><strong>{{ periodLabel }}</strong><button type="button" aria-label="Période suivante" (click)="changePeriod(1)">›</button></div>
              <button type="button" class="today-button" (click)="selectDate(today)">Aujourd’hui</button>
            </div>
          </div>
          <label class="status-filter">Filtrer par statut
            <select name="agendaStatus" [(ngModel)]="statusFilter">
              <option value="ALL">Tous les statuts</option><option value="PENDING">En attente</option><option value="CONFIRMED">Confirmés</option><option value="DONE">Terminés</option><option value="CANCELLED">Annulés</option>
            </select>
          </label>
          <div class="calendar">
            @if (agendaView() === 'week') { <div class="week-grid">
              @for (day of weekDays; track day.key) {
                <button type="button" class="day" [class.selected]="selectedDateKey() === day.key"
                        [class.is-today]="day.key === todayKey" (click)="selectDate(day.date)">
                  <span>{{ day.date | date:'EEE':'':'fr' }}</span><strong>{{ dateOnly(day.key) | date:'dd':'UTC' }}</strong>
                  @if (appointmentsOn(day.key).length > 0) { <i aria-label="consultation planifiée"></i> }
                </button>
              }
            </div> }
            @if (agendaView() === 'month') {
              <div class="month-grid">
                @for (day of monthDays; track day.key) {
                  <button type="button" class="month-day" [class.outside-month]="day.date.getMonth() !== selectedDate().getMonth()" [class.is-today]="day.key === todayKey" [class.selected]="day.key === selectedDateKey()" (click)="selectDate(day.date)">
                    <span>{{ dateOnly(day.key) | date:'d':'UTC' }}</span>@if (appointmentsOn(day.key).length) { <small>{{ appointmentsOn(day.key).length }} RDV</small> }
                  </button>
                }
              </div>
            }
            <div class="day-heading"><span class="material-icons">calendar_month</span>{{ dateOnly(selectedDateKey()) | date:'EEEE d MMMM y':'UTC':'fr' }}</div>
            @if (loading()) {
              <div class="empty"><span class="loader"></span><p>Chargement de l’agenda…</p></div>
            } @else if (filteredAppointmentsOn(selectedDateKey()).length === 0) {
              <div class="empty"><span class="empty-icon material-icons">event_available</span><h3>Votre journée est libre</h3><p>Aucun rendez-vous sur cette date.</p></div>
            } @else {
              <div class="schedule">
                @for (appointment of filteredAppointmentsOn(selectedDateKey()); track appointment.id) {
                  <article class="schedule-item">
                    <div class="time">{{ appointmentDate(appointment.scheduledAt) | date:'HH:mm':'UTC' }}<small>{{ appointment.durationMinutes }} min</small></div>
                    <div class="timeline"><span></span></div>
                    <div class="visit-card">
                      <div class="visit-main"><div class="patient-avatar">{{ patientInitials(appointment.patientId) }}</div><div><h3>{{ patientLabel(appointment) }}</h3><p>Consultation médicale · {{ statusLabel(appointment.status) }}</p></div></div>
                      <div class="visit-actions">
                        <button type="button" class="text-action" (click)="openConsultation(appointment.id, appointment.patientId)">Dossier et compte rendu</button>
                        @if (appointment.status === 'PENDING') { <button type="button" class="confirm" (click)="setStatus(appointment, 'CONFIRMED')">Confirmer</button> }
                        @if (appointment.status === 'CONFIRMED') {
                          <a class="video" [routerLink]="['/teleconsultation', appointment.id]"><span class="material-icons">videocam</span>Rejoindre</a>
                          <button type="button" class="complete" [disabled]="!isAppointmentStarted(appointment.scheduledAt)" (click)="setStatus(appointment, 'DONE')">Terminer</button>
                        }
                        @if (appointment.status === 'PENDING' || appointment.status === 'CONFIRMED') { <button type="button" class="text-action danger" (click)="cancelAppointment(appointment)">Annuler</button> }
                        @if (appointment.status === 'PENDING' || appointment.status === 'CONFIRMED') { <button type="button" class="text-action" (click)="startReschedule(appointment)">Modifier l’horaire</button> }
                      </div>
                      @if (reschedulingAppointmentId() === appointment.id) {
                        <form class="reschedule-form" (ngSubmit)="saveReschedule(appointment)">
                          <label>Nouvel horaire<input name="rescheduleAt" type="datetime-local" required [(ngModel)]="rescheduleDateTime"></label>
                          <button type="submit" [disabled]="savingReschedule()">Confirmer</button>
                          <button type="button" class="text-action" (click)="reschedulingAppointmentId.set('')">Annuler</button>
                        </form>
                      }
                    </div>
                  </article>
                }
              </div>
            }
          </div>
        </section>

        <section id="patients" class="patient-section" [hidden]="activePage() !== 'patients'">
          <div class="section-heading"><div><span class="eyebrow">SUIVI AUTORISÉ</span><h2>Mes patients</h2></div><span class="count">{{ patientPage()?.totalElements ?? 0 }} patients</span></div>
          <div class="patient-search"><input aria-label="Rechercher un patient" type="search" placeholder="Rechercher par e-mail" [(ngModel)]="patientSearch"><button type="button" (click)="searchPatients()">Rechercher</button></div>
          @if (loadingPatients()) { <div class="empty"><span class="loader"></span><p>Chargement des patients autorisés…</p></div> }
          @else if (patientPage()?.content?.length) {
            <div class="patient-table">
              @for (patient of patientPage()!.content; track patient.patientId) {
                <button class="patient-row patient-row-button" type="button" (click)="openPatient(patient.patientId)">
                  <span class="patient-avatar">{{ patientInitials(patient.patientId) }}</span>
                  <span class="patient-data"><strong>{{ patient.email }}</strong><small>Dernier rendez-vous : {{ patient.lastAppointment ? (appointmentDate(patient.lastAppointment) | date:'d MMM y':'UTC':'fr') : '—' }} · Prochain : {{ patient.nextAppointment ? (appointmentDate(patient.nextAppointment) | date:'d MMM y':'UTC':'fr') : '—' }}</small></span>
                  <span class="text-action">Ouvrir le dossier</span>
                </button>
              }
            </div>
            <div class="pagination"><button type="button" [disabled]="patientPageNumber() === 0" (click)="changePatientPage(-1)">Précédent</button><span>Page {{ patientPageNumber() + 1 }} / {{ patientPage()?.totalPages || 1 }}</span><button type="button" [disabled]="patientPageNumber() + 1 >= (patientPage()?.totalPages ?? 0)" (click)="changePatientPage(1)">Suivant</button></div>
          } @else { <div class="empty small"><p>Aucun patient autorisé ne correspond à cette recherche.</p></div> }
          @if (selectedPatient()) {
            <article class="detail-panel"><button class="close-detail" type="button" (click)="selectedPatient.set(null)">Fermer</button><span class="eyebrow">DOSSIER PATIENT AUTORISÉ</span><h3>{{ selectedPatient()?.email }}</h3>
              <h4>Dossier médical</h4><p class="medical-record">{{ selectedPatient()?.medicalRecord || 'Aucune information médicale enregistrée.' }}</p>
              <h4>Historique des rendez-vous autorisés</h4>
              @for (visit of selectedPatient()?.appointmentHistory ?? []; track visit.appointmentId) { <div class="activity-row"><span>{{ appointmentDate(visit.scheduledAt) | date:'d MMM y · HH:mm':'UTC':'fr' }}</span><span>{{ visit.durationMinutes }} min</span><span class="status">{{ statusLabel(visit.status) }}</span></div> }
              <h4>Comptes rendus précédents</h4>
              @if (selectedPatient()?.consultationHistory?.length) {
                @for (record of selectedPatient()!.consultationHistory; track record.appointmentId) {
                  <div class="clinical-record"><strong>{{ appointmentDate(record.scheduledAt) | date:'d MMM y':'UTC':'fr' }}</strong>
                    @if (record.report) { <p><b>Compte rendu</b><br>{{ record.report }}</p> }
                    @if (record.observations) { <p><b>Observations</b><br>{{ record.observations }}</p> }
                    @if (record.diagnosis) { <p><b>Diagnostic</b><br>{{ record.diagnosis }}</p> }
                  </div>
                }
              } @else { <p class="muted">Aucun compte rendu antérieur disponible.</p> }
            </article>
          }
        </section>

        <section id="consultations" class="patient-section" [hidden]="activePage() !== 'consultations'">
          <div class="section-heading"><div><span class="eyebrow">DOCUMENTATION CLINIQUE</span><h2>Consultations</h2></div></div>
          @if (!selectedAppointmentId()) { <div class="empty small"><p>Ouvrez un rendez-vous confirmé ou terminé depuis l’agenda pour consulter le dossier autorisé et rédiger un compte rendu.</p></div> }
          @else {
            <article class="detail-panel">
              <button class="close-detail" type="button" (click)="closeConsultation()">Fermer</button>
              <span class="eyebrow">RENDEZ-VOUS SÉLECTIONNÉ</span><h3>{{ selectedPatient()?.email || patientLabelById(selectedPatientId()) }}</h3>
              @if (selectedPatient()?.medicalRecord) { <p class="medical-record">{{ selectedPatient()?.medicalRecord }}</p> }
              @if (consultationFinalized()) { <div class="notice">Cette consultation est finalisée. Le compte rendu et son historique sont en lecture seule.</div> }
              <div class="consultation-form">
                <label>Compte rendu<textarea name="consultationReport" rows="5" maxlength="20000" [(ngModel)]="consultationReport" [disabled]="consultationFinalized()"></textarea></label>
                <label>Observations cliniques<textarea name="consultationObservations" rows="4" maxlength="20000" [(ngModel)]="consultationObservations" [disabled]="consultationFinalized()"></textarea></label>
                <label>Diagnostic<textarea name="consultationDiagnosis" rows="3" maxlength="10000" [(ngModel)]="consultationDiagnosis" [disabled]="consultationFinalized()"></textarea></label>
                <div class="form-actions"><button type="button" [disabled]="savingConsultation() || consultationFinalized()" (click)="saveConsultation(false)">Enregistrer le brouillon</button><button type="button" class="confirm" [disabled]="savingConsultation() || consultationFinalized() || !selectedAppointmentStarted()" (click)="saveConsultation(true)">Finaliser et terminer</button></div>
              </div>
              @if (consultation()?.revisions?.length) {
                <h4>Historique des modifications</h4>
                @for (revision of consultation()!.revisions; track revision.revisionNumber) { <div class="activity-row"><strong>Version {{ revision.revisionNumber }}</strong><span>{{ revision.createdAt | date:'d MMM y · HH:mm':'Europe/Paris':'fr' }}</span><span>{{ revision.report || 'Brouillon sauvegardé' }}</span></div> }
              }
            </article>
          }
        </section>

        <section id="ordonnances" class="patient-section" [hidden]="activePage() !== 'ordonnances'">
          <div class="section-heading"><div><span class="eyebrow">PRESCRIPTIONS</span><h2>Ordonnances</h2></div></div>
          @if (!selectedAppointmentId()) { <div class="empty small"><p>Sélectionnez une consultation confirmée ou terminée pour rédiger une ordonnance. Aucun contenu médical n’est généré automatiquement.</p></div> }
          @else {
            <article class="detail-panel">
              @if (!canPrescribe()) { <div class="notice">L’ordonnance ne peut être créée qu’après la consultation. Finalisez d’abord le rendez-vous.</div> }
              @if (createdPrescription()) {
                <div class="notice">Ordonnance existante · {{ createdPrescription()?.status === 'VALIDATED' ? 'validée et signée' : 'brouillon' }}</div>
                @for (revision of prescriptionHistory(); track revision.id) { <div class="activity-row"><strong>{{ revision.changeType }}</strong><span>{{ revision.createdAt | date:'d MMM y · HH:mm':'Europe/Paris':'fr' }}</span><span>{{ revision.content }}</span></div> }
              }
              <label>Contenu de l’ordonnance<textarea name="prescriptionContent" rows="5" maxlength="10000" [(ngModel)]="prescriptionContent" [disabled]="createdPrescription()?.status === 'VALIDATED' || !canPrescribe()"></textarea></label>
              <div class="form-actions">
                @if (!createdPrescription() && canPrescribe()) { <button type="button" [disabled]="!prescriptionContent.trim()" (click)="createPrescription()">Enregistrer le brouillon</button> }
                @else if (createdPrescription()?.status === 'DRAFT') { <button type="button" (click)="updatePrescription()">Enregistrer les modifications</button><button type="button" (click)="validatePrescription()">Valider et signer</button> }
                @else if (createdPrescription()?.status === 'VALIDATED') { <span class="status done">Ordonnance validée</span><button type="button" class="text-action" (click)="downloadPrescription()">Télécharger le PDF signé</button> }
              </div>
              @if (createdPrescription()) { <p class="muted">Ordonnance {{ createdPrescription()?.id }} · {{ createdPrescription()?.status === 'VALIDATED' ? 'validée' : 'brouillon' }}</p> }
            </article>
          }
        </section>

        <section id="notifications" class="patient-section" [hidden]="activePage() !== 'notifications'">
          <div class="section-heading"><div><span class="eyebrow">CENTRE DE NOTIFICATIONS</span><h2>Notifications</h2></div></div>
          @if (notifications().length === 0) { <div class="empty small"><p>Aucune notification pour le moment.</p></div> }
          @for (notification of notifications(); track notification.id) {
            <article class="notification-row" [class.unread]="!notification.readAt">
              <div><strong>{{ notification.message }}</strong><small>{{ notification.createdAt | date:'d MMM y · HH:mm':'Europe/Paris':'fr' }}</small></div>
              @if (notification.appointmentId) { <button class="text-action" type="button" (click)="openConsultation(notification.appointmentId, patientIdForAppointment(notification.appointmentId))">Ouvrir le rendez-vous</button> }
              @if (!notification.readAt) { <button class="text-action" type="button" (click)="markNotificationRead(notification)">Marquer comme lue</button> }
            </article>
          }
        </section>

        <section id="profil" class="patient-section" [hidden]="activePage() !== 'profil'">
          <div class="section-heading"><div><span class="eyebrow">INFORMATIONS PROFESSIONNELLES</span><h2>Mon profil</h2></div></div>
          @if (profile()) {
            <form class="profile-form" (ngSubmit)="saveProfile()">
              <label>Prénom<input name="profileFirstName" maxlength="80" [(ngModel)]="profileForm.firstName"></label>
              <label>Nom<input name="profileLastName" maxlength="80" [(ngModel)]="profileForm.lastName"></label>
              <label>E-mail professionnel<input [value]="profile()?.email" disabled></label>
              <label>Spécialité<input [value]="profile()?.specialty" disabled></label>
              <label>Photo (URL)<input name="profilePhoto" type="url" maxlength="2048" [(ngModel)]="profileForm.profilePhotoUrl"></label>
              <label>Téléphone professionnel<input name="profilePhone" type="tel" maxlength="40" [(ngModel)]="profileForm.professionalPhone"></label>
              <label>Adresse du cabinet<input name="profileAddress" maxlength="500" [(ngModel)]="profileForm.officeAddress"></label>
              <label>Numéro professionnel<input name="professionalNumber" maxlength="40" [(ngModel)]="profileForm.professionalNumber"></label>
              <label class="checkbox-label"><input name="emailNotifications" type="checkbox" [(ngModel)]="profileForm.emailNotificationsEnabled"> Recevoir les notifications par e-mail</label>
              <div class="profile-status">Compte : <strong>{{ profile()?.verificationStatus }}</strong> · Numéro professionnel : <strong>{{ profile()?.professionalNumberVerified ? 'vérifié' : 'non vérifié' }}</strong></div>
              <button type="submit" [disabled]="savingProfile()">Enregistrer le profil</button>
            </form>
          } @else { <div class="empty small"><p>Profil en cours de chargement…</p></div> }
        </section>
        <footer>© ClairSanté · Prenez soin de vous et de vos patients.</footer>
    </main>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f5f8f7; color:#18343d; }
    .content { width:min(100%,1250px); margin:0 auto; padding:0 38px 28px; }.topbar { display:flex; height:72px; align-items:center; justify-content:space-between; border-bottom:1px solid #e7edeb; }.breadcrumb { color:#839195; font-size:12px; }.breadcrumb span { margin:0 8px; color:#c1ccca; }.user-pill { display:flex; align-items:center; gap:9px; color:#52676d; font-size:12px; }.avatar { display:grid; width:31px; height:31px; place-items:center; border-radius:50%; background:#e1f0eb; color:#13766d; font-size:10px; font-weight:750; }
    .welcome { display:flex; align-items:end; justify-content:space-between; gap:20px; margin:34px 0 25px; }.eyebrow { color:#77938e; font-size:9px; font-weight:750; letter-spacing:1.3px; }h1 { margin:9px 0 6px; color:#183b46; font-size:30px; letter-spacing:-.8px; }h1 span { color:#edbd57; font-size:20px; }.welcome p { margin:0; color:#7b8b8f; font-size:13px; }.today-button { display:flex; align-items:center; gap:7px; border:1px solid #dce9e5; border-radius:10px; padding:10px 13px; background:#fff; color:#168176; font:inherit; font-size:11px; font-weight:700; cursor:pointer; }.today-button .material-icons { font-size:17px; }
    .summary-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:15px; }.summary-card { min-height:125px; border:1px solid #e8eeec; border-radius:17px; padding:16px; background:white; }.summary-icon { display:grid; width:34px; height:34px; place-items:center; border-radius:11px; }.summary-icon .material-icons { font-size:18px; }.mint { background:#e6f5ef; color:#11816f; }.blue { background:#eaf1fc; color:#5279ba; }.lilac { background:#f1ecfb; color:#856bb5; }.summary-card p { margin:13px 0 3px; color:#73858a; font-size:11px; }.summary-card strong { margin-right:6px; color:#183d47; font-size:24px; }.summary-card small { color:#98a5a7; font-size:10px; }
    .agenda-section,.patient-section { margin-top:30px; }.section-heading { display:flex; align-items:end; justify-content:space-between; gap:10px; margin-bottom:13px; }h2 { margin:6px 0 0; color:#23434c; font-size:18px; }.week-nav { display:flex; align-items:center; gap:9px; }.week-nav button { width:30px; height:30px; border:1px solid #e1eae7; border-radius:9px; background:white; color:#54736e; font-size:20px; cursor:pointer; }.week-nav strong { min-width:135px; color:#526b70; font-size:10px; text-align:center; }
    .calendar { border:1px solid #e6edeb; border-radius:17px; overflow:hidden; background:white; }.week-grid { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); gap:7px; padding:13px; border-bottom:1px solid #edf1ef; }.day { position:relative; display:grid; justify-items:center; gap:6px; min-height:67px; border:1px solid transparent; border-radius:11px; background:transparent; color:#738589; cursor:pointer; }.day>span { font-size:10px; text-transform:capitalize; }.day strong { color:#34525a; font-size:16px; }.day.selected { border-color:#cde9df; background:#eaf6f1; }.day.selected strong { color:#087e75; }.day.is-today strong { color:#087e75; }.day i { position:absolute; bottom:4px; width:4px; height:4px; border-radius:50%; background:#20a187; }
    .day-heading { display:flex; align-items:center; gap:7px; padding:17px 19px 12px; color:#4b666b; font-size:12px; font-weight:700; text-transform:capitalize; }.day-heading .material-icons { color:#128578; font-size:17px; }
    .schedule { padding:3px 19px 18px; }.schedule-item { display:grid; grid-template-columns:55px 13px minmax(0,1fr); gap:10px; min-height:80px; }.time { padding-top:15px; color:#35545c; font-size:13px; font-weight:700; }.time small { display:block; margin-top:5px; color:#9aa7a8; font-size:9px; font-weight:500; }.timeline { position:relative; display:flex; justify-content:center; }.timeline::before { position:absolute; top:0; bottom:0; width:1px; background:#e5eeeb; content:''; }.timeline span { z-index:1; width:8px; height:8px; margin-top:19px; border:2px solid white; border-radius:50%; background:#1c9b82; box-shadow:0 0 0 1px #b8ddd2; }
    .visit-card { display:flex; align-items:center; justify-content:space-between; gap:12px; min-height:66px; margin:5px 0 9px; border:1px solid #e6eeeb; border-radius:12px; padding:10px; background:#fcfefd; }.visit-main { display:flex; align-items:center; gap:10px; }.patient-avatar { display:grid; width:34px; height:34px; flex:0 0 auto; place-items:center; border-radius:50%; background:#eaf1fb; color:#5879ae; font-size:10px; font-weight:750; }.visit-main h3 { margin:0 0 4px; color:#34535a; font-size:11px; }.visit-main p { margin:0; color:#879598; font-size:9px; }.visit-actions { display:flex; flex-wrap:wrap; justify-content:flex-end; gap:6px; }.visit-actions button,.video { display:inline-flex; align-items:center; gap:4px; border:0; border-radius:8px; padding:8px 9px; font:inherit; font-size:9px; font-weight:700; text-decoration:none; cursor:pointer; }.confirm { background:#fff2d9; color:#96712d; }.complete { background:#e9f5ef; color:#28795e; }.video { background:#e7f3ef; color:#087e75; }.video .material-icons { font-size:14px; }
    .empty { display:grid; min-height:155px; justify-items:center; align-content:center; color:#869497; text-align:center; }.empty p { font-size:11px; }.empty h3 { margin:8px 0 0; color:#4a676c; font-size:13px; }.empty-icon { color:#55a08f; font-size:27px; }.loader { width:18px; height:18px; border:2px solid #d5e9e3; border-top-color:#087e75; border-radius:50%; animation:spin .7s linear infinite; }.notice { border-radius:10px; padding:12px; background:#fff1ee; color:#9a463e; font-size:12px; }
    .availability-section { margin-top:28px; }.availability-form { display:grid; grid-template-columns:repeat(auto-fit,minmax(120px,1fr)); gap:9px; margin:15px 0; }.availability-form label { display:grid; }.availability-list { display:grid; gap:7px; }.availability-item { display:flex; gap:12px; padding:10px; }
    .count { color:#89989a; font-size:10px; }.patient-table { display:grid; gap:7px; }.patient-row { display:flex; align-items:center; gap:11px; border:1px solid #e7eeec; border-radius:12px; padding:11px 13px; background:white; }.patient-data { display:grid; flex:1; gap:4px; }.patient-data strong { color:#38575e; font-size:11px; }.patient-data small { color:#88979a; font-size:9px; }.status { border-radius:20px; padding:6px 9px; background:#fff3dc; color:#a37a30; font-size:9px; font-weight:700; }.status.done { background:#e5f5ed; color:#287d5c; }.empty.small { min-height:75px; border:1px dashed #d9e5e2; border-radius:12px; background:white; }.empty.small p { margin:0; }footer { margin-top:35px; border-top:1px solid #e7edeb; padding:17px 0; color:#99a4a5; font-size:10px; }
    .overview-panels { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; margin-top:17px; }.overview-panel,.detail-panel { border:1px solid #e6edeb; border-radius:15px; padding:16px; background:white; }.activity-row { display:flex; align-items:center; justify-content:space-between; gap:10px; border-bottom:1px solid #edf2f0; padding:10px 2px; color:#52696d; font-size:10px; }.activity-row:last-child { border-bottom:0; }.activity-time { color:#148174; font-weight:700; white-space:nowrap; }.activity-row button { text-align:left; }.agenda-controls { display:flex; align-items:center; gap:9px; }.view-switch { display:flex; padding:3px; border:1px solid #e1eae7; border-radius:9px; background:white; }.view-switch button { border:0; border-radius:7px; padding:7px 9px; background:transparent; color:#60777a; font:inherit; font-size:10px; cursor:pointer; }.view-switch button.chosen { background:#eaf5f1; color:#087e75; font-weight:700; }.agenda-controls .today-button { white-space:nowrap; }.status-filter { display:flex; align-items:center; gap:8px; margin:0 0 10px auto; color:#718287; font-size:10px; }.month-grid { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); gap:1px; padding:8px; background:#f0f4f2; }.month-day { display:flex; min-height:74px; flex-direction:column; align-items:flex-start; gap:7px; border:0; padding:8px; background:#fff; color:#34525a; text-align:left; cursor:pointer; }.month-day small { color:#148174; font-size:9px; }.month-day.outside-month { color:#bdc7c5; }.month-day.selected { outline:2px solid #148174; outline-offset:-2px; }.availability-calendar { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); gap:7px; margin-top:10px; }.availability-day { display:grid; align-content:start; gap:6px; min-height:68px; border-radius:10px; padding:9px; background:#fff; box-shadow:0 0 0 1px #e7eeec; color:#52696d; font-size:9px; }.availability-day strong { color:#168176; }.availability-day small { color:#9aa7a8; }.availability-form label,.profile-form label,.consultation-form label,.detail-panel label { display:grid; gap:6px; color:#64797d; font-size:10px; }.availability-form input,.availability-form select,.profile-form input,.consultation-form textarea,.detail-panel textarea,.patient-search input,.status-filter select { min-width:0; border:1px solid #dce7e4; border-radius:8px; padding:9px; background:#fff; color:#29484f; font:inherit; font-size:11px; }.availability-form button,.profile-form button,.patient-search button,.form-actions button,.pagination button { align-self:end; border:0; border-radius:8px; padding:10px 12px; background:#087e75; color:white; font:inherit; font-size:10px; font-weight:700; cursor:pointer; }.availability-form button:disabled,.profile-form button:disabled,.form-actions button:disabled,.pagination button:disabled { opacity:.55; cursor:not-allowed; }.availability-item { align-items:center; flex-wrap:wrap; border:1px solid #e7eeec; border-radius:10px; background:white; color:#52696d; font-size:10px; }.text-action,.secondary-action,.close-detail { border:0; padding:5px; background:transparent; color:#087e75; font:inherit; font-size:10px; cursor:pointer; }.text-action.danger { color:#a24b43; }.exception-heading { margin-top:27px; }.exception-list { margin-top:10px; }.patient-search { display:flex; gap:8px; margin-bottom:12px; }.patient-search input { flex:1; }.patient-row-button { width:100%; text-align:left; cursor:pointer; }.pagination,.form-actions { display:flex; align-items:center; justify-content:flex-end; gap:9px; margin-top:13px; color:#718287; font-size:10px; }.detail-panel { position:relative; margin-top:12px; }.detail-panel h3 { margin:9px 0; color:#23434c; }.detail-panel h4 { margin:17px 0 7px; color:#426168; font-size:11px; }.close-detail { position:absolute; top:13px; right:13px; }.medical-record { white-space:pre-wrap; color:#52696d; font-size:11px; line-height:1.7; }.consultation-form { display:grid; gap:11px; }.consultation-form textarea,.detail-panel textarea { width:100%; resize:vertical; box-sizing:border-box; }.form-actions { justify-content:flex-start; }.notification-row { display:flex; align-items:center; justify-content:space-between; gap:14px; border:1px solid #e7eeec; border-radius:11px; margin-bottom:8px; padding:12px; background:white; }.notification-row.unread { border-left:3px solid #148174; }.notification-row>div { display:grid; gap:5px; color:#49636a; font-size:11px; }.notification-row small,.muted { color:#849397; font-size:9px; }.profile-form { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; border:1px solid #e6edeb; border-radius:15px; padding:17px; background:#fff; }.profile-form .checkbox-label { display:flex; align-items:center; gap:8px; }.profile-status { align-self:center; color:#718287; font-size:10px; }.profile-status strong { color:#168176; }
    @keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:900px) { .content { padding:0 20px 25px; } }
    @media(max-width:680px) { .content { padding:0 13px 20px; }.topbar { height:55px; }.user-pill { font-size:0; }.welcome { align-items:flex-start; flex-direction:column; margin:23px 0 17px; }h1 { font-size:25px; }.summary-grid { grid-template-columns:1fr; gap:8px; }.summary-card { display:grid; min-height:72px; grid-template-columns:40px 1fr auto; align-items:center; column-gap:9px; }.summary-icon { grid-row:span 2; }.summary-card p { margin:0; }.summary-card strong { grid-column:3; grid-row:1; }.summary-card small { grid-column:2/4; }.section-heading { align-items:flex-start; flex-direction:column; }.agenda-controls { width:100%; flex-wrap:wrap; }.week-nav { width:100%; justify-content:space-between; }.week-grid { gap:1px; padding:7px; }.day { min-height:58px; }.day>span { font-size:8px; }.visit-card { align-items:flex-start; flex-direction:column; }.visit-actions { width:100%; justify-content:flex-start; }.schedule { padding:3px 8px 14px; }.schedule-item { grid-template-columns:43px 8px 1fr; gap:6px; }.time { font-size:11px; }.patient-row { padding:9px; }.status { padding:5px 7px; }.overview-panels,.profile-form { grid-template-columns:1fr; }.month-day { min-height:50px; padding:5px; }.availability-calendar { grid-template-columns:repeat(2,minmax(0,1fr)); }.activity-row,.notification-row { align-items:flex-start; flex-wrap:wrap; }.availability-item { gap:7px; }.profile-status { line-height:1.6; } }
  `]
})
export class DoctorDashboardComponent implements OnInit {
  readonly appointments = signal<Appointment[]>([]);
  readonly activePage = signal<DoctorPage>('dashboard');
  readonly availabilities = signal<DoctorAvailability[]>([]);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly savingAvailability = signal(false);
  readonly savingReschedule = signal(false);
  readonly reschedulingAppointmentId = signal('');
  readonly stats = signal<DoctorDashboardStats | null>(null);
  readonly loadingStats = signal(true);
  readonly agendaView = signal<'day' | 'week' | 'month'>('week');
  statusFilter = 'ALL';
  readonly availabilityExceptions = signal<AvailabilityException[]>([]);
  readonly savingException = signal(false);
  readonly notifications = signal<DoctorNotification[]>([]);
  readonly patientPage = signal<PageResponse<DoctorPatient> | null>(null);
  readonly loadingPatients = signal(true);
  readonly patientPageNumber = signal(0);
  readonly selectedPatient = signal<DoctorPatientDetail | null>(null);
  readonly selectedAppointmentId = signal('');
  readonly selectedPatientId = signal('');
  readonly consultation = signal<DoctorConsultation | null>(null);
  readonly consultationFinalized = signal(false);
  readonly savingConsultation = signal(false);
  readonly createdPrescription = signal<Prescription | null>(null);
  readonly prescriptionHistory = signal<PrescriptionRevision[]>([]);
  readonly selectedAppointmentStatus = signal<Appointment['status'] | null>(null);
  readonly profile = signal<DoctorProfessionalProfile | null>(null);
  readonly savingProfile = signal(false);
  readonly profileForm = {
    firstName: '',
    lastName: '',
    profilePhotoUrl: '',
    professionalPhone: '',
    officeAddress: '',
    professionalNumber: '',
    emailNotificationsEnabled: true
  };
  patientSearch = '';
  consultationReport = '';
  consultationObservations = '';
  consultationDiagnosis = '';
  prescriptionContent = '';
  exceptionDate = '';
  exceptionStart = '';
  exceptionEnd = '';
  exceptionReason = '';
  editingAvailabilityId = '';
  rescheduleDateTime = '';
  readonly weekdays: Array<{ value: DayOfWeek; label: string }> = [
    { value: 'MONDAY', label: 'Lundi' },
    { value: 'TUESDAY', label: 'Mardi' },
    { value: 'WEDNESDAY', label: 'Mercredi' },
    { value: 'THURSDAY', label: 'Jeudi' },
    { value: 'FRIDAY', label: 'Vendredi' },
    { value: 'SATURDAY', label: 'Samedi' },
    { value: 'SUNDAY', label: 'Dimanche' }
  ];
  availabilityDay: DayOfWeek = 'MONDAY';
  availabilityStart = '09:00';
  availabilityEnd = '17:00';
  slotDurationMinutes = 30;
  readonly today = this.appointmentToday(new Date());
  readonly todayKey = this.toKey(this.today);
  readonly selectedDate = signal(this.startOfDay(new Date()));
  readonly selectedDateKey = signal(this.todayKey);
  weekStart = this.getMonday(this.today);
  weekEnd = this.addDays(this.weekStart, 6);
  weekDays = this.buildWeek();
  constructor(
    private readonly api: TelemedApiService,
    private readonly auth: AuthService,
    private readonly route: ActivatedRoute,
    private readonly router: Router
  ) {}

  get email(): string { return this.auth.claims()?.email ?? ''; }
  get initials(): string { return this.email.slice(0, 1).toUpperCase(); }
  get pageTitle(): string { return doctorPageTitles[this.activePage()]; }
  get periodLabel(): string {
    if (this.agendaView() === 'day') {
      return this.dateOnly(this.selectedDateKey()).toLocaleDateString('fr-FR', { timeZone: 'UTC', dateStyle: 'medium' });
    }
    if (this.agendaView() === 'month') {
      return this.dateOnly(this.selectedDateKey()).toLocaleDateString('fr-FR', { timeZone: 'UTC', month: 'long', year: 'numeric' });
    }
    return `${this.dateOnly(this.toKey(this.weekStart)).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', timeZone: 'UTC' })} — ${this.dateOnly(this.toKey(this.weekEnd)).toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' })}`;
  }
  get monthDays(): Array<{ date: Date; key: string }> {
    const firstDay = new Date(this.selectedDate().getFullYear(), this.selectedDate().getMonth(), 1);
    const start = this.getMonday(firstDay);
    return Array.from({ length: 42 }, (_, index) => {
      const date = this.addDays(start, index);
      return { date, key: this.toKey(date) };
    });
  }

  ngOnInit(): void {
    this.route.data.subscribe(data => {
      const page = data['doctorPage'];
      this.activePage.set(isDoctorPage(page) ? page : 'dashboard');
    });
    this.route.queryParamMap.subscribe(params => {
      const appointmentId = params.get('appointmentId');
      if (appointmentId
          && (this.activePage() === 'consultations' || this.activePage() === 'ordonnances')) {
        this.loadConsultation(appointmentId, params.get('patientId') ?? '');
      }
    });
    this.api.doctorAppointments().subscribe({
      next: appointments => {
        this.appointments.set(appointments);
        this.loading.set(false);
        const selectedAppointment = appointments.find(item => item.id === this.selectedAppointmentId());
        if (selectedAppointment) this.selectedAppointmentStatus.set(selectedAppointment.status);
      },
      error: () => { this.loading.set(false); this.error.set('Impossible de charger votre agenda pour le moment.'); }
    });
    this.api.doctorAvailabilities().subscribe({
      next: availabilities => this.availabilities.set(availabilities),
      error: () => this.error.set('Impossible de charger vos disponibilités.')
    });
    this.refreshStats();
    this.loadPatients();
    this.api.doctorProfile().subscribe({
      next: profile => {
        this.profile.set(profile);
        this.profileForm.firstName = profile.firstName ?? '';
        this.profileForm.lastName = profile.lastName ?? '';
        this.profileForm.profilePhotoUrl = profile.profilePhotoUrl ?? '';
        this.profileForm.professionalPhone = profile.professionalPhone ?? '';
        this.profileForm.officeAddress = profile.officeAddress ?? '';
        this.profileForm.professionalNumber = profile.professionalNumber ?? '';
        this.profileForm.emailNotificationsEnabled = profile.emailNotificationsEnabled;
      },
      error: () => this.error.set('Impossible de charger votre profil professionnel.')
    });
    this.loadNotifications();
    const from = this.todayKey;
    const toDate = this.addDays(this.today, 365);
    this.api.doctorAvailabilityExceptions(from, this.toKey(toDate)).subscribe({
      next: exceptions => this.availabilityExceptions.set(exceptions),
      error: () => this.error.set('Impossible de charger vos congés et indisponibilités.')
    });
    this.exceptionDate = from;
  }

  appointmentsOn(dayKey: string): Appointment[] {
    return this.appointments()
      .filter(item => item.scheduledAt.slice(0, 10) === dayKey)
      .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
  }

  filteredAppointmentsOn(dayKey: string): Appointment[] {
    const appointments = this.appointmentsOn(dayKey);
    return this.statusFilter === 'ALL'
      ? appointments
      : appointments.filter(appointment => appointment.status === this.statusFilter);
  }

  selectDate(date: Date): void {
    this.selectedDate.set(date);
    this.selectedDateKey.set(this.toKey(date));
    const monday = this.getMonday(date);
    this.weekStart = monday;
    this.weekEnd = this.addDays(monday, 6);
    this.weekDays = this.buildWeek();
  }

  changeWeek(days: number): void {
    this.selectDate(this.addDays(this.selectedDate(), days));
  }

  setAgendaView(view: 'day' | 'week' | 'month'): void { this.agendaView.set(view); }

  changePeriod(direction: -1 | 1): void {
    const date = new Date(this.selectedDate());
    if (this.agendaView() === 'day') date.setDate(date.getDate() + direction);
    else if (this.agendaView() === 'week') date.setDate(date.getDate() + direction * 7);
    else {
      date.setDate(1);
      date.setMonth(date.getMonth() + direction);
    }
    this.selectDate(date);
  }

  patientReference(id: string): string { return id.slice(0, 8).toUpperCase(); }
  patientInitials(id: string): string { return id.replace(/-/g, '').slice(0, 2).toUpperCase(); }
  patientLabel(appointment: Appointment): string {
    return this.stats()?.nextAppointments.find(item => item.id === appointment.id)?.patientEmail
      ?? this.stats()?.recentActivities.find(item => item.id === appointment.id)?.patientEmail
      ?? `Patient · ${this.patientReference(appointment.patientId)}`;
  }
  patientLabelById(patientId: string): string {
    return this.selectedPatient()?.email || `Patient · ${this.patientReference(patientId)}`;
  }
  patientIdForAppointment(appointmentId: string): string {
    return this.appointments().find(item => item.id === appointmentId)?.patientId
      ?? this.stats()?.nextAppointments.find(item => item.id === appointmentId)?.patientId
      ?? this.stats()?.recentActivities.find(item => item.id === appointmentId)?.patientId
      ?? '';
  }
  canPrescribe(): boolean { return this.selectedAppointmentStatus() === 'DONE'; }
  isAppointmentStarted(scheduledAt: string): boolean {
    return this.appointmentDate(scheduledAt).getTime() <= this.appointmentLocalNowAsUtc();
  }
  selectedAppointmentStarted(): boolean {
    const appointment = this.appointments().find(item => item.id === this.selectedAppointmentId());
    const scheduledAt = appointment?.scheduledAt
      ?? this.stats()?.nextAppointments.find(item => item.id === this.selectedAppointmentId())?.scheduledAt
      ?? this.stats()?.recentActivities.find(item => item.id === this.selectedAppointmentId())?.scheduledAt;
    return scheduledAt ? this.isAppointmentStarted(scheduledAt) : false;
  }

  statusLabel(status: Appointment['status']): string {
    return status === 'CONFIRMED' ? 'Confirmé' : status === 'PENDING' ? 'En attente'
      : status === 'DONE' ? 'Terminé' : 'Annulé';
  }

  setStatus(appointment: Appointment, status: 'CONFIRMED' | 'DONE' | 'CANCELLED'): void {
    this.api.updateAppointmentStatus(appointment.id, status).subscribe({
      next: updated => {
        this.appointments.update(items => items.map(item => item.id === updated.id ? updated : item));
        this.refreshStats();
        this.loadNotifications();
      },
      error: () => this.error.set('La mise à jour du rendez-vous a échoué.')
    });
  }

  cancelAppointment(appointment: Appointment): void {
    if (!window.confirm('Confirmer l’annulation de ce rendez-vous ?')) return;
    this.setStatus(appointment, 'CANCELLED');
  }

  startReschedule(appointment: Appointment): void {
    this.reschedulingAppointmentId.set(appointment.id);
    this.rescheduleDateTime = appointment.scheduledAt.slice(0, 16);
  }

  saveReschedule(appointment: Appointment): void {
    if (!this.rescheduleDateTime) return;
    this.savingReschedule.set(true);
    this.api.rescheduleDoctorAppointment(appointment.id, this.rescheduleDateTime).subscribe({
      next: updated => {
        this.appointments.update(items => items.map(item => item.id === updated.id ? updated : item));
        this.reschedulingAppointmentId.set('');
        this.savingReschedule.set(false);
        this.refreshStats();
        this.loadNotifications();
      },
      error: error => {
        this.error.set(error.status === 409
          ? 'Le nouvel horaire chevauche un rendez-vous, une indisponibilité ou une plage non ouverte.'
          : error.status === 400
            ? 'Choisissez un horaire futur différent de l’horaire actuel.'
            : 'Impossible de replanifier ce rendez-vous.');
        this.savingReschedule.set(false);
      }
    });
  }

  saveAvailability(): void {
    this.error.set('');
    this.savingAvailability.set(true);
    const payload = {
      dayOfWeek: this.availabilityDay,
      startTime: this.availabilityStart,
      endTime: this.availabilityEnd,
      slotDurationMinutes: this.slotDurationMinutes
    };
    const save = this.editingAvailabilityId
      ? this.api.updateDoctorAvailability(this.editingAvailabilityId, payload)
      : this.api.createDoctorAvailability(payload);
    save.subscribe({
      next: availability => {
        this.availabilities.update(items => this.sortAvailabilities(
          items.some(item => item.id === availability.id)
            ? items.map(item => item.id === availability.id ? availability : item)
            : [...items, availability]));
        this.savingAvailability.set(false);
        this.cancelAvailabilityEdit();
      },
      error: error => {
        this.error.set(error.status === 409
          ? 'Cette plage chevauche une disponibilité existante ou affecterait des rendez-vous déjà réservés.'
          : 'Impossible d’enregistrer cette disponibilité. Vérifiez les horaires saisis.');
        this.savingAvailability.set(false);
      }
    });
  }

  editAvailability(availability: DoctorAvailability): void {
    this.editingAvailabilityId = availability.id;
    this.availabilityDay = availability.dayOfWeek;
    this.availabilityStart = availability.startTime.slice(0, 5);
    this.availabilityEnd = availability.endTime.slice(0, 5);
    this.slotDurationMinutes = availability.slotDurationMinutes;
    document.getElementById('disponibilites')?.scrollIntoView({ behavior: 'smooth' });
  }

  cancelAvailabilityEdit(): void {
    this.editingAvailabilityId = '';
    this.availabilityDay = 'MONDAY';
    this.availabilityStart = '09:00';
    this.availabilityEnd = '17:00';
    this.slotDurationMinutes = 30;
  }

  deleteAvailability(availability: DoctorAvailability): void {
    if (!window.confirm('Supprimer cette plage horaire ? Les rendez-vous réservés ne seront pas supprimés.')) return;
    this.api.deleteDoctorAvailability(availability.id).subscribe({
      next: () => this.availabilities.update(items => items.filter(item => item.id !== availability.id)),
      error: error => this.error.set(error.status === 409
        ? 'Cette plage contient des rendez-vous. Replanifiez-les avant de la supprimer.'
        : 'Impossible de supprimer cette plage.')
    });
  }

  saveAvailabilityException(): void {
    if (!this.exceptionDate || Boolean(this.exceptionStart) !== Boolean(this.exceptionEnd)
        || this.exceptionStart && this.exceptionEnd <= this.exceptionStart) {
      this.error.set('Vérifiez la date et les heures de cette indisponibilité.');
      return;
    }
    this.savingException.set(true);
    this.api.createDoctorAvailabilityException({
      date: this.exceptionDate,
      startTime: this.exceptionStart || null,
      endTime: this.exceptionEnd || null,
      reason: this.exceptionReason.trim() || null
    }).subscribe({
      next: exception => {
        this.availabilityExceptions.update(items => [...items, exception]
          .sort((left, right) => left.date.localeCompare(right.date)
            || (left.startTime ?? '').localeCompare(right.startTime ?? '')));
        this.savingException.set(false);
        this.exceptionStart = '';
        this.exceptionEnd = '';
        this.exceptionReason = '';
      },
      error: error => {
        this.error.set(error.status === 409
          ? 'Cette période chevauche un rendez-vous ou une indisponibilité existante.'
          : 'Impossible d’enregistrer cette indisponibilité.');
        this.savingException.set(false);
      }
    });
  }

  deleteAvailabilityException(exception: AvailabilityException): void {
    if (!window.confirm('Supprimer cette indisponibilité ?')) return;
    this.api.deleteDoctorAvailabilityException(exception.id).subscribe({
      next: () => this.availabilityExceptions.update(items => items.filter(item => item.id !== exception.id)),
      error: () => this.error.set('Impossible de supprimer cette indisponibilité.')
    });
  }

  loadPatients(): void {
    this.loadingPatients.set(true);
    this.api.doctorPatients(this.patientSearch.trim(), this.patientPageNumber(), 20).subscribe({
      next: page => { this.patientPage.set(page); this.loadingPatients.set(false); },
      error: () => { this.error.set('Impossible de charger les patients autorisés.'); this.loadingPatients.set(false); }
    });
  }

  searchPatients(): void {
    this.patientPageNumber.set(0);
    this.loadPatients();
  }

  changePatientPage(direction: -1 | 1): void {
    this.patientPageNumber.update(page => page + direction);
    this.loadPatients();
  }

  openPatient(patientId: string): void {
    this.api.doctorPatient(patientId).subscribe({
      next: patient => this.selectedPatient.set(patient),
      error: () => this.error.set('Accès au dossier refusé ou patient introuvable.')
    });
  }

  openConsultation(appointmentId: string, patientId: string): void {
    const appointment = this.appointments().find(item => item.id === appointmentId);
    if (appointment && appointment.status !== 'CONFIRMED' && appointment.status !== 'DONE') {
      this.error.set('Le dossier et le compte rendu ne sont accessibles que pour un rendez-vous confirmé ou terminé.');
      return;
    }
    if (!patientId) patientId = this.patientIdForAppointment(appointmentId);
    void this.router.navigate(['/medecin/consultations'], {
      queryParams: { appointmentId, patientId: patientId || null }
    });
  }

  private loadConsultation(appointmentId: string, patientId: string): void {
    if (!patientId) patientId = this.patientIdForAppointment(appointmentId);
    const appointment = this.appointments().find(item => item.id === appointmentId);
    this.selectedAppointmentId.set(appointmentId);
    this.selectedPatientId.set(patientId);
    this.selectedAppointmentStatus.set(appointment?.status
      ?? this.stats()?.nextAppointments.find(item => item.id === appointmentId)?.status
      ?? this.stats()?.recentActivities.find(item => item.id === appointmentId)?.status
      ?? null);
    this.consultation.set(null);
    this.consultationFinalized.set(false);
    this.createdPrescription.set(null);
    this.prescriptionContent = '';
    this.prescriptionHistory.set([]);
    this.consultationReport = '';
    this.consultationObservations = '';
    this.consultationDiagnosis = '';
    if (patientId) this.openPatient(patientId);
    this.api.doctorConsultation(appointmentId).subscribe({
      next: consultation => {
        this.consultation.set(consultation);
        this.consultationReport = consultation.report ?? '';
        this.consultationObservations = consultation.observations ?? '';
        this.consultationDiagnosis = consultation.diagnosis ?? '';
        this.consultationFinalized.set(consultation.finalized);
      },
      error: error => {
        if (error.status !== 404) this.error.set('Impossible de charger le compte rendu de cette consultation.');
      }
    });
    this.api.doctorPrescription(appointmentId).subscribe({
      next: prescription => {
        this.createdPrescription.set(prescription);
        this.prescriptionContent = prescription.content;
        this.api.doctorPrescriptionHistory(prescription.id).subscribe({
          next: history => this.prescriptionHistory.set(history),
          error: () => this.error.set('Impossible de charger l’historique de l’ordonnance.')
        });
      },
      error: error => {
        if (error.status !== 404) this.error.set('Impossible de charger l’ordonnance associée à cette consultation.');
      }
    });
  }

  closeConsultation(): void {
    this.selectedAppointmentId.set('');
    this.consultation.set(null);
    void this.router.navigate(['/medecin/consultations']);
  }

  saveConsultation(finalized: boolean): void {
    if (finalized && !this.consultationReport.trim()) {
      this.error.set('Un compte rendu est nécessaire pour finaliser la consultation.');
      return;
    }
    const appointmentId = this.selectedAppointmentId();
    this.savingConsultation.set(true);
    this.api.saveDoctorConsultation(appointmentId, {
      report: this.consultationReport.trim() || null,
      observations: this.consultationObservations.trim() || null,
      diagnosis: this.consultationDiagnosis.trim() || null,
      finalized
    }).subscribe({
      next: consultation => {
        this.consultation.set(consultation);
        this.consultationFinalized.set(consultation.finalized);
        this.savingConsultation.set(false);
        if (finalized) {
          this.api.doctorAppointments().subscribe({
            next: appointments => {
              this.appointments.set(appointments);
              this.selectedAppointmentStatus.set(appointments.find(item => item.id === appointmentId)?.status ?? 'DONE');
            },
            error: () => this.error.set('Le compte rendu est finalisé, mais le rafraîchissement de l’agenda a échoué.')
          });
          this.refreshStats();
        }
      },
      error: error => {
        this.error.set(error.status === 409
          ? 'La consultation ne peut pas être enregistrée dans son état actuel.'
          : 'Impossible d’enregistrer le compte rendu.');
        this.savingConsultation.set(false);
      }
    });
  }

  createPrescription(): void {
    if (!this.selectedAppointmentId() || !this.prescriptionContent.trim()) return;
    this.api.createDoctorPrescription(this.selectedAppointmentId(), this.prescriptionContent.trim()).subscribe({
      next: prescription => {
        this.createdPrescription.set(prescription);
        this.loadPrescriptionHistory(prescription.id);
      },
      error: () => this.error.set('Impossible d’enregistrer le brouillon d’ordonnance.')
    });
  }

  updatePrescription(): void {
    const prescription = this.createdPrescription();
    if (!prescription || prescription.status !== 'DRAFT' || !this.prescriptionContent.trim()) return;
    this.api.updateDoctorPrescription(prescription.id, this.prescriptionContent.trim()).subscribe({
      next: updated => {
        this.createdPrescription.set(updated);
        this.loadPrescriptionHistory(updated.id);
      },
      error: () => this.error.set('Impossible de modifier ce brouillon.')
    });
  }

  validatePrescription(): void {
    const prescription = this.createdPrescription();
    if (!prescription) return;
    if (!window.confirm('Valider et signer cette ordonnance ? Cette action est sensible.')) return;
    this.api.validateDoctorPrescription(prescription.id).subscribe({
      next: validated => {
        this.createdPrescription.set(validated);
        this.loadPrescriptionHistory(validated.id);
      },
      error: () => this.error.set('Impossible de valider cette ordonnance.')
    });
  }

  private loadPrescriptionHistory(prescriptionId: string): void {
    this.api.doctorPrescriptionHistory(prescriptionId).subscribe({
      next: history => this.prescriptionHistory.set(history),
      error: () => this.error.set('Impossible de charger l’historique de l’ordonnance.')
    });
  }

  downloadPrescription(): void {
    const prescription = this.createdPrescription();
    if (!prescription || prescription.status !== 'VALIDATED') return;
    this.api.doctorPrescriptionPdf(prescription.id).subscribe({
      next: file => {
        const url = URL.createObjectURL(file);
        const link = document.createElement('a');
        link.href = url;
        link.download = `ordonnance-${prescription.id}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.error.set('Impossible de télécharger l’ordonnance signée.')
    });
  }

  loadNotifications(): void {
    this.api.doctorNotifications().subscribe({
      next: notifications => this.notifications.set(notifications),
      error: () => this.error.set('Impossible de charger vos notifications.')
    });
  }

  markNotificationRead(notification: DoctorNotification): void {
    this.api.markDoctorNotificationRead(notification.id).subscribe({
      next: updated => this.notifications.update(items => items.map(item => item.id === updated.id ? updated : item)),
      error: () => this.error.set('Impossible de marquer cette notification comme lue.')
    });
  }

  saveProfile(): void {
    this.savingProfile.set(true);
    this.api.updateDoctorProfile({
      firstName: this.profileForm.firstName.trim() || null,
      lastName: this.profileForm.lastName.trim() || null,
      profilePhotoUrl: this.profileForm.profilePhotoUrl.trim() || null,
      professionalPhone: this.profileForm.professionalPhone.trim() || null,
      officeAddress: this.profileForm.officeAddress.trim() || null,
      professionalNumber: this.profileForm.professionalNumber.trim() || null,
      emailNotificationsEnabled: this.profileForm.emailNotificationsEnabled
    }).subscribe({
      next: profile => { this.profile.set(profile); this.savingProfile.set(false); },
      error: () => { this.error.set('Impossible d’enregistrer votre profil professionnel.'); this.savingProfile.set(false); }
    });
  }

  private refreshStats(): void {
    this.loadingStats.set(true);
    this.api.doctorDashboardStats().subscribe({
      next: stats => { this.stats.set(stats); this.loadingStats.set(false); },
      error: () => { this.error.set('Impossible de charger les statistiques du tableau de bord.'); this.loadingStats.set(false); }
    });
  }

  private sortAvailabilities(items: DoctorAvailability[]): DoctorAvailability[] {
    return [...items].sort((a, b) =>
      this.weekdays.findIndex(day => day.value === a.dayOfWeek)
        - this.weekdays.findIndex(day => day.value === b.dayOfWeek)
      || a.startTime.localeCompare(b.startTime));
  }

  availabilitiesForDay(day: DayOfWeek): DoctorAvailability[] {
    return this.availabilities().filter(item => item.dayOfWeek === day);
  }

  dayLabel(day: DayOfWeek): string {
    return this.weekdays.find(item => item.value === day)?.label ?? day;
  }

  formatTime(time: string): string {
    return time.slice(0, 5);
  }

  appointmentDate(value: string): Date {
    return new Date(/[zZ]|[+-]\d{2}:\d{2}$/.test(value) ? value : `${value}Z`);
  }

  dateOnly(value: string): Date {
    return new Date(`${value}T12:00:00Z`);
  }

  signOut(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/');
  }

  private buildWeek(): Array<{ date: Date; key: string }> {
    return Array.from({ length: 7 }, (_, index) => {
      const date = this.addDays(this.weekStart, index);
      return { date, key: this.toKey(date) };
    });
  }

  private toKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
  private startOfDay(date: Date): Date { return new Date(date.getFullYear(), date.getMonth(), date.getDate()); }
  private getMonday(date: Date): Date {
    const day = this.startOfDay(date);
    day.setDate(day.getDate() - ((day.getDay() + 6) % 7));
    return day;
  }
  private addDays(date: Date, days: number): Date {
    const result = new Date(date);
    result.setDate(result.getDate() + days);
    return result;
  }

  private appointmentToday(now: Date): Date {
    const [year, month, day] = new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Europe/Paris',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit'
    }).format(now).split('-').map(Number);
    return new Date(year, month - 1, day);
  }

  private appointmentLocalNowAsUtc(): number {
    const parts = new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Europe/Paris',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
      hourCycle: 'h23'
    }).formatToParts(new Date());
    const values = Object.fromEntries(parts.filter(part => part.type !== 'literal')
      .map(part => [part.type, Number(part.value)]));
    return Date.UTC(values['year'], values['month'] - 1, values['day'],
      values['hour'], values['minute'], values['second']);
  }
}
