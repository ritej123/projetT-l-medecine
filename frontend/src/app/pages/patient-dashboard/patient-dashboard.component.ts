import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Appointment, DoctorProfile, Prescription, TelemedApiService } from '../../core/telemed-api.service';

@Component({
  selector: 'app-patient-dashboard',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <div class="workspace">
      <aside class="sidebar">
        <a class="brand" routerLink="/"><span class="logo">+</span><span>ClairSanté<small>ESPACE PATIENT</small></span></a>
        <nav aria-label="Navigation principale">
          <a class="active" routerLink="/patient"><span class="material-icons">grid_view</span>Vue d'ensemble</a>
          <a routerLink="/reservation"><span class="material-icons">calendar_month</span>Prendre rendez-vous</a>
          <a href="#prescriptions"><span class="material-icons">description</span>Mes ordonnances</a>
        </nav>
        <div class="sidebar-help"><span class="material-icons">shield</span><strong>Votre espace est privé</strong><p>Vos informations de santé restent protégées.</p></div>
        <button class="logout" type="button" (click)="signOut()"><span class="material-icons">logout</span>Se déconnecter</button>
      </aside>
      <main class="content">
        <header class="topbar"><div class="breadcrumb">Mon espace <span>/</span> Tableau de bord</div><div class="user-pill"><span class="avatar">{{ initials }}</span><span>{{ email }}</span></div></header>
        <section class="welcome">
          <div><span class="eyebrow">VOTRE SANTÉ, EN TOUTE SIMPLICITÉ</span><h1>Bonjour, {{ firstName }} <span>✦</span></h1><p>Retrouvez vos rendez-vous et documents médicaux au même endroit.</p></div>
          <a class="primary" routerLink="/reservation"><span class="material-icons">add</span>Prendre rendez-vous</a>
        </section>

        @if (error()) { <div class="notice" role="alert">{{ error() }}</div> }
        <section class="summary-grid" aria-label="Résumé de votre espace">
          <article class="summary-card"><div class="summary-icon mint"><span class="material-icons">event_available</span></div><p>Prochains rendez-vous</p><strong>{{ upcoming().length }}</strong><small>à venir</small></article>
          <article class="summary-card"><div class="summary-icon blue"><span class="material-icons">description</span></div><p>Ordonnances disponibles</p><strong>{{ prescriptions().length }}</strong><small>validées par votre médecin</small></article>
          <article class="summary-card"><div class="summary-icon lilac"><span class="material-icons">favorite</span></div><p>Suivi santé</p><strong class="healthy">À jour</strong><small>toutes vos informations au même endroit</small></article>
        </section>

        <section class="section-block">
          <div class="section-heading"><div><span class="eyebrow">À NE PAS MANQUER</span><h2>Vos prochains rendez-vous</h2></div><a routerLink="/reservation">Voir les disponibilités <span>→</span></a></div>
          @if (loading()) {
            <div class="empty"><span class="loader"></span><p>Chargement de vos rendez-vous…</p></div>
          } @else if (upcoming().length === 0) {
            <div class="empty"><span class="empty-icon material-icons">event_note</span><h3>Rien de prévu pour le moment</h3><p>Choisissez un créneau avec un professionnel de santé.</p><a class="secondary" routerLink="/reservation">Trouver un rendez-vous</a></div>
          } @else {
            <div class="appointment-list">
              @for (appointment of upcoming(); track appointment.id) {
                <article class="appointment-card">
                  <div class="date-tile"><strong>{{ appointment.scheduledAt | date:'dd' }}</strong><span>{{ appointment.scheduledAt | date:'MMM' }}</span></div>
                  <div class="appointment-info"><h3>Consultation {{ specialtyFor(appointment.doctorId) }}</h3><p><span class="material-icons">schedule</span>{{ appointment.scheduledAt | date:'EEEE d MMMM, HH:mm':'':'fr' }} · {{ appointment.durationMinutes }} min</p></div>
                  <span class="status" [class.confirmed]="appointment.status === 'CONFIRMED'">{{ appointment.status === 'CONFIRMED' ? 'Confirmé' : 'En attente' }}</span>
                  @if (appointment.status === 'CONFIRMED') {
                    <a class="icon-action" [routerLink]="['/teleconsultation', appointment.id]" aria-label="Ouvrir la téléconsultation"><span class="material-icons">videocam</span></a>
                  }
                  <button type="button" class="cancel-action" (click)="cancelAppointment(appointment)"
                          [disabled]="cancelingId() !== null">
                    {{ cancelingId() === appointment.id ? 'Annulation…' : 'Annuler' }}
                  </button>
                </article>
              }
            </div>
          }
        </section>

        <section id="prescriptions" class="section-block prescriptions">
          <div class="section-heading"><div><span class="eyebrow">DOCUMENTS MÉDICAUX</span><h2>Mes ordonnances</h2></div><span class="doc-count">{{ prescriptions().length }} document{{ prescriptions().length === 1 ? '' : 's' }}</span></div>
          @if (prescriptions().length === 0) {
            <div class="empty compact"><span class="empty-icon material-icons">folder_open</span><h3>Aucune ordonnance disponible</h3><p>Les ordonnances validées par votre médecin s’afficheront ici.</p></div>
          } @else {
            <div class="prescription-list">
              @for (prescription of prescriptions(); track prescription.id) {
                <article class="prescription-card">
                  <span class="document-icon material-icons">description</span>
                  <div><h3>Ordonnance médicale</h3><p>Émise le {{ prescription.date | date:'d MMMM y':'':'fr' }}</p></div>
                  <button type="button" class="download" (click)="downloadPrescription(prescription)">Télécharger PDF <span class="material-icons">download</span></button>
                </article>
              }
            </div>
          }
        </section>
        <footer>© ClairSanté · Prenez soin de vous, sereinement.</footer>
      </main>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f5f8f7; color:#18343d; }
    .workspace { display:grid; min-height:100vh; grid-template-columns:248px minmax(0,1fr); }
    .sidebar { position:sticky; top:0; display:flex; height:100vh; flex-direction:column; padding:28px 17px 18px; border-right:1px solid #e5ecea; background:#fff; }
    .brand { display:flex; align-items:center; gap:11px; padding:0 9px; color:#183d49; font-weight:800; text-decoration:none; }
    .logo { display:grid; width:38px; height:38px; place-items:center; border-radius:13px; background:#087e75; color:white; font-size:27px; }
    .brand small { display:block; margin-top:4px; color:#94a3a4; font-size:9px; letter-spacing:1.4px; }
    nav { display:grid; gap:6px; margin-top:50px; }
    nav a,.logout { display:flex; align-items:center; gap:12px; padding:12px; border:0; border-radius:11px; background:transparent; color:#718287; font:inherit; font-size:13px; text-align:left; text-decoration:none; cursor:pointer; }
    nav a .material-icons,.logout .material-icons { font-size:19px; }
    nav a.active { background:#eaf5f1; color:#087e75; font-weight:700; }
    .sidebar-help { margin-top:auto; border:1px solid #e5efed; border-radius:15px; padding:15px; background:#f7fbf9; }
    .sidebar-help .material-icons { color:#168879; }
    .sidebar-help strong { display:block; margin-top:9px; font-size:12px; }
    .sidebar-help p { margin:5px 0 0; color:#7e8e91; font-size:11px; line-height:1.6; }
    .logout { margin-top:15px; }
    .content { width:min(100%,1250px); margin:0 auto; padding:0 38px 28px; }
    .topbar { display:flex; height:72px; align-items:center; justify-content:space-between; border-bottom:1px solid #e7edeb; }
    .breadcrumb { color:#839195; font-size:12px; }.breadcrumb span { margin:0 8px; color:#c1ccca; }
    .user-pill { display:flex; align-items:center; gap:9px; color:#52676d; font-size:12px; }
    .avatar { display:grid; width:31px; height:31px; place-items:center; border-radius:50%; background:#e1f0eb; color:#13766d; font-size:10px; font-weight:750; }
    .welcome { display:flex; align-items:end; justify-content:space-between; gap:20px; margin:34px 0 25px; }
    .eyebrow { color:#77938e; font-size:9px; font-weight:750; letter-spacing:1.3px; }
    h1 { margin:9px 0 6px; color:#183b46; font-size:30px; letter-spacing:-.8px; } h1 span { color:#edbd57; font-size:20px; }
    .welcome p { margin:0; color:#7b8b8f; font-size:13px; }
    .primary,.secondary { display:inline-flex; align-items:center; justify-content:center; gap:7px; border:0; border-radius:11px; padding:12px 16px; background:#087e75; color:white; font:inherit; font-size:12px; font-weight:700; text-decoration:none; cursor:pointer; transition:transform .16s,box-shadow .16s; }
    .primary:hover,.secondary:hover { transform:translateY(-2px); box-shadow:0 9px 20px #087e7528; }.primary .material-icons { font-size:17px; }
    .summary-grid { display:grid; grid-template-columns:repeat(3,minmax(0,1fr)); gap:15px; }
    .summary-card { min-height:134px; border:1px solid #e8eeec; border-radius:17px; padding:17px; background:white; box-shadow:0 4px 18px #264a4008; }
    .summary-icon { display:grid; width:34px; height:34px; place-items:center; border-radius:11px; }.summary-icon .material-icons { font-size:18px; }
    .mint { background:#e6f5ef; color:#11816f; }.blue { background:#eaf1fc; color:#5279ba; }.lilac { background:#f1ecfb; color:#856bb5; }
    .summary-card p { margin:14px 0 3px; color:#73858a; font-size:11px; }.summary-card strong { margin-right:6px; color:#183d47; font-size:24px; }.summary-card small { color:#98a5a7; font-size:10px; }.summary-card .healthy { color:#13816e; font-size:20px; }
    .section-block { margin-top:31px; }.section-heading { display:flex; align-items:end; justify-content:space-between; gap:10px; margin-bottom:14px; }
    h2 { margin:6px 0 0; color:#23434c; font-size:18px; letter-spacing:-.3px; }.section-heading>a { color:#0a8075; font-size:11px; font-weight:700; text-decoration:none; }
    .appointment-list,.prescription-list { display:grid; gap:9px; }
    .appointment-card,.prescription-card { display:flex; align-items:center; gap:15px; border:1px solid #e7eeec; border-radius:15px; padding:13px 15px; background:white; }
    .date-tile { display:grid; width:45px; height:49px; flex:0 0 auto; align-content:center; justify-items:center; border-radius:11px; background:#eff7f4; color:#177d70; }.date-tile strong { font-size:18px; line-height:1.1; }.date-tile span { font-size:9px; text-transform:uppercase; }
    .appointment-info { min-width:0; flex:1; }.appointment-info h3,.prescription-card h3 { margin:0 0 5px; color:#2a4851; font-size:13px; }.appointment-info p,.prescription-card p { display:flex; align-items:center; gap:4px; margin:0; color:#829094; font-size:10px; }.appointment-info p .material-icons { font-size:13px; }
    .status { border-radius:20px; padding:6px 9px; background:#fff5e2; color:#a47929; font-size:9px; font-weight:700; }.status.confirmed { background:#e5f5ed; color:#287d5c; }
    .icon-action { display:grid; width:34px; height:34px; place-items:center; border-radius:10px; background:#eaf5f1; color:#087e75; }.icon-action .material-icons { font-size:18px; }
    .cancel-action { border:1px solid #f0dddd; border-radius:9px; padding:8px 10px; background:#fff; color:#a44d48; font:inherit; font-size:10px; font-weight:700; cursor:pointer; }.cancel-action:disabled { opacity:.55; cursor:wait; }
    .empty { display:grid; min-height:180px; justify-items:center; align-content:center; border:1px dashed #d9e5e2; border-radius:16px; background:#fff; text-align:center; }.empty p { margin:7px 0 15px; color:#869497; font-size:12px; }.empty h3 { margin:9px 0 0; color:#39565e; font-size:14px; }.empty-icon { color:#5c9a8c; font-size:30px; }.empty.compact { min-height:145px; }.secondary { padding:9px 13px; font-size:11px; }
    .loader { width:20px; height:20px; border:2px solid #d5e9e3; border-top-color:#087e75; border-radius:50%; animation:spin .7s linear infinite; }.notice { border-radius:10px; padding:12px; background:#fff1ee; color:#9a463e; font-size:12px; }
    .doc-count { color:#8a989a; font-size:10px; }.document-icon { display:grid; width:38px; height:38px; place-items:center; border-radius:11px; background:#edf2fc; color:#6685bd; }
    .prescription-card>div { flex:1; }.download { display:flex; align-items:center; gap:5px; border:0; background:transparent; color:#087e75; font:inherit; font-size:10px; font-weight:700; cursor:pointer; }.download .material-icons { font-size:16px; }
    footer { margin-top:38px; border-top:1px solid #e7edeb; padding:17px 0; color:#99a4a5; font-size:10px; }
    @keyframes spin { to { transform:rotate(360deg); } }
    @media(max-width:900px) { .workspace { grid-template-columns:200px 1fr; }.content { padding:0 22px 25px; }.summary-grid { gap:8px; }.summary-card { padding:13px; } }
    @media(max-width:680px) { .workspace { display:block; }.sidebar { position:static; height:auto; flex-direction:row; align-items:center; justify-content:space-between; padding:12px 14px; border-right:0; border-bottom:1px solid #e5ecea; }.brand { font-size:13px; }.brand small,.sidebar-help { display:none; }nav { display:flex; gap:2px; margin:0; }nav a { padding:9px; font-size:0; }nav a .material-icons { font-size:20px; }.logout { margin:0; padding:9px; font-size:0; }.logout .material-icons { font-size:20px; }.content { padding:0 15px 22px; }.topbar { height:56px; }.breadcrumb { font-size:10px; }.user-pill { font-size:0; }.welcome { align-items:flex-start; flex-direction:column; margin:24px 0 18px; }h1 { font-size:26px; }.summary-grid { grid-template-columns:1fr; }.summary-card { display:grid; min-height:80px; grid-template-columns:40px 1fr auto; align-items:center; column-gap:10px; }.summary-icon { grid-row:span 2; }.summary-card p { margin:0; }.summary-card strong { grid-column:3; grid-row:1; }.summary-card small { grid-column:2 / 4; }.section-heading { align-items:start; flex-direction:column; }.appointment-card { gap:9px; padding:10px; }.appointment-info h3 { font-size:11px; }.appointment-info p { display:block; line-height:1.6; }.status { padding:5px 7px; }.prescription-card { flex-wrap:wrap; gap:9px; }.download { margin-left:auto; } }
    @media(prefers-reduced-motion:reduce) { *,*::before,*::after { animation-duration:.01ms!important; transition-duration:.01ms!important; } }
  `]
})
export class PatientDashboardComponent implements OnInit {
  readonly loading = signal(true);
  readonly error = signal('');
  readonly appointments = signal<Appointment[]>([]);
  readonly doctors = signal<DoctorProfile[]>([]);
  readonly prescriptions = signal<Prescription[]>([]);
  readonly cancelingId = signal<string | null>(null);

  constructor(private readonly api: TelemedApiService, private readonly auth: AuthService) {}

  get email(): string { return this.auth.claims()?.email ?? ''; }
  get firstName(): string { return this.email.split('@')[0]?.split(/[._-]/)[0] ?? 'bonjour'; }
  get initials(): string { return this.email.slice(0, 1).toUpperCase(); }

  upcoming(): Appointment[] {
    return this.appointments()
      .filter(item => item.status === 'PENDING' || item.status === 'CONFIRMED')
      .filter(item => new Date(item.scheduledAt).getTime() >= Date.now())
      .sort((a, b) => a.scheduledAt.localeCompare(b.scheduledAt));
  }

  specialtyFor(doctorId: string): string {
    const specialty = this.doctors().find(doctor => doctor.id === doctorId)?.specialty;
    return specialty
      ? `avec un spécialiste en ${specialty}`
      : 'médicale';
  }

  ngOnInit(): void {
    this.api.patientAppointments().subscribe({
      next: appointments => { this.appointments.set(appointments); this.loading.set(false); },
      error: () => { this.loading.set(false); this.error.set('Vos rendez-vous ne sont pas disponibles pour le moment.'); }
    });
    this.api.patientDoctors().subscribe({
      next: doctors => this.doctors.set(doctors),
      error: () => this.error.set('L’annuaire médical est momentanément indisponible.')
    });
    this.api.patientPrescriptions().subscribe({
      next: prescriptions => this.prescriptions.set(prescriptions),
      error: () => this.error.set('Vos ordonnances ne sont pas disponibles pour le moment.')
    });
  }

  downloadPrescription(prescription: Prescription): void {
    this.api.prescriptionPdf(prescription.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `ordonnance-${prescription.id}.pdf`;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: () => this.error.set('Le téléchargement du document a échoué.')
    });
  }

  cancelAppointment(appointment: Appointment): void {
    if (this.cancelingId() || !window.confirm('Voulez-vous vraiment annuler ce rendez-vous ?')) {
      return;
    }
    this.cancelingId.set(appointment.id);
    this.api.cancelAppointment(appointment.id).subscribe({
      next: updated => {
        this.appointments.update(items => items.map(item => item.id === updated.id ? updated : item));
        this.cancelingId.set(null);
      },
      error: () => {
        this.error.set('L’annulation du rendez-vous a échoué. Veuillez réessayer.');
        this.cancelingId.set(null);
      }
    });
  }

  signOut(): void {
    this.auth.logout();
    window.location.assign('/');
  }
}
