import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { catchError, forkJoin, map, of } from 'rxjs';
import { AppointmentSlot, DoctorProfile, TelemedApiService } from '../../core/telemed-api.service';

type AvailabilityFilter = 'all' | 'today' | 'week';

interface DoctorAvailabilityState {
  slots: AppointmentSlot[];
  failed: boolean;
}

@Component({
  selector: 'app-doctor-directory',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  template: `
    <div class="page">
      <header class="topbar">
        <a class="brand" routerLink="/patient"><span class="logo">+</span><span>ClairSanté<small>ESPACE PATIENT</small></span></a>
        <a class="back-link" routerLink="/patient">← Retour à mon espace</a>
      </header>

      <main>
        <section class="intro">
          <span class="eyebrow">PRENEZ SOIN DE VOUS</span>
          <h1>Trouvez votre médecin</h1>
          <p>Choisissez un professionnel et consultez ses prochaines disponibilités.</p>
        </section>

        <section class="filters" aria-label="Filtres des médecins">
          <label class="search">
            <span class="material-icons" aria-hidden="true">search</span>
            <input type="search" aria-label="Rechercher un médecin ou une spécialité"
                   placeholder="Nom ou spécialité" [(ngModel)]="search">
          </label>
          <select aria-label="Filtrer par spécialité" [(ngModel)]="specialtyFilter">
            <option value="">Toutes les spécialités</option>
            @for (specialty of specialties(); track specialty) {
              <option [value]="specialty">{{ specialty }}</option>
            }
          </select>
          <label class="city-filter">
            <span class="material-icons" aria-hidden="true">location_on</span>
            <input type="search" aria-label="Filtrer par ville" placeholder="Ville" [(ngModel)]="cityFilter">
          </label>
          <select aria-label="Filtrer par disponibilité" [(ngModel)]="availabilityFilter">
            <option value="all">Toutes disponibilités</option>
            <option value="today">Aujourd’hui</option>
            <option value="week">Cette semaine</option>
          </select>
        </section>

        @if (loading()) {
          <div class="state-card" role="status"><span class="loader"></span>Chargement des médecins et des créneaux…</div>
        } @else if (error()) {
          <div class="state-card error" role="alert">{{ error() }}</div>
        } @else if (filteredDoctors().length === 0) {
          <div class="state-card empty">
            <span class="material-icons">person_search</span>
            <h2>Aucun médecin trouvé</h2>
            <p>Modifiez vos filtres ou réessayez plus tard.</p>
          </div>
        } @else {
          <div class="doctor-list">
            @for (doctor of filteredDoctors(); track doctor.id) {
              <article class="doctor-card" [class.unavailable]="availabilityState(doctor.id)?.slots?.length === 0 && !availabilityState(doctor.id)?.failed">
                <div class="doctor-heading">
                  @if (doctor.profilePhotoUrl && !photoErrors().has(doctor.id)) {
                    <img class="avatar" [src]="doctor.profilePhotoUrl" [alt]="'Photo de ' + doctorName(doctor)"
                         (error)="markPhotoError(doctor.id)">
                  } @else {
                    <span class="avatar" aria-hidden="true">{{ initials(doctor) }}</span>
                  }
                  <div class="identity">
                    <h2>{{ doctorName(doctor) }}</h2>
                    <p class="specialty">{{ doctor.specialty || 'Spécialité non renseignée' }}</p>
                  </div>
                  @if (availabilityState(doctor.id)?.slots?.length) {
                    <span class="availability-badge"><span></span>Disponible</span>
                  } @else if (availabilityState(doctor.id)?.failed) {
                    <span class="availability-badge unknown">Disponibilité inconnue</span>
                  } @else {
                    <span class="availability-badge none">Aucune disponibilité</span>
                  }
                </div>

                <div class="details">
                  <p><span class="material-icons" aria-hidden="true">location_on</span>
                    {{ location(doctor) }}</p>
                  <p><span class="material-icons" aria-hidden="true">event_available</span>
                    @if (availabilityState(doctor.id)?.failed) {
                      Créneaux momentanément indisponibles
                    } @else if (availabilityState(doctor.id)?.slots?.[0]; as nextSlot) {
                      Prochaine disponibilité : {{ nextSlot.startsAt | date:'EEEE d MMMM à HH:mm':'':'fr' }}
                    } @else {
                      Aucune disponibilité dans les 31 prochains jours
                    }
                  </p>
                  <p><span class="material-icons" aria-hidden="true">payments</span>Tarif non renseigné</p>
                </div>

                <div class="actions">
                  <a class="secondary" [routerLink]="['/medecins', doctor.id]">Voir le profil</a>
                  <a class="primary" [routerLink]="['/reservation', doctor.id, 'date']">Prendre rendez-vous
                    <span class="material-icons" aria-hidden="true">arrow_forward</span>
                  </a>
                </div>
              </article>
            }
          </div>
        }

        <footer><span class="material-icons" aria-hidden="true">verified_user</span>Vos informations restent protégées et confidentielles.</footer>
      </main>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f5f8f7; color:#1d3b44; }
    .page { width:min(1060px,100%); margin:0 auto; padding:0 24px 32px; }
    .topbar { display:flex; min-height:72px; align-items:center; justify-content:space-between; border-bottom:1px solid #e5ece9; }
    .brand { display:flex; align-items:center; gap:10px; color:#183d49; font-weight:800; text-decoration:none; }
    .logo { display:grid; width:37px; height:37px; place-items:center; border-radius:12px; background:#087e75; color:#fff; font-size:26px; }
    .brand small { display:block; margin-top:3px; color:#94a3a4; font-size:9px; letter-spacing:1.2px; }
    .back-link { color:#087e75; font-size:12px; font-weight:700; text-decoration:none; }
    .intro { padding:34px 0 22px; }
    .eyebrow { color:#77938e; font-size:9px; font-weight:800; letter-spacing:1.4px; }
    h1 { margin:8px 0 6px; color:#183b46; font-size:30px; letter-spacing:-.7px; }
    .intro p { margin:0; color:#7b8b8f; font-size:13px; }
    .filters { display:grid; grid-template-columns:2fr 1fr 1fr 1fr; gap:9px; margin-bottom:17px; }
    .filters input,.filters select { width:100%; min-width:0; height:42px; border:1px solid #e2ebe8; border-radius:11px; outline:none; background:#fff; color:#38555d; font:inherit; font-size:12px; }
    .filters input { padding:0 12px; }
    .filters select { padding:0 10px; }
    .filters input:focus,.filters select:focus { border-color:#83c1b2; box-shadow:0 0 0 3px #087e7514; }
    .search,.city-filter { position:relative; }
    .search .material-icons,.city-filter .material-icons { position:absolute; top:12px; left:11px; color:#8ca09e; font-size:17px; pointer-events:none; }
    .search input,.city-filter input { padding-left:36px; }
    .doctor-list { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:13px; }
    .doctor-card { display:flex; min-width:0; flex-direction:column; border:1px solid #e3ebe8; border-radius:17px; padding:18px; background:#fff; box-shadow:0 5px 20px #234b4008; }
    .doctor-card.unavailable { background:#fafcfb; }
    .doctor-heading { display:flex; align-items:center; gap:11px; }
    .avatar { display:grid; width:47px; height:47px; flex:0 0 auto; place-items:center; border-radius:15px; background:#e8f4ef; color:#087e75; font-size:15px; font-weight:800; }
    .identity { min-width:0; flex:1; }
    .identity h2 { overflow:hidden; margin:0 0 4px; color:#264750; font-size:15px; text-overflow:ellipsis; white-space:nowrap; }
    .specialty { margin:0; color:#168477; font-size:11px; font-weight:700; }
    .availability-badge { flex:0 0 auto; border-radius:20px; padding:6px 8px; background:#eaf6f0; color:#267c5c; font-size:9px; font-weight:750; }
    .availability-badge span { display:inline-block; width:6px; height:6px; margin-right:5px; border-radius:50%; background:#38a878; vertical-align:1px; }
    .availability-badge.none { background:#f0f3f2; color:#77888a; }
    .availability-badge.unknown { background:#fff5e3; color:#8c6b31; }
    .details { display:grid; gap:9px; margin:17px 0; }
    .details p { display:flex; align-items:flex-start; gap:7px; margin:0; color:#718489; font-size:11px; line-height:1.5; }
    .details .material-icons { flex:0 0 auto; color:#71948c; font-size:15px; }
    .actions { display:flex; gap:8px; margin-top:auto; }
    .actions a { display:flex; min-height:40px; align-items:center; justify-content:center; gap:6px; border-radius:10px; padding:0 12px; font-size:11px; font-weight:750; text-align:center; text-decoration:none; }
    .secondary { flex:1; border:1px solid #dce9e4; color:#087e75; }
    .primary { flex:1.2; background:#087e75; color:#fff; }
    .primary .material-icons { font-size:15px; }
    .primary:hover { background:#066d66; }
    .state-card { display:flex; min-height:170px; align-items:center; justify-content:center; gap:10px; border:1px solid #e3ebe8; border-radius:16px; background:#fff; color:#74878a; font-size:12px; }
    .state-card.error { color:#9a463e; background:#fff7f5; }
    .state-card.empty { flex-direction:column; text-align:center; }
    .state-card.empty .material-icons { color:#6b9f93; font-size:30px; }
    .state-card h2 { margin:0; color:#36545c; font-size:15px; }
    .state-card p { margin:0; font-size:11px; }
    .loader { width:15px; height:15px; border:2px solid #d8eae4; border-top-color:#087e75; border-radius:50%; animation:spin .7s linear infinite; }
    footer { display:flex; align-items:center; justify-content:center; gap:6px; margin-top:27px; color:#98a5a5; font-size:10px; }
    footer .material-icons { color:#55978a; font-size:14px; }
    @keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:760px) { .filters { grid-template-columns:1fr 1fr; }.doctor-list { grid-template-columns:1fr; } }
    @media(max-width:480px) { .page { padding:0 14px 26px; }.topbar { min-height:62px; }.brand { font-size:13px; }.back-link { font-size:10px; }.intro { padding:25px 0 17px; }h1 { font-size:25px; }.intro p { font-size:11px; }.filters { gap:7px; }.filters input,.filters select { height:40px; font-size:10px; }.doctor-card { padding:15px; }.doctor-heading { gap:8px; }.avatar { width:42px; height:42px; border-radius:13px; }.identity h2 { font-size:13px; }.availability-badge { max-width:110px; white-space:normal; text-align:center; }.actions a { padding:0 8px; font-size:10px; } }
    @media(prefers-reduced-motion:reduce) { *,*::before,*::after { animation-duration:.01ms!important; } }
  `]
})
export class DoctorDirectoryComponent implements OnInit {
  readonly doctors = signal<DoctorProfile[]>([]);
  readonly availability = signal<Record<string, DoctorAvailabilityState>>({});
  readonly photoErrors = signal<Set<string>>(new Set());
  readonly loading = signal(true);
  readonly error = signal('');
  readonly search = signal('');
  readonly specialtyFilter = signal('');
  readonly cityFilter = signal('');
  readonly availabilityFilter = signal<AvailabilityFilter>('all');

  readonly specialties = computed(() => [...new Set(
    this.doctors().map(doctor => doctor.specialty?.trim()).filter((value): value is string => !!value)
  )].sort((a, b) => a.localeCompare(b, 'fr')));

  readonly filteredDoctors = computed(() => {
    const term = this.search().trim().toLocaleLowerCase('fr');
    const specialty = this.specialtyFilter().trim().toLocaleLowerCase('fr');
    const city = this.cityFilter().trim().toLocaleLowerCase('fr');
    const filter = this.availabilityFilter();
    return this.doctors().filter(doctor => {
      const state = this.availability()[doctor.id];
      const fullName = `${doctor.firstName ?? ''} ${doctor.lastName ?? ''}`.trim().toLocaleLowerCase('fr');
      if (term && !fullName.includes(term) && !(doctor.specialty ?? '').toLocaleLowerCase('fr').includes(term)) {
        return false;
      }
      if (specialty && (doctor.specialty ?? '').toLocaleLowerCase('fr') !== specialty) {
        return false;
      }
      if (city && !(doctor.officeCity ?? '').toLocaleLowerCase('fr').includes(city)) {
        return false;
      }
      if (filter !== 'all' && state && !state.failed && !this.hasAvailabilityInFilter(state.slots, filter)) {
        return false;
      }
      return true;
    }).sort((left, right) => {
      const leftNext = this.availability()[left.id]?.slots[0]?.startsAt ?? '';
      const rightNext = this.availability()[right.id]?.slots[0]?.startsAt ?? '';
      if (!leftNext && !rightNext) return this.doctorName(left).localeCompare(this.doctorName(right), 'fr');
      if (!leftNext) return 1;
      if (!rightNext) return -1;
      return leftNext.localeCompare(rightNext);
    });
  });

  constructor(private readonly api: TelemedApiService) {}

  ngOnInit(): void {
    this.api.patientDoctors().subscribe({
      next: doctors => {
        this.doctors.set(doctors);
        if (doctors.length === 0) {
          this.loading.set(false);
          return;
        }
        const from = this.dateKey(new Date());
        const rangeEnd = new Date();
        rangeEnd.setDate(rangeEnd.getDate() + 30);
        const to = this.dateKey(rangeEnd);
        forkJoin(doctors.map(doctor => this.api.availableSlots(doctor.id, from, to).pipe(
          map(slots => ({ doctorId: doctor.id, state: { slots: this.sorted(slots), failed: false } })),
          catchError(() => of({ doctorId: doctor.id, state: { slots: [], failed: true } }))
        ))).subscribe(results => {
          this.availability.set(Object.fromEntries(results.map(result => [result.doctorId, result.state])));
          this.loading.set(false);
        });
      },
      error: () => {
        this.error.set('Impossible de charger l’annuaire médical. Veuillez réessayer.');
        this.loading.set(false);
      }
    });
  }

  availabilityState(doctorId: string): DoctorAvailabilityState | undefined {
    return this.availability()[doctorId];
  }

  doctorName(doctor: DoctorProfile): string {
    const name = [doctor.firstName, doctor.lastName].filter(value => !!value?.trim()).join(' ');
    return name ? `Dr ${name}` : 'Nom à renseigner';
  }

  initials(doctor: DoctorProfile): string {
    const initials = `${doctor.firstName?.trim().charAt(0) ?? ''}${doctor.lastName?.trim().charAt(0) ?? ''}`;
    return initials.toLocaleUpperCase('fr') || 'M';
  }

  location(doctor: DoctorProfile): string {
    return [doctor.officeAddress, doctor.officeCity].filter(value => !!value?.trim()).join(', ')
      || 'Lieu de consultation non renseigné';
  }

  markPhotoError(doctorId: string): void {
    this.photoErrors.update(errors => new Set(errors).add(doctorId));
  }

  private hasAvailabilityInFilter(slots: AppointmentSlot[], filter: Exclude<AvailabilityFilter, 'all'>): boolean {
    const today = this.dateKey(new Date());
    const end = new Date();
    const daysUntilSunday = (7 - end.getDay()) % 7;
    end.setDate(end.getDate() + (filter === 'today' ? 0 : daysUntilSunday));
    const lastDay = this.dateKey(end);
    return slots.some(slot => {
      const day = slot.startsAt.slice(0, 10);
      return day >= today && day <= lastDay;
    });
  }

  private sorted(slots: AppointmentSlot[]): AppointmentSlot[] {
    return [...slots].sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  }

  private dateKey(date: Date): string {
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }
}
