import { DatePipe, SlicePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { catchError, of } from 'rxjs';
import { AppointmentSlot, DayOfWeek, PatientDoctorDetail, TelemedApiService } from '../../core/telemed-api.service';

const DAY_NAMES: Record<DayOfWeek, string> = {
  MONDAY: 'Lundi',
  TUESDAY: 'Mardi',
  WEDNESDAY: 'Mercredi',
  THURSDAY: 'Jeudi',
  FRIDAY: 'Vendredi',
  SATURDAY: 'Samedi',
  SUNDAY: 'Dimanche'
};

const DAY_ORDER: DayOfWeek[] = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'
];

@Component({
  selector: 'app-doctor-profile',
  standalone: true,
  imports: [DatePipe, RouterLink, SlicePipe],
  template: `
    <div class="page">
      <header class="topbar">
        <a class="brand" routerLink="/medecins"><span class="logo">+</span><span>ClairSanté<small>ESPACE PATIENT</small></span></a>
        <a class="back-link" routerLink="/patient">← Retour à mon espace</a>
      </header>

      <main>
        @if (loading()) {
          <div class="state-card" role="status"><span class="loader"></span>Chargement du profil…</div>
        } @else if (error()) {
          <div class="state-card error" role="alert">{{ error() }} <a routerLink="/medecins">Retour à la liste</a></div>
        } @else if (doctor(); as profile) {
          <section class="profile-card">
            <div class="profile-heading">
              @if (profile.profilePhotoUrl && !photoError()) {
                <img class="avatar" [src]="profile.profilePhotoUrl" [alt]="'Photo de ' + doctorName(profile)"
                     (error)="photoError.set(true)">
              } @else {
                <span class="avatar initials" aria-hidden="true">{{ initials(profile) }}</span>
              }
              <div class="identity">
                <span class="eyebrow">PROFIL PROFESSIONNEL</span>
                <h1>{{ doctorName(profile) }}</h1>
                <p class="specialty">{{ profile.specialty || 'Spécialité non renseignée' }}</p>
              </div>
              <a class="primary desktop-cta" [routerLink]="['/reservation', profile.id, 'date']">Prendre rendez-vous
                <span class="material-icons" aria-hidden="true">arrow_forward</span>
              </a>
            </div>

            <div class="profile-grid">
              <section class="info-card">
                <h2><span class="material-icons" aria-hidden="true">person</span>Présentation</h2>
                <p class="bio">{{ profile.bio?.trim() || 'Aucune présentation renseignée pour le moment.' }}</p>
              </section>

              <section class="info-card">
                <h2><span class="material-icons" aria-hidden="true">location_on</span>Lieu de consultation</h2>
                <p>{{ location(profile) }}</p>
                <p class="muted">Les modalités précises seront confirmées lors de la prise de rendez-vous.</p>
              </section>

              <section class="info-card hours">
                <h2><span class="material-icons" aria-hidden="true">schedule</span>Horaires habituels</h2>
                @if (profile.availabilities.length === 0) {
                  <p class="muted">Les horaires ne sont pas renseignés.</p>
                } @else {
                  <ul>
                    @for (availability of orderedAvailabilities(profile); track availability.id) {
                      <li><strong>{{ dayName(availability.dayOfWeek) }}</strong>
                        <span>{{ availability.startTime | slice:0:5 }} – {{ availability.endTime | slice:0:5 }}</span>
                      </li>
                    }
                  </ul>
                  <p class="muted">Les créneaux proposés peuvent varier selon les rendez-vous déjà pris.</p>
                }
              </section>

              <section class="info-card">
                <h2><span class="material-icons" aria-hidden="true">medical_services</span>Actes proposés</h2>
                <p class="muted">Les actes proposés ne sont pas renseignés.</p>
              </section>

              <section class="info-card">
                <h2><span class="material-icons" aria-hidden="true">payments</span>Tarif</h2>
                <p class="muted">Tarif non renseigné.</p>
              </section>
            </div>

            <section class="next-slot">
              <div>
                <span class="eyebrow">PROCHAINE DISPONIBILITÉ</span>
                @if (loadingSlots()) {
                  <p>Recherche des créneaux…</p>
                } @else if (slotError()) {
                  <p>Les disponibilités ne peuvent pas être chargées pour le moment.</p>
                } @else if (nextSlot(); as slot) {
                  <p>{{ slot.startsAt | date:'EEEE d MMMM y à HH:mm':'':'fr' }}</p>
                } @else {
                  <p>Aucun créneau dans les 31 prochains jours.</p>
                }
              </div>
              <a class="primary mobile-cta" [routerLink]="['/reservation', profile.id, 'date']">Prendre rendez-vous</a>
            </section>
          </section>
        }

        <footer><span class="material-icons" aria-hidden="true">verified_user</span>Vos informations restent protégées et confidentielles.</footer>
      </main>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f5f8f7; color:#1d3b44; }
    .page { width:min(940px,100%); margin:0 auto; padding:0 24px 32px; }
    .topbar { display:flex; min-height:72px; align-items:center; justify-content:space-between; border-bottom:1px solid #e5ece9; }
    .brand { display:flex; align-items:center; gap:10px; color:#183d49; font-weight:800; text-decoration:none; }
    .logo { display:grid; width:37px; height:37px; place-items:center; border-radius:12px; background:#087e75; color:#fff; font-size:26px; }
    .brand small { display:block; margin-top:3px; color:#94a3a4; font-size:9px; letter-spacing:1.2px; }
    .back-link { color:#087e75; font-size:12px; font-weight:700; text-decoration:none; }
    .profile-card { margin-top:25px; border:1px solid #e3ebe8; border-radius:19px; padding:24px; background:#fff; box-shadow:0 8px 28px #234b4008; }
    .profile-heading { display:flex; align-items:center; gap:16px; padding-bottom:21px; border-bottom:1px solid #edf2f0; }
    .avatar { width:80px; height:80px; flex:0 0 auto; border-radius:23px; object-fit:cover; }
    .initials { display:grid; place-items:center; background:#e8f4ef; color:#087e75; font-size:24px; font-weight:800; }
    .identity { min-width:0; flex:1; }
    .eyebrow { color:#77938e; font-size:9px; font-weight:800; letter-spacing:1.4px; }
    h1 { margin:7px 0 5px; color:#183b46; font-size:25px; letter-spacing:-.5px; }
    .specialty { margin:0; color:#168477; font-size:13px; font-weight:700; }
    .primary { display:inline-flex; min-height:42px; align-items:center; justify-content:center; gap:7px; border-radius:10px; padding:0 15px; background:#087e75; color:#fff; font-size:11px; font-weight:750; text-decoration:none; }
    .primary .material-icons { font-size:16px; }
    .mobile-cta { display:none; }
    .profile-grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:12px; padding:20px 0; }
    .info-card { min-width:0; border:1px solid #e7eeeb; border-radius:13px; padding:15px; background:#fbfdfc; }
    .info-card h2 { display:flex; align-items:center; gap:7px; margin:0 0 12px; color:#35545c; font-size:13px; }
    .info-card h2 .material-icons { color:#198477; font-size:17px; }
    .info-card p { margin:0 0 8px; color:#536d72; font-size:12px; line-height:1.7; }
    .info-card .muted { color:#89989a; font-size:10px; }
    .info-card .bio { white-space:pre-line; }
    .hours ul { display:grid; gap:8px; margin:0 0 10px; padding:0; list-style:none; }
    .hours li { display:flex; justify-content:space-between; gap:10px; color:#536d72; font-size:11px; }
    .hours li strong { color:#35545c; }
    .next-slot { display:flex; align-items:center; justify-content:space-between; gap:15px; border:1px solid #dceee6; border-radius:13px; padding:15px; background:#f4faf7; }
    .next-slot p { margin:6px 0 0; color:#355d58; font-size:13px; font-weight:700; text-transform:capitalize; }
    .state-card { display:flex; min-height:190px; align-items:center; justify-content:center; gap:10px; margin-top:25px; border:1px solid #e3ebe8; border-radius:16px; background:#fff; color:#74878a; font-size:12px; }
    .state-card.error { flex-direction:column; color:#9a463e; background:#fff7f5; }
    .state-card a { color:#087e75; font-weight:700; }
    .loader { width:15px; height:15px; border:2px solid #d8eae4; border-top-color:#087e75; border-radius:50%; animation:spin .7s linear infinite; }
    footer { display:flex; align-items:center; justify-content:center; gap:6px; margin-top:25px; color:#98a5a5; font-size:10px; }
    footer .material-icons { color:#55978a; font-size:14px; }
    @keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:620px) { .page { padding:0 14px 26px; }.topbar { min-height:62px; }.brand { font-size:13px; }.back-link { font-size:10px; }.profile-card { margin-top:17px; padding:16px; border-radius:16px; }.profile-heading { gap:11px; }.avatar { width:61px; height:61px; border-radius:18px; }h1 { font-size:19px; }.specialty { font-size:11px; }.desktop-cta { display:none; }.mobile-cta { display:inline-flex; }.profile-grid { grid-template-columns:1fr; gap:9px; padding:15px 0; }.next-slot { align-items:flex-start; flex-direction:column; }.next-slot p { font-size:12px; } }
  `]
})
export class DoctorProfileComponent implements OnInit {
  readonly doctor = signal<PatientDoctorDetail | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly photoError = signal(false);
  readonly slots = signal<AppointmentSlot[]>([]);
  readonly loadingSlots = signal(true);
  readonly slotError = signal(false);

  constructor(
    private readonly route: ActivatedRoute,
    private readonly api: TelemedApiService
  ) {}

  ngOnInit(): void {
    const doctorId = this.route.snapshot.paramMap.get('id');
    if (!doctorId) {
      this.error.set('Le profil demandé est introuvable.');
      this.loading.set(false);
      this.loadingSlots.set(false);
      return;
    }

    this.api.patientDoctor(doctorId).subscribe({
      next: doctor => {
        this.doctor.set(doctor);
        this.loading.set(false);
        this.loadSlots(doctorId);
      },
      error: () => {
        this.error.set('Ce médecin est introuvable ou son profil n’est pas disponible.');
        this.loading.set(false);
        this.loadingSlots.set(false);
      }
    });
  }

  nextSlot(): AppointmentSlot | null {
    return this.slots()[0] ?? null;
  }

  doctorName(doctor: PatientDoctorDetail): string {
    const name = [doctor.firstName, doctor.lastName].filter(value => !!value?.trim()).join(' ');
    return name ? `Dr ${name}` : 'Nom à renseigner';
  }

  initials(doctor: PatientDoctorDetail): string {
    const initials = `${doctor.firstName?.trim().charAt(0) ?? ''}${doctor.lastName?.trim().charAt(0) ?? ''}`;
    return initials.toLocaleUpperCase('fr') || 'M';
  }

  location(doctor: PatientDoctorDetail): string {
    return [doctor.officeAddress, doctor.officeCity].filter(value => !!value?.trim()).join(', ')
      || 'Lieu de consultation non renseigné';
  }

  orderedAvailabilities(doctor: PatientDoctorDetail) {
    return [...doctor.availabilities].sort((a, b) =>
      DAY_ORDER.indexOf(a.dayOfWeek) - DAY_ORDER.indexOf(b.dayOfWeek)
        || a.startTime.localeCompare(b.startTime));
  }

  dayName(day: DayOfWeek): string {
    return DAY_NAMES[day];
  }

  private loadSlots(doctorId: string): void {
    const from = this.dateKey(new Date());
    const toDate = new Date();
    toDate.setDate(toDate.getDate() + 30);
    this.api.availableSlots(doctorId, from, this.dateKey(toDate)).pipe(
      catchError(() => {
        this.slotError.set(true);
        return of([]);
      })
    ).subscribe(slots => {
      this.slots.set([...slots].sort((a, b) => a.startsAt.localeCompare(b.startsAt)));
      this.loadingSlots.set(false);
    });
  }

  private dateKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
