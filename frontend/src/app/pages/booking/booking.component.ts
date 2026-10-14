import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AppointmentSlot, DoctorProfile, TelemedApiService } from '../../core/telemed-api.service';

interface CalendarDay {
  date: Date;
  key: string;
  inMonth: boolean;
}

@Component({
  selector: 'app-booking',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <div class="booking-page">
      <header class="header">
        <a class="brand" routerLink="/patient"><span class="logo">+</span><span>ClairSanté<small>ESPACE PATIENT</small></span></a>
        <a class="back-link" routerLink="/patient">← Retour à mon espace</a>
      </header>
      <main>
        <section class="intro"><span class="eyebrow">À VOTRE ÉCOUTE</span><h1>Réservez votre consultation</h1><p>Choisissez un professionnel et le créneau qui vous convient.</p></section>
        @if (error()) { <div class="notice" role="alert">{{ error() }}</div> }
        @if (success()) { <div class="success" role="status">{{ success() }} <a routerLink="/patient">Voir mes rendez-vous</a></div> }
        <div class="booking-grid">
          <section class="calendar-card">
            <div class="step-heading"><span class="step">1</span><div><h2>Choisir un médecin</h2><p>Les disponibilités à venir s’afficheront sur le calendrier.</p></div></div>
            @if (doctors().length === 0) {
              <div class="doctor-empty">Aucun médecin n’est disponible dans l’annuaire pour le moment.</div>
            } @else {
              <div class="doctor-list" role="list" aria-label="Médecins disponibles">
                @for (doctor of doctors(); track doctor.id) {
                  <button type="button" class="doctor-choice" [class.selected]="selectedDoctor()?.id === doctor.id"
                          (click)="selectDoctor(doctor)">
                    <span class="doctor-avatar material-icons">medical_services</span>
                    <span class="doctor-copy"><strong>Médecin</strong><small>{{ doctor.specialty }}</small></span>
                    <span class="check material-icons">{{ selectedDoctor()?.id === doctor.id ? 'check_circle' : 'radio_button_unchecked' }}</span>
                  </button>
                }
              </div>
            }

            <div class="step-heading calendar-title"><span class="step">2</span><div><h2>Choisir une date</h2><p>Seuls les jours avec un créneau libre sont activés.</p></div></div>
            <div class="month-bar">
              <button type="button" aria-label="Mois précédent" (click)="changeMonth(-1)">‹</button>
              <strong>{{ monthDate | date:'MMMM y':'':'fr' }}</strong>
              <button type="button" aria-label="Mois suivant" (click)="changeMonth(1)">›</button>
            </div>
            <div class="calendar-grid" aria-label="Calendrier des disponibilités">
              @for (name of weekdays; track name) { <span class="weekday">{{ name }}</span> }
              @for (day of calendarDays(); track day.key) {
                @if (day.inMonth) {
                  <button type="button" class="calendar-day" [class.selected]="selectedDateKey() === day.key"
                          [class.available]="hasAvailability(day.key)" [disabled]="!hasAvailability(day.key)"
                          (click)="selectDate(day.date)" [attr.aria-label]="day.date | date:'EEEE d MMMM':'':'fr'">
                    {{ day.date | date:'d' }}
                  </button>
                } @else { <span class="calendar-spacer" aria-hidden="true"></span> }
              }
            </div>
            @if (loadingSlots()) { <p class="loading-note"><span class="loader"></span>Recherche des créneaux disponibles…</p> }
          </section>

          <section class="slots-card" aria-labelledby="slots-title">
            <div class="step-heading"><span class="step">3</span><div><h2 id="slots-title">Choisir un créneau</h2><p>{{ selectedDate() ? (selectedDate() | date:'EEEE d MMMM y':'':'fr') : 'Sélectionnez une date sur le calendrier' }}</p></div></div>
            @if (!selectedDoctor()) {
              <div class="slot-empty"><span class="material-icons">person_search</span><h3>Commencez par choisir un médecin</h3><p>Ses créneaux disponibles s’afficheront ici.</p></div>
            } @else if (!selectedDate()) {
              <div class="slot-empty"><span class="material-icons">calendar_month</span><h3>Choisissez une date disponible</h3><p>Les créneaux libres sont indiqués en vert dans le calendrier.</p></div>
            } @else if (availableForSelectedDate().length === 0 && !loadingSlots()) {
              <div class="slot-empty"><span class="material-icons">event_busy</span><h3>Aucun créneau ce jour-là</h3><p>Essayez une autre date dans le calendrier.</p></div>
            } @else {
              <div class="slots-grid">
                @for (slot of availableForSelectedDate(); track slot.startsAt) {
                  <button type="button" class="slot" [class.selected]="selectedSlot()?.startsAt === slot.startsAt" (click)="selectSlot(slot)">
                    <span class="material-icons">schedule</span>{{ slot.startsAt | date:'HH:mm' }}
                  </button>
                }
              </div>
              @if (selectedSlot()) {
                <div class="selection-summary">
                  <span class="material-icons">event_available</span>
                  <div><strong>Votre sélection</strong><p>{{ selectedDate() | date:'EEEE d MMMM':'':'fr' }} à {{ selectedSlot()!.startsAt | date:'HH:mm' }}</p></div>
                </div>
                <button type="button" class="confirm-button" [disabled]="booking()" (click)="confirmBooking()">
                  {{ booking() ? 'Réservation…' : 'Confirmer le rendez-vous' }} <span class="material-icons">arrow_forward</span>
                </button>
              }
            }
            <div class="secure-note"><span class="material-icons">verified_user</span>Vos informations sont protégées et confidentielles.</div>
          </section>
        </div>
      </main>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f5f8f7; color:#1d3b44; }
    .booking-page { max-width:1200px; margin:0 auto; padding:0 25px 50px; }
    .header { display:flex; height:76px; align-items:center; justify-content:space-between; border-bottom:1px solid #e5ece9; }
    .brand { display:flex; align-items:center; gap:11px; color:#183d49; font-weight:800; text-decoration:none; }.logo { display:grid; width:39px; height:39px; place-items:center; border-radius:13px; background:#087e75; color:#fff; font-size:27px; }.brand small { display:block; margin-top:3px; color:#94a3a4; font-size:9px; letter-spacing:1.3px; }
    .back-link { color:#708388; font-size:12px; text-decoration:none; }.intro { margin:35px 0 24px; }.eyebrow { color:#77938e; font-size:9px; font-weight:750; letter-spacing:1.3px; }h1 { margin:8px 0 6px; color:#183b46; font-size:30px; letter-spacing:-.8px; }.intro p { margin:0; color:#7b8b8f; font-size:13px; }
    .booking-grid { display:grid; grid-template-columns:minmax(0,1.25fr) minmax(310px,.75fr); align-items:start; gap:17px; }.calendar-card,.slots-card { border:1px solid #e6edeb; border-radius:19px; padding:22px; background:#fff; box-shadow:0 8px 28px #234b4008; }
    .step-heading { display:flex; align-items:flex-start; gap:11px; }.step { display:grid; width:26px; height:26px; flex:0 0 auto; place-items:center; border-radius:50%; background:#e5f4ee; color:#138173; font-size:11px; font-weight:800; }.step-heading h2 { margin:2px 0 5px; color:#2c4c54; font-size:14px; }.step-heading p { margin:0; color:#89979a; font-size:10px; line-height:1.5; }
    .doctor-list { display:grid; gap:7px; margin:15px 0 23px; }.doctor-choice { display:flex; align-items:center; gap:10px; width:100%; border:1px solid #e7edeb; border-radius:12px; padding:10px; background:white; text-align:left; cursor:pointer; transition:border .16s,background .16s; }.doctor-choice:hover,.doctor-choice.selected { border-color:#a9d9cb; background:#f5fbf8; }.doctor-avatar { display:grid; width:36px; height:36px; place-items:center; border-radius:11px; background:#eaf3f0; color:#2b8a7d; font-size:18px; }.doctor-copy { display:grid; flex:1; gap:4px; }.doctor-copy strong { color:#38555d; font-size:11px; }.doctor-copy small { color:#819093; font-size:10px; }.check { color:#168477; font-size:18px; }.doctor-empty { margin:15px 0 22px; border-radius:10px; padding:13px; background:#f7faf9; color:#7d8d90; font-size:11px; }
    .calendar-title { margin-bottom:18px; }.month-bar { display:flex; align-items:center; justify-content:space-between; margin:8px 0 14px; }.month-bar strong { color:#38565d; font-size:13px; text-transform:capitalize; }.month-bar button { width:29px; height:29px; border:1px solid #e5ece9; border-radius:9px; background:#fff; color:#59746e; font-size:20px; cursor:pointer; }
    .calendar-grid { display:grid; grid-template-columns:repeat(7,minmax(0,1fr)); justify-items:center; gap:6px 4px; }.weekday { padding:5px 0; color:#9aa6a6; font-size:9px; text-transform:capitalize; }.calendar-day,.calendar-spacer { display:grid; width:37px; height:37px; place-items:center; }.calendar-day { border:1px solid transparent; border-radius:50%; background:transparent; color:#aab4b4; font:inherit; font-size:11px; }.calendar-day.available { color:#2b6f61; cursor:pointer; }.calendar-day.available:not(.selected) { background:#f0f8f4; }.calendar-day.selected { border-color:#087e75; background:#087e75; color:white; font-weight:750; }.calendar-day:disabled { cursor:default; opacity:.45; }
    .loading-note { display:flex; align-items:center; gap:8px; margin:16px 0 0; color:#879598; font-size:10px; }.loader { width:13px; height:13px; border:2px solid #d8eae4; border-top-color:#087e75; border-radius:50%; animation:spin .7s linear infinite; }
    .slots-card { min-height:400px; }.slots-card>.step-heading { margin-bottom:22px; }.slots-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:8px; }.slot { display:flex; align-items:center; justify-content:center; gap:5px; min-height:41px; border:1px solid #dce9e4; border-radius:10px; background:white; color:#4f7069; font:inherit; font-size:11px; font-weight:650; cursor:pointer; transition:all .15s; }.slot .material-icons { font-size:14px; }.slot:hover,.slot.selected { border-color:#158879; background:#e9f6f1; color:#087e75; }
    .slot-empty { display:grid; min-height:220px; justify-items:center; align-content:center; text-align:center; }.slot-empty>.material-icons { color:#6b9f93; font-size:30px; }.slot-empty h3 { margin:12px 0 6px; color:#446168; font-size:13px; }.slot-empty p { max-width:230px; margin:0; color:#89979a; font-size:10px; line-height:1.6; }
    .selection-summary { display:flex; align-items:center; gap:10px; margin-top:20px; border:1px solid #dceee6; border-radius:12px; padding:12px; background:#f4faf7; }.selection-summary>.material-icons { color:#198776; }.selection-summary strong { color:#3d5b60; font-size:10px; }.selection-summary p { margin:4px 0 0; color:#79908b; font-size:10px; text-transform:capitalize; }
    .confirm-button { display:flex; width:100%; align-items:center; justify-content:center; gap:9px; margin-top:12px; border:0; border-radius:11px; padding:13px; background:#087e75; color:#fff; font:inherit; font-size:11px; font-weight:750; cursor:pointer; }.confirm-button .material-icons { font-size:16px; }.confirm-button:disabled { opacity:.6; cursor:wait; }
    .secure-note { display:flex; align-items:center; justify-content:center; gap:6px; margin-top:20px; color:#8d9b9c; font-size:9px; }.secure-note .material-icons { color:#258c7e; font-size:14px; }
    .notice,.success { margin-bottom:15px; border-radius:10px; padding:12px 14px; font-size:11px; }.notice { background:#fff1ee; color:#9a463e; }.success { background:#e8f6ef; color:#286e53; }.success a { margin-left:8px; color:#087e75; font-weight:700; }
    @keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:780px) { .booking-grid { grid-template-columns:1fr; }.slots-card { min-height:320px; }.slot-empty { min-height:170px; } }
    @media(max-width:500px) { .booking-page { padding:0 13px 28px; }.header { height:62px; }.brand { font-size:13px; }.brand small { font-size:8px; }.back-link { font-size:10px; }.intro { margin:25px 0 17px; }h1 { font-size:24px; }.intro p { font-size:11px; }.calendar-card,.slots-card { padding:16px; border-radius:15px; }.calendar-day,.calendar-spacer { width:33px; height:33px; }.calendar-grid { gap:4px 2px; }.slots-grid { grid-template-columns:repeat(3,minmax(0,1fr)); }.slot { min-height:38px; font-size:10px; } }
  `]
})
export class BookingComponent implements OnInit {
  readonly doctors = signal<DoctorProfile[]>([]);
  readonly selectedDoctor = signal<DoctorProfile | null>(null);
  readonly slots = signal<AppointmentSlot[]>([]);
  readonly selectedDate = signal<Date | null>(null);
  readonly selectedDateKey = signal('');
  readonly selectedSlot = signal<AppointmentSlot | null>(null);
  readonly loadingSlots = signal(false);
  readonly booking = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly weekdays = ['lun.', 'mar.', 'mer.', 'jeu.', 'ven.', 'sam.', 'dim.'];
  monthDate = new Date(new Date().getFullYear(), new Date().getMonth(), 1);

  constructor(private readonly api: TelemedApiService) {}

  ngOnInit(): void {
    this.api.patientDoctors().subscribe({
      next: doctors => {
        this.doctors.set(doctors);
        if (doctors.length > 0) {
          this.selectDoctor(doctors[0]);
        }
      },
      error: () => this.error.set('L’annuaire des médecins est indisponible.')
    });
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
      return { date, key: this.toKey(date), inMonth: dayNumber >= 1 && dayNumber <= daysInMonth };
    });
  }

  hasAvailability(dayKey: string): boolean {
    return this.slots().some(slot => slot.startsAt.slice(0, 10) === dayKey);
  }

  availableForSelectedDate(): AppointmentSlot[] {
    const key = this.selectedDateKey();
    return this.slots()
      .filter(slot => slot.startsAt.slice(0, 10) === key)
      .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  }

  selectDoctor(doctor: DoctorProfile): void {
    this.selectedDoctor.set(doctor);
    this.selectedDate.set(null);
    this.selectedDateKey.set('');
    this.selectedSlot.set(null);
    this.loadMonthSlots();
  }

  selectDate(date: Date): void {
    this.selectedDate.set(date);
    this.selectedDateKey.set(this.toKey(date));
    this.selectedSlot.set(null);
  }

  selectSlot(slot: AppointmentSlot): void {
    this.selectedSlot.set(slot);
  }

  changeMonth(delta: number): void {
    this.monthDate = new Date(this.monthDate.getFullYear(), this.monthDate.getMonth() + delta, 1);
    this.selectedDate.set(null);
    this.selectedDateKey.set('');
    this.selectedSlot.set(null);
    this.loadMonthSlots();
  }

  confirmBooking(): void {
    const doctor = this.selectedDoctor();
    const slot = this.selectedSlot();
    if (!doctor || !slot) {
      return;
    }
    this.error.set('');
    this.booking.set(true);
    this.api.bookAppointment(doctor.id, slot.startsAt).subscribe({
      next: () => {
        this.booking.set(false);
        this.success.set('Votre rendez-vous est bien enregistré.');
        this.slots.update(items => items.filter(item => item.startsAt !== slot.startsAt));
        this.selectedSlot.set(null);
      },
      error: error => {
        this.booking.set(false);
        this.error.set(error.status === 409
          ? 'Ce créneau vient d’être réservé. Choisissez une autre disponibilité.'
          : error.status === 400
            ? 'Ce créneau n’est plus réservable.'
            : 'La réservation a échoué. Veuillez réessayer.');
        this.loadMonthSlots();
      }
    });
  }

  private loadMonthSlots(): void {
    const doctor = this.selectedDoctor();
    if (!doctor) {
      return;
    }
    this.loadingSlots.set(true);
    const from = this.toKey(new Date(this.monthDate.getFullYear(), this.monthDate.getMonth(), 1));
    const to = this.toKey(new Date(this.monthDate.getFullYear(), this.monthDate.getMonth() + 1, 0));
    this.api.availableSlots(doctor.id, from, to).subscribe({
      next: slots => {
        this.slots.set(slots);
        this.loadingSlots.set(false);
        const today = this.toKey(new Date());
        if (!this.selectedDateKey() && slots.some(slot => slot.startsAt.slice(0, 10) === today)) {
          this.selectDate(new Date());
        }
      },
      error: () => {
        this.loadingSlots.set(false);
        this.error.set('Impossible de charger les disponibilités de ce médecin.');
      }
    });
  }

  private toKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
