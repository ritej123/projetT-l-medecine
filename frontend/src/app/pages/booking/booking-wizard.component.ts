import { DatePipe, NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { Appointment, AppointmentSlot, PatientDoctorDetail, TelemedApiService } from '../../core/telemed-api.service';
import { BookingFlowService } from './booking-flow.service';

type BookingStep = 'date' | 'recap' | 'confirmation';

interface CalendarDay {
  date: Date;
  key: string;
  inMonth: boolean;
}

@Component({
  selector: 'app-booking-wizard',
  standalone: true,
  imports: [DatePipe, FormsModule, NgTemplateOutlet, RouterLink],
  template: `
    <div class="page">
      <header class="topbar">
        <a class="brand" routerLink="/medecins"><span class="logo">+</span><span>ClairSanté<small>ESPACE PATIENT</small></span></a>
        <a class="back-link" routerLink="/patient">← Retour à mon espace</a>
      </header>

      <main>
        <section class="intro">
          <span class="eyebrow">PRENEZ RENDEZ-VOUS</span>
          <h1>{{ stepTitle() }}</h1>
          <p>Votre rendez-vous en quelques étapes simples.</p>
        </section>

        <nav class="progress" aria-label="Étapes de réservation">
          <div class="progress-line"><span [style.width.%]="progressWidth()"></span></div>
          @for (label of stepLabels; track label.index) {
            <div class="progress-step" [class.active]="stepIndex() === label.index" [class.complete]="stepIndex() > label.index">
              <span>{{ stepIndex() > label.index ? '✓' : label.index }}</span><small>{{ label.label }}</small>
            </div>
          }
        </nav>

        @if (error()) { <div class="notice" role="alert">{{ error() }}</div> }
        @if (step() === 'recap' && error() && !selectedSlot()) {
          <button class="secondary recovery-action" type="button" (click)="returnToDate()">Choisir un autre créneau</button>
        }

        @if (step() === 'date') {
          @if (loadingDoctor()) {
            <div class="state-card" role="status"><span class="loader"></span>Chargement du médecin…</div>
          } @else if (pageError()) {
            <div class="state-card error" role="alert">{{ pageError() }} <a routerLink="/medecins">Retour à la liste</a></div>
          } @else if (doctor(); as profile) {
            <div class="booking-layout">
              <section class="main-card">
                <h2>1. Choisissez une date et une heure</h2>
                <p class="muted">Les jours en vert disposent d’au moins un créneau libre.</p>
                @if (loadingSlots()) {
                  <div class="inline-state" role="status"><span class="loader"></span>Recherche des créneaux…</div>
                } @else if (slotsError()) {
                  <div class="notice" role="alert">Impossible de charger les disponibilités. Réessayez.</div>
                }
                <div class="month-bar">
                  <button type="button" aria-label="Mois précédent" (click)="changeMonth(-1)">‹</button>
                  <strong>{{ monthDate | date:'MMMM y':'':'fr' }}</strong>
                  <button type="button" aria-label="Mois suivant" (click)="changeMonth(1)">›</button>
                </div>
                <div class="calendar-grid" aria-label="Calendrier des disponibilités">
                  @for (name of weekdays; track name) { <span class="weekday">{{ name }}</span> }
                  @for (day of calendarDays(); track day.key) {
                    @if (day.inMonth) {
                      <button type="button" class="calendar-day" [class.available]="hasAvailability(day.key)"
                              [class.selected]="selectedDateKey() === day.key"
                              [disabled]="loadingSlots() || !hasAvailability(day.key)"
                              (click)="selectDate(day.key)"
                              [attr.aria-label]="day.date | date:'EEEE d MMMM':'':'fr'">
                        {{ day.date | date:'d' }}
                      </button>
                    } @else {
                      <span class="calendar-spacer" aria-hidden="true"></span>
                    }
                  }
                </div>
                @if (!loadingSlots() && !slotsError() && slots().length === 0) {
                  <div class="inline-empty"><span class="material-icons">event_busy</span>Aucun créneau disponible ce mois-ci. Essayez un autre mois.</div>
                }

                <section class="slot-section" aria-live="polite">
                  @if (!selectedDateKey()) {
                    <div class="inline-empty"><span class="material-icons">event_available</span>Sélectionnez un jour en vert pour voir les horaires.</div>
                  } @else if (loadingSlots()) {
                    <div class="inline-state"><span class="loader"></span>Chargement des horaires…</div>
                  } @else if (slotsForSelectedDate().length === 0) {
                    <div class="inline-empty"><span class="material-icons">event_busy</span>Aucun créneau disponible ce jour-là. Choisissez une autre date.</div>
                  } @else {
                    <h3>Créneaux du {{ selectedDate() | date:'EEEE d MMMM':'':'fr' }}</h3>
                    <div class="slots">
                      @for (slot of slotsForSelectedDate(); track slot.startsAt) {
                        <button type="button" class="slot" [class.chosen]="selectedSlot()?.startsAt === slot.startsAt"
                                (click)="selectSlot(slot)">{{ slot.startsAt | date:'HH:mm' }}</button>
                      }
                    </div>
                  }
                </section>
                <div class="step-actions">
                  <a class="secondary" [routerLink]="['/medecins', profile.id]">Retour au profil</a>
                  <button class="primary" type="button" [disabled]="!selectedSlot()" (click)="goToRecap()">
                    Continuer <span class="material-icons">arrow_forward</span>
                  </button>
                </div>
              </section>
              <ng-container *ngTemplateOutlet="selectionSummary"></ng-container>
            </div>
          }
        } @else if (step() === 'recap') {
          @if (loadingDoctor() || validatingSlot()) {
            <div class="state-card" role="status"><span class="loader"></span>Vérification de votre sélection…</div>
          } @else if (pageError()) {
            <div class="state-card error" role="alert">{{ pageError() }}
              <button class="secondary" type="button" (click)="returnToDate()">Choisir un autre créneau</button>
            </div>
          } @else if (doctor(); as profile) {
            <div class="booking-layout">
              <section class="main-card">
                <h2>2. Vérifiez votre rendez-vous</h2>
                <div class="recap-lines">
                  <p><strong>Médecin</strong><span>{{ doctorName(profile) }} · {{ profile.specialty }}</span></p>
                  <p><strong>Date et heure</strong><span>{{ selectedSlot()?.startsAt | date:'EEEE d MMMM y à HH:mm':'':'fr' }}</span></p>
                  <p><strong>Lieu</strong><span>{{ location(profile) }}</span></p>
                </div>
                <label class="reason">
                  Motif de consultation <span>(facultatif)</span>
                  <textarea name="consultationReason" maxlength="500" rows="4" [(ngModel)]="reasonInput"
                            (ngModelChange)="updateReason($event)"
                            placeholder="Indiquez brièvement le motif de votre consultation"></textarea>
                  <small>{{ reasonInput.length }}/500 caractères</small>
                </label>
                <div class="step-actions">
                  <button class="secondary" type="button" (click)="returnToDate()">Retour</button>
                  <button class="primary" type="button" [disabled]="booking() || !selectedSlot()" (click)="confirmBooking()">
                    {{ booking() ? 'Confirmation…' : 'Confirmer le rendez-vous' }}
                    <span class="material-icons">arrow_forward</span>
                  </button>
                </div>
              </section>
              <ng-container *ngTemplateOutlet="selectionSummary"></ng-container>
            </div>
          }
        } @else {
          @if (loadingConfirmation()) {
            <div class="state-card" role="status"><span class="loader"></span>Préparation de la confirmation…</div>
          } @else if (pageError()) {
            <div class="state-card error" role="alert">{{ pageError() }} <a routerLink="/patient">Retour à mon espace</a></div>
          } @else if (appointment(); as savedAppointment) {
            <div class="booking-layout">
              <section class="main-card confirmed">
                <span class="success-icon material-icons">check_circle</span>
                <span class="eyebrow">RENDEZ-VOUS ENREGISTRÉ</span>
                <h2>Votre demande de rendez-vous est enregistrée</h2>
                <div class="recap-lines">
                @if (doctor(); as profile) {
                  <p><strong>Médecin</strong><span>{{ doctorName(profile) }} · {{ profile.specialty }}</span></p>
                  <p><strong>Lieu</strong><span>{{ location(profile) }}</span></p>
                } @else {
                  <p><strong>Médecin</strong><span>Informations du profil indisponibles</span></p>
                }
                  <p><strong>Date et heure</strong><span>{{ appointmentDate(savedAppointment) | date:'EEEE d MMMM y à HH:mm':'':'fr' }}</span></p>
                </div>
                <div class="step-actions confirmation-actions">
                  <button class="secondary" type="button" (click)="downloadCalendar(savedAppointment)">Ajouter au calendrier</button>
                  <a class="primary" routerLink="/patient">Retour à mon espace</a>
                </div>
              </section>
              <ng-container *ngTemplateOutlet="selectionSummary"></ng-container>
            </div>
          }
        }

        <ng-template #selectionSummary>
          <aside class="selection-card" aria-label="Votre sélection">
            <h2><span class="material-icons">event_note</span>Votre sélection</h2>
            @if (doctor(); as profile) {
              <div class="mini-doctor">
                <span class="mini-avatar">{{ initials(profile) }}</span>
                <div><strong>{{ doctorName(profile) }}</strong><small>{{ profile.specialty }}</small></div>
              </div>
              <div class="summary-line"><span class="material-icons">calendar_month</span>
                <span>{{ summaryDate() }}</span>
              </div>
              <div class="summary-line"><span class="material-icons">schedule</span>
                <span>{{ summaryTime() }}</span>
              </div>
              <div class="summary-line"><span class="material-icons">location_on</span>
                <span>{{ location(profile) }}</span>
              </div>
            } @else {
              <p class="muted">Sélection en cours…</p>
            }
          </aside>
        </ng-template>
      </main>
      <footer class="privacy"><span class="material-icons">verified_user</span>Vos informations restent protégées et confidentielles.</footer>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f5f8f7; color:#1d3b44; }
    .page { width:min(1050px,100%); margin:0 auto; padding:0 24px 24px; }
    .topbar { display:flex; min-height:72px; align-items:center; justify-content:space-between; border-bottom:1px solid #e5ece9; }
    .brand { display:flex; align-items:center; gap:10px; color:#183d49; font-weight:800; text-decoration:none; }
    .logo { display:grid; width:37px; height:37px; place-items:center; border-radius:12px; background:#087e75; color:#fff; font-size:26px; }
    .brand small { display:block; margin-top:3px; color:#94a3a4; font-size:9px; letter-spacing:1.2px; }
    .back-link { color:#087e75; font-size:12px; font-weight:700; text-decoration:none; }
    .intro { padding:27px 0 15px; }
    .eyebrow { color:#77938e; font-size:9px; font-weight:800; letter-spacing:1.4px; }
    h1 { margin:7px 0 4px; color:#183b46; font-size:27px; letter-spacing:-.6px; }
    .intro p { margin:0; color:#7b8b8f; font-size:12px; }
    .progress { position:relative; display:flex; justify-content:space-between; margin:4px 0 20px; }
    .progress-line { position:absolute; top:15px; right:12%; left:12%; height:2px; background:#dfe9e5; }
    .progress-line span { display:block; height:100%; background:#168477; transition:width .2s ease; }
    .progress-step { position:relative; z-index:1; display:grid; justify-items:center; gap:5px; width:25%; color:#91a09f; }
    .progress-step>span { display:grid; width:30px; height:30px; place-items:center; border:2px solid #dfe9e5; border-radius:50%; background:#f5f8f7; font-size:11px; font-weight:800; }
    .progress-step small { font-size:10px; }
    .progress-step.active,.progress-step.complete { color:#087e75; }
    .progress-step.active>span,.progress-step.complete>span { border-color:#087e75; background:#087e75; color:#fff; }
    .booking-layout { display:grid; grid-template-columns:minmax(0,1.5fr) minmax(240px,.75fr); align-items:start; gap:14px; }
    .main-card,.selection-card { border:1px solid #e4ece9; border-radius:16px; padding:19px; background:#fff; box-shadow:0 6px 24px #234b4008; }
    .main-card h2 { margin:0 0 6px; color:#2b4b53; font-size:16px; }
    .muted { margin:0; color:#839396; font-size:11px; }
    .month-bar { display:flex; align-items:center; justify-content:space-between; margin:16px 0 10px; }
    .month-bar strong { color:#38565d; font-size:13px; text-transform:capitalize; }
    .month-bar button { width:31px; height:31px; border:1px solid #e5ece9; border-radius:9px; background:#fff; color:#59746e; font-size:20px; cursor:pointer; }
    .calendar-grid { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); justify-items:center; gap:5px 3px; }
    .weekday { padding:5px 0; color:#9aa6a6; font-size:9px; text-transform:capitalize; }
    .calendar-day,.calendar-spacer { display:grid; width:38px; height:38px; place-items:center; }
    .calendar-day { border:1px solid transparent; border-radius:50%; background:transparent; color:#aab4b4; font:inherit; font-size:11px; }
    .calendar-day.available { background:#e9f6f0; color:#24765f; font-weight:700; cursor:pointer; }
    .calendar-day.available:hover { box-shadow:0 0 0 2px #a5d7c6; }
    .calendar-day.selected { border-color:#087e75; background:#087e75; color:#fff; }
    .calendar-day:disabled { cursor:default; opacity:.5; }
    .slot-section { min-height:125px; margin-top:16px; border-top:1px solid #edf2f0; padding-top:14px; }
    .slot-section h3 { margin:0 0 10px; color:#38565d; font-size:12px; text-transform:capitalize; }
    .slots { display:grid; grid-template-columns:repeat(auto-fill,minmax(72px,1fr)); gap:8px; }
    .slot { min-height:38px; border:1px solid #dbe9e3; border-radius:9px; background:#fff; color:#4f7069; font:inherit; font-size:11px; font-weight:700; cursor:pointer; }
    .slot:hover,.slot.chosen { border-color:#168477; background:#e9f6f1; color:#087e75; }
    .inline-empty,.inline-state { display:flex; min-height:78px; align-items:center; justify-content:center; gap:8px; color:#839396; font-size:11px; text-align:center; }
    .inline-empty .material-icons { color:#60988a; font-size:18px; }
    .loader { width:14px; height:14px; border:2px solid #d8eae4; border-top-color:#087e75; border-radius:50%; animation:spin .7s linear infinite; }
    .step-actions { display:flex; align-items:center; justify-content:space-between; gap:9px; margin-top:18px; }
    .primary,.secondary { display:inline-flex; min-height:40px; align-items:center; justify-content:center; gap:7px; border:0; border-radius:10px; padding:0 13px; font:inherit; font-size:11px; font-weight:750; text-decoration:none; cursor:pointer; }
    .primary { background:#087e75; color:#fff; }
    .primary:hover { background:#066d66; }
    .primary .material-icons { font-size:16px; }
    .primary:disabled { opacity:.5; cursor:not-allowed; }
    .secondary { border:1px solid #dce9e4; background:#fff; color:#087e75; }
    .selection-card { position:sticky; top:15px; }
    .selection-card h2 { display:flex; align-items:center; gap:7px; margin:0 0 15px; color:#35545c; font-size:13px; }
    .selection-card h2 .material-icons { color:#168477; font-size:17px; }
    .mini-doctor { display:flex; align-items:center; gap:9px; padding-bottom:12px; border-bottom:1px solid #edf2f0; }
    .mini-avatar { display:grid; width:38px; height:38px; place-items:center; border-radius:12px; background:#e8f4ef; color:#087e75; font-size:12px; font-weight:800; }
    .mini-doctor>div { display:grid; gap:4px; }.mini-doctor strong { color:#38555d; font-size:11px; }.mini-doctor small { color:#168477; font-size:10px; }
    .summary-line { display:flex; align-items:flex-start; gap:8px; margin-top:12px; color:#677d80; font-size:10px; line-height:1.5; }
    .summary-line .material-icons { color:#6f9c91; font-size:15px; }
    .recap-lines { display:grid; gap:0; margin:18px 0; border:1px solid #e7eeeb; border-radius:11px; padding:4px 13px; background:#fbfdfc; }
    .recap-lines p { display:grid; grid-template-columns:112px minmax(0,1fr); gap:8px; margin:0; border-bottom:1px solid #edf2f0; padding:11px 0; font-size:11px; }
    .recap-lines p:last-child { border-bottom:0; }
    .recap-lines strong { color:#48646a; }.recap-lines span { color:#718489; }
    .reason { display:grid; gap:7px; color:#48646a; font-size:11px; font-weight:700; }
    .reason>span { color:#8b999a; font-weight:400; }
    .reason textarea { width:100%; box-sizing:border-box; resize:vertical; border:1px solid #dce7e4; border-radius:9px; padding:10px; color:#29484f; font:inherit; font-size:11px; }
    .reason textarea:focus { border-color:#83c1b2; outline:2px solid #087e7514; }
    .reason small { justify-self:end; color:#94a1a1; font-size:9px; font-weight:400; }
    .notice { margin-bottom:13px; border-radius:10px; padding:11px 13px; background:#fff1ee; color:#9a463e; font-size:11px; }
    .recovery-action { margin-bottom:13px; }
    .state-card { display:flex; min-height:180px; align-items:center; justify-content:center; gap:9px; border:1px solid #e3ebe8; border-radius:15px; background:#fff; color:#74878a; font-size:12px; }
    .state-card.error { flex-direction:column; color:#9a463e; background:#fff7f5; text-align:center; }
    .state-card a { color:#087e75; font-weight:700; }
    .confirmed { display:grid; justify-items:start; }
    .success-icon { margin-bottom:8px; color:#208769; font-size:36px; }
    .confirmed h2 { margin:6px 0 0; color:#21634f; font-size:19px; }
    .confirmation-actions { width:100%; }
    .privacy { display:flex; align-items:center; justify-content:center; gap:5px; margin-top:25px; color:#98a5a5; font-size:9px; }
    .privacy .material-icons { color:#55978a; font-size:13px; }
    @keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:720px) { .page { padding:0 15px 20px; }.topbar { min-height:62px; }.brand { font-size:13px; }.back-link { font-size:10px; }.intro { padding-top:22px; }h1 { font-size:23px; }.booking-layout { grid-template-columns:1fr; }.selection-card { position:static; grid-row:1; }.main-card { grid-row:2; }.selection-card { padding:14px; }.selection-card h2 { margin-bottom:10px; }.summary-line { display:inline-flex; width:calc(50% - 8px); vertical-align:top; }.mini-doctor { padding-bottom:9px; }.main-card { padding:16px; } }
    @media(max-width:420px) { .progress-step small { font-size:9px; }.calendar-day,.calendar-spacer { width:34px; height:34px; }.calendar-grid { gap:4px 1px; }.recap-lines p { grid-template-columns:86px minmax(0,1fr); }.step-actions { align-items:stretch; }.step-actions>* { flex:1; }.confirmation-actions { flex-direction:column; } }
  `]
})
export class BookingWizardComponent implements OnInit {
  readonly step = signal<BookingStep>('date');
  readonly doctor = signal<PatientDoctorDetail | null>(null);
  readonly loadingDoctor = signal(true);
  readonly loadingSlots = signal(false);
  readonly validatingSlot = signal(false);
  readonly loadingConfirmation = signal(true);
  readonly booking = signal(false);
  readonly slotsError = signal(false);
  readonly pageError = signal('');
  readonly error = signal('');
  readonly slots = signal<AppointmentSlot[]>([]);
  readonly selectedDateKey = signal('');
  readonly selectedSlot = signal<AppointmentSlot | null>(null);
  readonly appointment = signal<Appointment | null>(null);
  reasonInput = '';
  readonly stepLabels = [
    { index: 1, label: 'Date et heure' },
    { index: 2, label: 'Récapitulatif' },
    { index: 3, label: 'Confirmation' }
  ];
  readonly weekdays = ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.'];
  monthDate = new Date(new Date().getFullYear(), new Date().getMonth(), 1);
  private doctorId = '';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly router: Router,
    private readonly api: TelemedApiService,
    private readonly flow: BookingFlowService
  ) {}

  ngOnInit(): void {
    this.doctorId = this.route.snapshot.paramMap.get('doctorId') ?? '';
    this.step.set(this.route.snapshot.data['step'] as BookingStep);
    if (!this.doctorId) {
      this.pageError.set('Le médecin sélectionné est introuvable.');
      this.loadingDoctor.set(false);
      this.loadingConfirmation.set(false);
      return;
    }

    if (this.step() === 'confirmation') {
      this.loadConfirmation(this.route.snapshot.paramMap.get('appointmentId') ?? '');
      return;
    }
    const savedBooking = this.flow.selection();
    const requestedStart = this.route.snapshot.queryParamMap.get('startsAt');
    if (savedBooking.doctor?.id === this.doctorId
        && savedBooking.appointment
        && requestedStart
        && savedBooking.appointment.scheduledAt === requestedStart) {
      this.router.navigate(
        ['/reservation', this.doctorId, 'confirmation', savedBooking.appointment.id],
        { replaceUrl: true }
      );
      return;
    }
    if (this.step() === 'date'
        && !this.route.snapshot.queryParamMap.has('date')
        && !this.route.snapshot.queryParamMap.has('startsAt')) {
      this.flow.startNewBooking();
    }
    this.initializeSelection();
    this.loadDoctor();
  }

  stepIndex(): number {
    return this.step() === 'date' ? 1 : this.step() === 'recap' ? 2 : 3;
  }

  progressWidth(): number {
    return (this.stepIndex() - 1) * 50;
  }

  stepTitle(): string {
    return this.step() === 'date' ? 'Choisissez votre créneau'
      : this.step() === 'recap' ? 'Vérifiez votre réservation' : 'Rendez-vous confirmé';
  }

  calendarDays(): CalendarDay[] {
    const year = this.monthDate.getFullYear();
    const month = this.monthDate.getMonth();
    const firstWeekday = (new Date(year, month, 1).getDay() + 6) % 7;
    const daysInMonth = new Date(year, month + 1, 0).getDate();
    const total = Math.ceil((firstWeekday + daysInMonth) / 7) * 7;
    return Array.from({ length: total }, (_, index) => {
      const dayNumber = index - firstWeekday + 1;
      const date = new Date(year, month, dayNumber);
      return { date, key: this.dateKey(date), inMonth: dayNumber >= 1 && dayNumber <= daysInMonth };
    });
  }

  hasAvailability(dayKey: string): boolean {
    return this.slots().some(slot => slot.startsAt.slice(0, 10) === dayKey);
  }

  slotsForSelectedDate(): AppointmentSlot[] {
    return this.slots()
      .filter(slot => slot.startsAt.slice(0, 10) === this.selectedDateKey())
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  }

  selectedDate(): Date | null {
    return this.selectedDateKey() ? new Date(`${this.selectedDateKey()}T00:00:00`) : null;
  }

  selectDate(dateKey: string): void {
    this.selectedDateKey.set(dateKey);
    this.selectedSlot.set(null);
    this.flow.setDate(dateKey);
    this.updateDateQuery(dateKey);
  }

  selectSlot(slot: AppointmentSlot): void {
    this.selectedSlot.set(slot);
    this.flow.setSlot(slot);
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { date: this.selectedDateKey(), startsAt: slot.startsAt },
      replaceUrl: true
    });
  }

  changeMonth(delta: number): void {
    this.monthDate = new Date(this.monthDate.getFullYear(), this.monthDate.getMonth() + delta, 1);
    this.selectedDateKey.set('');
    this.selectedSlot.set(null);
    this.flow.setDate('');
    this.flow.setSlot(null);
    this.updateDateQuery(null);
    this.loadMonthSlots();
  }

  goToRecap(): void {
    const slot = this.selectedSlot();
    if (!slot) return;
    this.flow.setSlot(slot);
    this.router.navigate(['/reservation', this.doctorId, 'recap'], {
      queryParams: { date: slot.startsAt.slice(0, 10), startsAt: slot.startsAt }
    });
  }

  returnToDate(): void {
    this.router.navigate(['/reservation', this.doctorId, 'date'], {
      queryParams: {
        date: this.selectedDateKey() || this.flow.selection().dateKey || null,
        startsAt: this.selectedSlot()?.startsAt ?? this.flow.selection().slot?.startsAt ?? null
      }
    });
  }

  updateReason(reason: string): void {
    this.flow.setConsultationReason(reason);
  }

  doctorName(doctor: PatientDoctorDetail): string {
    const name = [doctor.firstName, doctor.lastName].filter(value => !!value?.trim()).join(' ');
    return name ? `Dr ${name}` : 'Nom à renseigner';
  }

  initials(doctor: PatientDoctorDetail): string {
    const value = `${doctor.firstName?.trim().charAt(0) ?? ''}${doctor.lastName?.trim().charAt(0) ?? ''}`;
    return value.toLocaleUpperCase('fr') || 'M';
  }

  location(doctor: PatientDoctorDetail): string {
    return [doctor.officeAddress, doctor.officeCity].filter(value => !!value?.trim()).join(', ')
      || 'Lieu de consultation non renseigné';
  }

  summaryDate(): string {
    const value = this.selectedSlot()?.startsAt
      ?? this.flow.selection().slot?.startsAt
      ?? this.appointment()?.scheduledAt;
    return value ? new Date(value).toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }) : 'À choisir';
  }

  summaryTime(): string {
    const value = this.selectedSlot()?.startsAt
      ?? this.flow.selection().slot?.startsAt
      ?? this.appointment()?.scheduledAt;
    return value ? new Date(value).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }) : 'À choisir';
  }

  appointmentDate(appointment: Appointment): Date {
    return new Date(appointment.scheduledAt);
  }

  confirmBooking(): void {
    const slot = this.selectedSlot();
    if (!slot || this.booking()) return;
    this.error.set('');
    this.booking.set(true);
    const reason = this.flow.selection().consultationReason.trim() || null;
    this.api.bookAppointment(this.doctorId, slot.startsAt, reason).subscribe({
      next: appointment => {
        this.booking.set(false);
        this.flow.setAppointment(appointment);
        this.router.navigate(['/reservation', this.doctorId, 'confirmation', appointment.id]);
      },
      error: (error: unknown) => {
        this.booking.set(false);
        const slotConflict = error instanceof HttpErrorResponse && (error.status === 409 || error.status === 400);
        this.error.set(error instanceof HttpErrorResponse && error.status === 409
          ? 'Ce créneau vient d’être réservé. Choisissez une autre disponibilité.'
          : error instanceof HttpErrorResponse && error.status === 400
            ? 'Ce créneau n’est plus réservable. Choisissez une autre disponibilité.'
            : 'La réservation a échoué. Vérifiez votre connexion puis réessayez.');
        if (slotConflict) {
          this.selectedSlot.set(null);
          this.flow.setSlot(null);
        }
      }
    });
  }

  downloadCalendar(appointment: Appointment): void {
    const start = this.icsDate(appointment.scheduledAt);
    const end = this.icsDate(this.addMinutes(appointment.scheduledAt, appointment.durationMinutes));
    const doctor = this.doctor();
    const title = doctor ? `Consultation avec ${this.doctorName(doctor)}` : 'Consultation médicale';
    const lines = [
      'BEGIN:VCALENDAR',
      'VERSION:2.0',
      'PRODID:-//ClairSante//Rendez-vous//FR',
      'BEGIN:VEVENT',
      `UID:${appointment.id}@clairsante`,
      `DTSTAMP:${this.utcNow()}`,
      `DTSTART:${start}`,
      `DTEND:${end}`,
      `SUMMARY:${this.escapeIcs(title)}`,
      `LOCATION:${this.escapeIcs(doctor ? this.location(doctor) : '')}`,
      'END:VEVENT',
      'END:VCALENDAR'
    ];
    const blob = new Blob([lines.join('\r\n')], { type: 'text/calendar;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = 'rendez-vous-clairsante.ics';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  private initializeSelection(): void {
    const current = this.flow.selection();
    const hasCurrentDoctor = current.doctor?.id === this.doctorId;
    const date = this.route.snapshot.queryParamMap.get('date') || (hasCurrentDoctor ? current.dateKey : '');
    const startsAt = this.route.snapshot.queryParamMap.get('startsAt') || (hasCurrentDoctor ? current.slot?.startsAt : undefined);
    this.selectedDateKey.set(date);
    this.selectedSlot.set(startsAt && current.slot?.startsAt === startsAt ? current.slot : null);
    this.reasonInput = hasCurrentDoctor ? current.consultationReason : '';
    if (date) {
      const [year, month] = date.split('-').map(Number);
      this.monthDate = new Date(year, month - 1, 1);
    }
  }

  private loadDoctor(): void {
    this.loadingDoctor.set(true);
    this.api.patientDoctor(this.doctorId).subscribe({
      next: doctor => {
        this.doctor.set(doctor);
        this.flow.setDoctor(doctor);
        this.loadingDoctor.set(false);
        if (this.step() === 'date') this.loadMonthSlots();
        else this.validateSelectedSlot();
      },
      error: () => {
        this.pageError.set('Le profil de ce médecin n’est pas disponible.');
        this.loadingDoctor.set(false);
        this.validatingSlot.set(false);
      }
    });
  }

  private loadMonthSlots(): void {
    const from = this.dateKey(new Date(this.monthDate.getFullYear(), this.monthDate.getMonth(), 1));
    const to = this.dateKey(new Date(this.monthDate.getFullYear(), this.monthDate.getMonth() + 1, 0));
    this.loadingSlots.set(true);
    this.slotsError.set(false);
    this.slots.set([]);
    this.api.availableSlots(this.doctorId, from, to).subscribe({
      next: slots => {
        this.slots.set(this.sorted(slots));
        this.loadingSlots.set(false);
        const startsAt = this.route.snapshot.queryParamMap.get('startsAt') || this.flow.selection().slot?.startsAt;
        if (startsAt) {
          const current = slots.find(slot => slot.startsAt === startsAt);
          if (current) {
            this.selectedSlot.set(current);
            this.flow.setSlot(current);
          } else {
            this.selectedSlot.set(null);
            this.flow.setSlot(null);
          }
        }
      },
      error: () => {
        this.loadingSlots.set(false);
        this.slotsError.set(true);
      }
    });
  }

  private validateSelectedSlot(): void {
    const selection = this.flow.selection();
    const date = this.route.snapshot.queryParamMap.get('date') || selection.dateKey;
    const startsAt = this.route.snapshot.queryParamMap.get('startsAt') || selection.slot?.startsAt;
    if (!date || !startsAt) {
      this.pageError.set('Choisissez une date et un créneau avant de continuer.');
      this.validatingSlot.set(false);
      return;
    }
    this.selectedDateKey.set(date);
    this.selectedSlot.set(null);
    this.validatingSlot.set(true);
    this.api.availableSlots(this.doctorId, date, date).subscribe({
      next: slots => {
        const freeSlot = slots.find(slot => slot.startsAt === startsAt);
        this.validatingSlot.set(false);
        if (!freeSlot) {
          this.pageError.set('Ce créneau n’est plus disponible. Choisissez un autre horaire.');
          this.flow.setSlot(null);
          return;
        }
        this.selectedSlot.set(freeSlot);
        this.flow.setDate(date);
        this.flow.setSlot(freeSlot);
        this.reasonInput = selection.consultationReason;
      },
      error: () => {
        this.validatingSlot.set(false);
        this.pageError.set('Impossible de vérifier ce créneau. Revenez en arrière et réessayez.');
      }
    });
  }

  private loadConfirmation(appointmentId: string): void {
    if (!appointmentId) {
      this.pageError.set('La confirmation du rendez-vous est introuvable.');
      this.loadingConfirmation.set(false);
      return;
    }
    const cached = this.flow.selection().appointment;
    if (cached?.id === appointmentId) {
      this.setConfirmationDetails(cached);
      return;
    }
    this.api.patientAppointments().subscribe({
      next: appointments => {
        const saved = appointments.find(item => item.id === appointmentId);
        if (!saved) {
          this.pageError.set('Ce rendez-vous ne figure pas dans votre espace patient.');
          this.loadingConfirmation.set(false);
          return;
        }
        this.flow.setAppointment(saved);
        this.setConfirmationDetails(saved);
      },
      error: () => {
        this.pageError.set('Impossible de charger la confirmation. Consultez votre espace patient.');
        this.loadingConfirmation.set(false);
      }
    });
  }

  private setConfirmationDetails(appointment: Appointment): void {
    this.appointment.set(appointment);
    this.selectedDateKey.set(appointment.scheduledAt.slice(0, 10));
    this.doctorId = appointment.doctorId;
    const cachedDoctor = this.flow.selection().doctor;
    if (cachedDoctor?.id === appointment.doctorId) {
      this.doctor.set(cachedDoctor);
      this.loadingConfirmation.set(false);
      return;
    }
    this.api.patientDoctor(appointment.doctorId).pipe(
      catchError(() => of(null))
    ).subscribe(doctor => {
      this.doctor.set(doctor);
      this.loadingConfirmation.set(false);
    });
  }

  private updateDateQuery(date: string | null): void {
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { date, startsAt: null },
      replaceUrl: true
    });
  }

  private sorted(slots: AppointmentSlot[]): AppointmentSlot[] {
    return [...slots].sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  }

  private dateKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private addMinutes(value: string, minutes: number): string {
    const date = this.localDate(value);
    date.setMinutes(date.getMinutes() + minutes);
    return this.localDateString(date);
  }

  private icsDate(value: string): string {
    return value.replace(/[-:]/g, '').slice(0, 15);
  }

  private localDate(value: string): Date {
    const [day, time = '00:00:00'] = value.split('T');
    const [year, month, date] = day.split('-').map(Number);
    const [hour, minute, second = '0'] = time.split(':');
    return new Date(year, month - 1, date, Number(hour), Number(minute), Number(second.slice(0, 2)));
  }

  private localDateString(date: Date): string {
    const pad = (value: number) => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  private utcNow(): string {
    return new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '');
  }

  private escapeIcs(value: string): string {
    return value.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/,/g, '\\,').replace(/;/g, '\\;');
  }
}
