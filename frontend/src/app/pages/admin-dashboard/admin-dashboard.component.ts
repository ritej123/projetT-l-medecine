import { Component, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import {
  AdminAppointment,
  AdminAuditLog,
  AdminDashboardStats,
  AdminDoctor,
  AdminPatient,
  PageResponse,
  TelemedApiService
} from '../../core/telemed-api.service';

type AdminSection = 'dashboard' | 'patients' | 'doctors' | 'pending' | 'appointments' | 'audit' | 'profile' | 'settings';

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [DatePipe, FormsModule],
  template: `
    <div class="admin-shell">
      <aside class="sidebar">
        <a class="brand" href="/"><span class="logo">+</span><span>ClairSanté<small>ADMINISTRATION</small></span></a>
        <nav aria-label="Navigation administrateur">
          <button [class.active]="section() === 'dashboard'" (click)="openSection('dashboard')"><span class="material-icons">grid_view</span>Tableau de bord</button>
          <button [class.active]="section() === 'patients'" (click)="openSection('patients')"><span class="material-icons">groups</span>Patients</button>
          <button [class.active]="section() === 'doctors'" (click)="openSection('doctors')"><span class="material-icons">medical_services</span>Médecins</button>
          <button [class.active]="section() === 'pending'" (click)="openSection('pending')"><span class="material-icons">verified_user</span>Validation médecins @if (stats()?.pendingDoctors) { <span class="badge">{{ stats()?.pendingDoctors }}</span> }</button>
          <button [class.active]="section() === 'appointments'" (click)="openSection('appointments')"><span class="material-icons">calendar_month</span>Rendez-vous</button>
          <button [class.active]="section() === 'audit'" (click)="openSection('audit')"><span class="material-icons">history</span>Journal d’activité</button>
        </nav>
        <div class="sidebar-bottom">
          <button [class.active]="section() === 'settings'" (click)="openSection('settings')"><span class="material-icons">settings</span>Paramètres</button>
          <button [class.active]="section() === 'profile'" (click)="openSection('profile')"><span class="material-icons">account_circle</span>Mon profil</button>
          <button class="logout" (click)="signOut()"><span class="material-icons">logout</span>Déconnexion</button>
        </div>
      </aside>

      <main class="main">
        <header class="topbar">
          <div><span class="eyebrow">ESPACE ADMINISTRATEUR</span><h1>{{ sectionTitle() }}</h1></div>
          <div class="admin-identity">
            <button type="button" class="notification" aria-label="Demandes médecins en attente" (click)="openSection('pending')">
              <span class="material-icons">notifications</span>
              @if (stats()?.pendingDoctors) { <i>{{ stats()?.pendingDoctors }}</i> }
            </button>
            <span class="avatar">A</span><span class="admin-email">{{ email }}</span>
          </div>
        </header>

        @if (error()) { <div class="notice error" role="alert">{{ error() }}</div> }
        @if (message()) { <div class="notice success" role="status">{{ message() }}</div> }

        @if (section() === 'dashboard') {
          @if (loadingStats()) {
            <div class="loading"><span class="loader"></span>Chargement des indicateurs réels…</div>
          } @else if (stats()) {
            <section class="stats-grid" aria-label="Indicateurs de la plateforme">
              <article class="stat-card"><span class="stat-icon teal material-icons">groups</span><div><small>Patients inscrits</small><strong>{{ stats()!.totalPatients }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon blue material-icons">medical_services</span><div><small>Médecins</small><strong>{{ stats()!.totalDoctors }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon amber material-icons">pending_actions</span><div><small>En attente de validation</small><strong>{{ stats()!.pendingDoctors }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon green material-icons">verified</span><div><small>Médecins actifs vérifiés</small><strong>{{ stats()!.verifiedActiveDoctors }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon red material-icons">person_off</span><div><small>Comptes suspendus</small><strong>{{ stats()!.suspendedAccounts }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon purple material-icons">event</span><div><small>Total rendez-vous</small><strong>{{ stats()!.totalAppointments }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon teal material-icons">today</span><div><small>Aujourd’hui</small><strong>{{ stats()!.appointmentsToday }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon blue material-icons">upcoming</span><div><small>À venir</small><strong>{{ stats()!.upcomingAppointments }}</strong></div></article>
              <article class="stat-card"><span class="stat-icon red material-icons">event_busy</span><div><small>Annulés</small><strong>{{ stats()!.cancelledAppointments }}</strong></div></article>
            </section>

            <section class="chart-grid">
              <article class="panel chart-panel">
                <div class="panel-heading"><div><span class="eyebrow">TENDANCE SUR 6 MOIS</span><h2>Inscriptions mensuelles</h2></div><span class="material-icons">trending_up</span></div>
                <div class="bar-chart" role="img" aria-label="Nombre de créations de compte par mois">
                  @for (item of stats()!.monthlyActivity; track item.month) {
                    <div class="bar-column"><strong>{{ item.registrations }}</strong><div class="bar-rail"><i class="bar registrations" [style.height.%]="barHeight(item.registrations, maxRegistrations())"></i></div><small>{{ monthLabel(item.month) }}</small></div>
                  }
                </div>
              </article>
              <article class="panel chart-panel">
                <div class="panel-heading"><div><span class="eyebrow">TENDANCE SUR 6 MOIS</span><h2>Rendez-vous mensuels</h2></div><span class="material-icons">monitoring</span></div>
                <div class="bar-chart" role="img" aria-label="Nombre de rendez-vous par mois">
                  @for (item of stats()!.monthlyActivity; track item.month) {
                    <div class="bar-column"><strong>{{ item.appointments }}</strong><div class="bar-rail"><i class="bar appointments" [style.height.%]="barHeight(item.appointments, maxAppointments())"></i></div><small>{{ monthLabel(item.month) }}</small></div>
                  }
                </div>
              </article>
            </section>

            <section class="panel">
              <div class="panel-heading"><div><span class="eyebrow">À TRAITER</span><h2>Demandes médecins récentes</h2></div><button class="text-button" (click)="openSection('pending')">Toutes les demandes →</button></div>
              @if (stats()!.recentDoctorApplications.length === 0) {
                <p class="empty">Aucune demande de validation en attente.</p>
              } @else {
                <div class="table-wrap"><table><thead><tr><th>Adresse e-mail</th><th>Spécialité</th><th>Reçue le</th><th>Action</th></tr></thead><tbody>
                  @for (doctor of stats()!.recentDoctorApplications; track doctor.id) {
                    <tr><td>{{ doctor.email }}</td><td>{{ doctor.specialty }}</td><td>{{ doctor.createdAt | date:'d MMM y, HH:mm':'':'fr' }}</td><td><button class="small-action" (click)="verifyDoctor(doctor)">Examiner</button></td></tr>
                  }
                </tbody></table></div>
              }
            </section>

            <section class="panel">
              <div class="panel-heading"><div><span class="eyebrow">ACTIVITÉ RÉCENTE</span><h2>Derniers rendez-vous</h2></div><button class="text-button" (click)="openSection('appointments')">Voir tout →</button></div>
              @if (stats()!.recentAppointments.length === 0) {
                <p class="empty">Aucun rendez-vous enregistré.</p>
              } @else {
                <div class="table-wrap"><table><thead><tr><th>Date</th><th>Patient</th><th>Médecin</th><th>Spécialité</th><th>Statut</th></tr></thead><tbody>
                  @for (appointment of stats()!.recentAppointments; track appointment.id) {
                    <tr><td>{{ appointment.scheduledAt | date:'d MMM y, HH:mm':'':'fr' }}</td><td>{{ appointment.patientEmail }}</td><td>{{ appointment.doctorEmail }}</td><td>{{ appointment.specialty }}</td><td><span class="status" [class]="statusClass(appointment.status)">{{ statusLabel(appointment.status) }}</span></td></tr>
                  }
                </tbody></table></div>
              }
            </section>
          }
        }

        @if (section() === 'doctors' || section() === 'pending') {
          <section class="panel">
            <div class="filters">
              <input aria-label="Recherche médecin" placeholder="Rechercher par e-mail ou spécialité" [(ngModel)]="search">
              @if (section() === 'doctors') {
                <select aria-label="Statut de vérification" [(ngModel)]="doctorStatus"><option value="">Tous les statuts</option><option value="PENDING">En attente</option><option value="VERIFIED">Vérifié</option><option value="REJECTED">Refusé</option></select>
                <select aria-label="Statut du compte" [(ngModel)]="enabledFilter"><option value="">Tous les comptes</option><option value="true">Actifs</option><option value="false">Suspendus</option></select>
              }
              <input aria-label="Date de début" type="date" [(ngModel)]="fromDate">
              <input aria-label="Date de fin" type="date" [(ngModel)]="toDate">
              <button class="primary-button" (click)="loadDoctors(0)">Rechercher</button>
            </div>
            @if (loadingList()) { <div class="loading"><span class="loader"></span>Chargement des médecins…</div> }
            @else if (doctors()?.content?.length) {
              <div class="table-wrap"><table><thead><tr><th>Médecin</th><th>Spécialité</th><th>Vérification</th><th>Compte</th><th>Inscription</th><th>Actions</th></tr></thead><tbody>
                @for (doctor of doctors()!.content; track doctor.id) {
                  <tr><td><strong>{{ doctor.email }}</strong>@if (doctor.bio) { <small class="subtext">{{ doctor.bio }}</small> }</td><td>{{ doctor.specialty }}</td><td><span class="status" [class]="verificationClass(doctor.verificationStatus)">{{ verificationLabel(doctor.verificationStatus) }}</span></td><td>{{ doctor.enabled ? 'Actif' : 'Suspendu' }}</td><td>{{ doctor.createdAt | date:'d MMM y':'':'fr' }}</td><td class="actions">
                    @if (doctor.verificationStatus === 'PENDING') { <button class="small-action" [disabled]="busyId() === doctor.id" (click)="verifyDoctor(doctor)">Valider</button><button class="small-action danger" [disabled]="busyId() === doctor.id" (click)="rejectDoctor(doctor)">Refuser</button> }
                    @if (doctor.verificationStatus === 'VERIFIED') { <button class="small-action" [disabled]="busyId() === doctor.id" (click)="setDoctorEnabled(doctor)">{{ doctor.enabled ? 'Suspendre' : 'Réactiver' }}</button> }
                  </td></tr>
                }
              </tbody></table></div>
              <div class="pagination"><span>{{ doctors()!.totalElements }} médecin(s) · page {{ doctors()!.number + 1 }} / {{ maxPage(doctors()!.totalPages) }}</span><button [disabled]="doctors()!.number === 0" (click)="loadDoctors(doctors()!.number - 1)">Précédent</button><button [disabled]="doctors()!.number + 1 >= doctors()!.totalPages" (click)="loadDoctors(doctors()!.number + 1)">Suivant</button></div>
            } @else { <p class="empty">Aucun médecin ne correspond à ces critères.</p> }
          </section>
        }

        @if (section() === 'patients') {
          <section class="panel">
            <div class="filters">
              <input aria-label="Recherche patient" placeholder="Rechercher par adresse e-mail" [(ngModel)]="search">
              <select aria-label="Statut patient" [(ngModel)]="enabledFilter"><option value="">Tous les comptes</option><option value="true">Actifs</option><option value="false">Suspendus</option></select>
              <input aria-label="Date de début" type="date" [(ngModel)]="fromDate"><input aria-label="Date de fin" type="date" [(ngModel)]="toDate">
              <button class="primary-button" (click)="loadPatients(0)">Rechercher</button>
            </div>
            <p class="privacy-note"><span class="material-icons">lock</span>Cette vue ne donne pas accès aux dossiers médicaux ni aux ordonnances.</p>
            @if (loadingList()) { <div class="loading"><span class="loader"></span>Chargement des patients…</div> }
            @else if (patients()?.content?.length) {
              <div class="table-wrap"><table><thead><tr><th>Adresse e-mail</th><th>Statut</th><th>Inscription</th><th>Action</th></tr></thead><tbody>
                @for (patient of patients()!.content; track patient.id) {
                  <tr><td>{{ patient.email }}</td><td><span class="status" [class]="patient.enabled ? 'verified' : 'rejected'">{{ patient.enabled ? 'Actif' : 'Suspendu' }}</span></td><td>{{ patient.createdAt | date:'d MMM y':'':'fr' }}</td><td><button class="small-action" [disabled]="busyId() === patient.id" (click)="setPatientEnabled(patient)">{{ patient.enabled ? 'Suspendre' : 'Réactiver' }}</button></td></tr>
                }
              </tbody></table></div>
              <div class="pagination"><span>{{ patients()!.totalElements }} patient(s) · page {{ patients()!.number + 1 }} / {{ maxPage(patients()!.totalPages) }}</span><button [disabled]="patients()!.number === 0" (click)="loadPatients(patients()!.number - 1)">Précédent</button><button [disabled]="patients()!.number + 1 >= patients()!.totalPages" (click)="loadPatients(patients()!.number + 1)">Suivant</button></div>
            } @else { <p class="empty">Aucun patient ne correspond à ces critères.</p> }
          </section>
        }

        @if (section() === 'appointments') {
          <section class="panel">
            <div class="filters">
              <input aria-label="Recherche rendez-vous" placeholder="E-mail patient ou médecin" [(ngModel)]="search">
              <select aria-label="Statut rendez-vous" [(ngModel)]="appointmentStatus"><option value="">Tous les statuts</option><option value="PENDING">En attente</option><option value="CONFIRMED">Confirmé</option><option value="DONE">Terminé</option><option value="CANCELLED">Annulé</option></select>
              <input aria-label="Date de début" type="date" [(ngModel)]="fromDate"><input aria-label="Date de fin" type="date" [(ngModel)]="toDate">
              <button class="primary-button" (click)="loadAppointments(0)">Filtrer</button>
            </div>
            @if (loadingList()) { <div class="loading"><span class="loader"></span>Chargement des rendez-vous…</div> }
            @else if (appointments()?.content?.length) {
              <div class="table-wrap"><table><thead><tr><th>Date</th><th>Patient</th><th>Médecin</th><th>Spécialité</th><th>Durée</th><th>Statut</th></tr></thead><tbody>
                @for (appointment of appointments()!.content; track appointment.id) {
                  <tr><td>{{ appointment.scheduledAt | date:'d MMM y, HH:mm':'':'fr' }}</td><td>{{ appointment.patientEmail }}</td><td>{{ appointment.doctorEmail }}</td><td>{{ appointment.specialty }}</td><td>{{ appointment.durationMinutes }} min</td><td><span class="status" [class]="statusClass(appointment.status)">{{ statusLabel(appointment.status) }}</span></td></tr>
                }
              </tbody></table></div>
              <div class="pagination"><span>{{ appointments()!.totalElements }} rendez-vous · page {{ appointments()!.number + 1 }} / {{ maxPage(appointments()!.totalPages) }}</span><button [disabled]="appointments()!.number === 0" (click)="loadAppointments(appointments()!.number - 1)">Précédent</button><button [disabled]="appointments()!.number + 1 >= appointments()!.totalPages" (click)="loadAppointments(appointments()!.number + 1)">Suivant</button></div>
            } @else { <p class="empty">Aucun rendez-vous ne correspond à ces critères.</p> }
          </section>
        }

        @if (section() === 'audit') {
          <section class="panel">
            <p class="privacy-note"><span class="material-icons">verified_user</span>Le journal contient les actions administratives et des identifiants de cible, sans données médicales, mots de passe ni jetons.</p>
            @if (loadingList()) { <div class="loading"><span class="loader"></span>Chargement du journal…</div> }
            @else if (auditLogs()?.content?.length) {
              <div class="table-wrap"><table><thead><tr><th>Date</th><th>Administrateur</th><th>Action</th><th>Cible</th><th>Résultat</th></tr></thead><tbody>
                @for (entry of auditLogs()!.content; track entry.id) {
                  <tr><td>{{ entry.createdAt | date:'d MMM y, HH:mm:ss':'':'fr' }}</td><td>{{ entry.actorEmail }}</td><td>{{ actionLabel(entry.action) }}</td><td>{{ entry.targetType }} · {{ entry.targetId }}</td><td><span class="status verified">{{ entry.result === 'SUCCESS' ? 'Réussie' : 'Refusée' }}</span></td></tr>
                }
              </tbody></table></div>
              <div class="pagination"><span>{{ auditLogs()!.totalElements }} action(s) · page {{ auditLogs()!.number + 1 }} / {{ maxPage(auditLogs()!.totalPages) }}</span><button [disabled]="auditLogs()!.number === 0" (click)="loadAudit(auditLogs()!.number - 1)">Précédent</button><button [disabled]="auditLogs()!.number + 1 >= auditLogs()!.totalPages" (click)="loadAudit(auditLogs()!.number + 1)">Suivant</button></div>
            } @else { <p class="empty">Aucune action administrative enregistrée.</p> }
          </section>
        }

        @if (section() === 'profile') {
          <section class="panel profile"><span class="avatar large">A</span><span class="eyebrow">COMPTE ADMINISTRATEUR</span><h2>{{ email }}</h2><p>Votre compte dispose du rôle ADMIN. Les opérations sensibles sont enregistrées dans le journal d’activité.</p></section>
        }
        @if (section() === 'settings') {
          <section class="panel"><h2>Paramètres de la plateforme</h2><p class="empty">Aucun paramètre métier administrable n’est encore exposé par le backend. Les secrets et paramètres techniques restent configurés côté serveur.</p></section>
        }
        <footer>ClairSanté · Administration · Données affichées depuis le backend</footer>
      </main>
    </div>
  `,
  styles: [`
    :host{display:block;min-height:100vh;background:#f5f8f7;color:#18343d}.admin-shell{min-height:100vh;display:grid;grid-template-columns:245px minmax(0,1fr)}
    .sidebar{position:sticky;top:0;height:100vh;display:flex;flex-direction:column;padding:24px 14px;background:#fff;border-right:1px solid #e5ecea}.brand{display:flex;align-items:center;gap:10px;padding:0 9px;color:#183d49;font-weight:800;text-decoration:none}.logo{display:grid;width:38px;height:38px;place-items:center;border-radius:13px;background:#087e75;color:#fff;font-size:27px}.brand small{display:block;margin-top:3px;color:#94a3a4;font-size:9px;letter-spacing:1.2px}
    nav,.sidebar-bottom{display:grid;gap:5px;margin-top:36px}nav button,.sidebar-bottom button{display:flex;align-items:center;gap:11px;width:100%;border:0;border-radius:10px;padding:11px;background:transparent;color:#718287;text-align:left;font:inherit;font-size:12px;cursor:pointer}nav button .material-icons,.sidebar-bottom .material-icons{font-size:18px}nav button.active,.sidebar-bottom button.active{background:#eaf5f1;color:#087e75;font-weight:700}.badge{margin-left:auto;border-radius:20px;padding:2px 7px;background:#fae8df;color:#a94735;font-size:10px}.sidebar-bottom{margin-top:auto}.logout{color:#a44d48!important}
    .main{max-width:1500px;width:100%;margin:auto;padding:0 30px 24px}.topbar{min-height:82px;display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e5ecea;margin-bottom:24px}.topbar h1{margin:4px 0 0;font-size:22px;color:#1d4049}.eyebrow{color:#78928d;font-size:9px;font-weight:800;letter-spacing:1.2px}.admin-identity{display:flex;align-items:center;gap:9px;color:#536b70;font-size:12px}.avatar{display:grid;width:34px;height:34px;place-items:center;border-radius:50%;background:#e1f0eb;color:#087e75;font-weight:800}.notification{position:relative;border:0;background:transparent;color:#53726e;cursor:pointer}.notification i{position:absolute;top:-4px;right:-5px;min-width:14px;border-radius:12px;background:#d95747;color:#fff;font-size:9px;font-style:normal}.notice{margin:0 0 14px;border-radius:10px;padding:11px 14px;font-size:12px}.error{background:#fff0ee;color:#9a463e}.success{background:#e5f5ed;color:#28795e}
    .stats-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px}.stat-card{min-height:92px;display:flex;align-items:center;gap:13px;border:1px solid #e6edeb;border-radius:15px;padding:14px;background:#fff}.stat-icon{display:grid;place-items:center;width:38px;height:38px;border-radius:12px;font-size:19px}.teal{background:#e5f4ee;color:#138173}.blue{background:#eaf1fc;color:#5279ba}.amber{background:#fff3dd;color:#a57d31}.green{background:#e5f5ed;color:#287d5c}.red{background:#fff0ee;color:#b34e46}.purple{background:#f1ecfb;color:#856bb5}.stat-card small{display:block;color:#77898d;font-size:10px}.stat-card strong{display:block;margin-top:4px;color:#183d47;font-size:23px}
    .chart-grid{display:grid;grid-template-columns:1fr 1fr;gap:13px;margin:15px 0}.panel{margin:14px 0;border:1px solid #e6edeb;border-radius:16px;padding:17px;background:#fff}.panel-heading{display:flex;align-items:center;justify-content:space-between;gap:10px}.panel-heading h2,.panel h2{margin:5px 0 0;color:#23434c;font-size:16px}.panel-heading>.material-icons{color:#148577}.bar-chart{height:160px;display:grid;grid-template-columns:repeat(6,1fr);align-items:end;gap:10px;margin-top:14px}.bar-column{height:100%;display:flex;flex-direction:column;align-items:center;justify-content:end;gap:5px}.bar-column strong{color:#577075;font-size:10px}.bar-column small{color:#819195;font-size:9px}.bar-rail{height:110px;width:min(28px,80%);display:flex;align-items:end;border-radius:8px 8px 2px 2px;background:#f2f6f5}.bar{display:block;width:100%;min-height:2px;border-radius:8px 8px 2px 2px}.registrations{background:linear-gradient(#46b29c,#087e75)}.appointments{background:linear-gradient(#91b5e8,#5279ba)}
    .text-button{border:0;background:transparent;color:#087e75;font:inherit;font-size:11px;font-weight:700;cursor:pointer}.table-wrap{width:100%;overflow-x:auto;margin-top:13px}table{width:100%;border-collapse:collapse;text-align:left;white-space:nowrap}th{padding:10px 9px;background:#f7faf9;color:#76898d;font-size:9px;font-weight:750}td{padding:11px 9px;border-bottom:1px solid #edf1ef;color:#50676c;font-size:10px}td strong{color:#38575e}tr:last-child td{border-bottom:0}.subtext{display:block;max-width:260px;overflow:hidden;color:#88979a;text-overflow:ellipsis}.small-action,.pagination button{border:1px solid #d7e7e1;border-radius:8px;padding:6px 9px;background:#edf7f3;color:#087e75;font:inherit;font-size:9px;font-weight:700;cursor:pointer}.small-action:disabled{opacity:.5;cursor:wait}.small-action.danger{margin-left:5px;border-color:#f0dddd;background:#fff3f1;color:#a44d48}.status{display:inline-block;border-radius:20px;padding:5px 8px;background:#f1f3f3;color:#68797c;font-size:9px;font-weight:700}.status.verified{background:#e5f5ed;color:#287d5c}.status.pending{background:#fff3dd;color:#a57d31}.status.rejected{background:#fff0ee;color:#a44d48}.status.confirmed{background:#eaf1fc;color:#5279ba}.status.done{background:#e5f5ed;color:#287d5c}.status.cancelled{background:#f1f3f3;color:#68797c}
    .filters{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:11px}.filters input,.filters select{min-height:36px;max-width:240px;border:1px solid #dfe8e5;border-radius:9px;padding:0 9px;background:#fcfefd;color:#496268;font:inherit;font-size:10px}.primary-button{border:0;border-radius:9px;padding:0 13px;background:#087e75;color:#fff;font:inherit;font-size:10px;font-weight:700;cursor:pointer}.pagination{display:flex;align-items:center;justify-content:flex-end;gap:8px;margin-top:14px;color:#819195;font-size:10px}.pagination span{margin-right:auto}.pagination button:disabled{opacity:.45;cursor:not-allowed}.empty{padding:22px 8px;color:#879598;text-align:center;font-size:11px}.loading{display:flex;min-height:140px;align-items:center;justify-content:center;gap:9px;color:#7b8d90;font-size:11px}.loader{width:18px;height:18px;border:2px solid #d5e9e3;border-top-color:#087e75;border-radius:50%;animation:spin .7s linear infinite}.privacy-note{display:flex;align-items:center;gap:7px;color:#7b8d90;font-size:10px}.privacy-note .material-icons{color:#168879;font-size:16px}.profile{text-align:center;padding:40px}.avatar.large{width:56px;height:56px;margin:0 auto 16px;font-size:20px}.profile h2{margin:10px 0}.profile p{max-width:500px;margin:0 auto;color:#7d8d90;font-size:12px;line-height:1.7}footer{padding:16px 0;color:#99a4a5;font-size:9px}
    @keyframes spin{to{transform:rotate(360deg)}}@media(max-width:1000px){.admin-shell{grid-template-columns:205px minmax(0,1fr)}.main{padding:0 18px 20px}.stats-grid{grid-template-columns:repeat(2,minmax(0,1fr))}}@media(max-width:700px){.admin-shell{display:block}.sidebar{position:static;width:auto;height:auto;padding:12px}.brand{margin-bottom:10px}.sidebar nav,.sidebar-bottom{display:flex;overflow-x:auto;gap:3px;margin:0}.sidebar nav button,.sidebar-bottom button{width:auto;min-width:max-content;padding:9px;font-size:10px}.sidebar-bottom{margin-top:5px}.sidebar nav button .material-icons,.sidebar-bottom .material-icons{font-size:16px}.main{padding:0 12px 18px}.topbar{min-height:65px;margin-bottom:15px}.topbar h1{font-size:18px}.admin-email{display:none}.stats-grid{grid-template-columns:1fr 1fr;gap:7px}.stat-card{min-height:77px;gap:8px;padding:9px}.stat-icon{width:31px;height:31px;font-size:16px}.stat-card small{font-size:9px}.stat-card strong{font-size:19px}.chart-grid{grid-template-columns:1fr}.filters>*{flex:1 1 42%;max-width:none!important}.primary-button{min-height:36px}.panel{padding:12px}.pagination{flex-wrap:wrap}.pagination span{flex-basis:100%}}
  `]
})
export class AdminDashboardComponent implements OnInit {
  readonly section = signal<AdminSection>('dashboard');
  readonly stats = signal<AdminDashboardStats | null>(null);
  readonly loadingStats = signal(true);
  readonly loadingList = signal(false);
  readonly error = signal('');
  readonly message = signal('');
  readonly busyId = signal<string | null>(null);
  readonly doctors = signal<PageResponse<AdminDoctor> | null>(null);
  readonly patients = signal<PageResponse<AdminPatient> | null>(null);
  readonly appointments = signal<PageResponse<AdminAppointment> | null>(null);
  readonly auditLogs = signal<PageResponse<AdminAuditLog> | null>(null);

  search = '';
  doctorStatus = '';
  appointmentStatus = '';
  enabledFilter = '';
  fromDate = '';
  toDate = '';

  constructor(private readonly api: TelemedApiService, private readonly auth: AuthService) {}

  get email(): string {
    return this.auth.claims()?.email ?? '';
  }

  ngOnInit(): void {
    this.loadStats();
  }

  sectionTitle(): string {
    const titles: Record<AdminSection, string> = {
      dashboard: 'Vue d’ensemble',
      patients: 'Gestion des patients',
      doctors: 'Gestion des médecins',
      pending: 'Validation des médecins',
      appointments: 'Gestion des rendez-vous',
      audit: 'Journal d’activité',
      profile: 'Mon profil',
      settings: 'Paramètres'
    };
    return titles[this.section()];
  }

  openSection(section: AdminSection): void {
    this.section.set(section);
    this.error.set('');
    this.message.set('');
    this.search = '';
    this.fromDate = '';
    this.toDate = '';
    if (section === 'doctors' || section === 'pending') {
      this.doctorStatus = section === 'pending' ? 'PENDING' : '';
      this.enabledFilter = '';
      this.loadDoctors(0);
    } else if (section === 'patients') {
      this.enabledFilter = '';
      this.loadPatients(0);
    } else if (section === 'appointments') {
      this.appointmentStatus = '';
      this.loadAppointments(0);
    } else if (section === 'audit') {
      this.loadAudit(0);
    } else if (section === 'dashboard' && !this.stats()) {
      this.loadStats();
    }
  }

  loadStats(): void {
    this.loadingStats.set(true);
    this.api.adminDashboardStats().subscribe({
      next: stats => {
        this.stats.set(stats);
        this.loadingStats.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger les indicateurs administratifs.');
        this.loadingStats.set(false);
      }
    });
  }

  loadDoctors(page: number): void {
    this.loadingList.set(true);
    this.api.adminDoctors({
      search: this.search,
      status: this.doctorStatus,
      enabled: this.enabledFilter,
      from: this.fromDate,
      to: this.toDate,
      page,
      size: 20
    }).subscribe({
      next: result => { this.doctors.set(result); this.loadingList.set(false); },
      error: () => { this.error.set('Impossible de charger les médecins.'); this.loadingList.set(false); }
    });
  }

  loadPatients(page: number): void {
    this.loadingList.set(true);
    this.api.adminPatients({
      search: this.search,
      enabled: this.enabledFilter,
      from: this.fromDate,
      to: this.toDate,
      page,
      size: 20
    }).subscribe({
      next: result => { this.patients.set(result); this.loadingList.set(false); },
      error: () => { this.error.set('Impossible de charger les patients.'); this.loadingList.set(false); }
    });
  }

  loadAppointments(page: number): void {
    this.loadingList.set(true);
    this.api.adminAppointments({
      search: this.search,
      status: this.appointmentStatus,
      from: this.fromDate,
      to: this.toDate,
      page,
      size: 20
    }).subscribe({
      next: result => { this.appointments.set(result); this.loadingList.set(false); },
      error: () => { this.error.set('Impossible de charger les rendez-vous.'); this.loadingList.set(false); }
    });
  }

  loadAudit(page: number): void {
    this.loadingList.set(true);
    this.api.adminAuditLogs(page).subscribe({
      next: result => { this.auditLogs.set(result); this.loadingList.set(false); },
      error: () => { this.error.set('Impossible de charger le journal d’activité.'); this.loadingList.set(false); }
    });
  }

  verifyDoctor(doctor: AdminDoctor): void {
    if (!window.confirm(`Valider le compte médecin ${doctor.email} (${doctor.specialty}) ?`)) {
      return;
    }
    this.runDoctorAction(doctor, () => this.api.verifyAdminDoctor(doctor.id), 'La demande médecin a été validée.');
  }

  rejectDoctor(doctor: AdminDoctor): void {
    if (!window.confirm(`Refuser définitivement la demande de ${doctor.email} ?`)) {
      return;
    }
    this.runDoctorAction(doctor, () => this.api.rejectAdminDoctor(doctor.id), 'La demande médecin a été refusée.');
  }

  setDoctorEnabled(doctor: AdminDoctor): void {
    const enabled = !doctor.enabled;
    const action = enabled ? 'réactiver' : 'suspendre';
    if (!window.confirm(`Confirmer ${action} le compte médecin ${doctor.email} ?`)) {
      return;
    }
    this.runDoctorAction(
      doctor,
      () => this.api.setAdminDoctorEnabled(doctor.id, enabled),
      enabled ? 'Le compte médecin a été réactivé.' : 'Le compte médecin a été suspendu.'
    );
  }

  setPatientEnabled(patient: AdminPatient): void {
    const enabled = !patient.enabled;
    const action = enabled ? 'réactiver' : 'suspendre';
    if (!window.confirm(`Confirmer ${action} le compte patient ${patient.email} ?`)) {
      return;
    }
    this.busyId.set(patient.id);
    this.api.setAdminPatientEnabled(patient.id, enabled).subscribe({
      next: updated => {
        this.patients.update(page => page
          ? { ...page, content: page.content.map(item => item.id === updated.id ? updated : item) }
          : page);
        this.busyId.set(null);
        this.message.set(enabled ? 'Le compte patient a été réactivé.' : 'Le compte patient a été suspendu.');
      },
      error: () => {
        this.error.set('La mise à jour du compte patient a échoué.');
        this.busyId.set(null);
      }
    });
  }

  verificationLabel(status: AdminDoctor['verificationStatus']): string {
    return status === 'VERIFIED' ? 'Vérifié' : status === 'PENDING' ? 'En attente' : 'Refusé';
  }

  verificationClass(status: AdminDoctor['verificationStatus']): string {
    return status === 'VERIFIED' ? 'verified' : status === 'PENDING' ? 'pending' : 'rejected';
  }

  statusLabel(status: AdminAppointment['status']): string {
    return status === 'CONFIRMED' ? 'Confirmé'
      : status === 'PENDING' ? 'En attente'
        : status === 'DONE' ? 'Terminé' : 'Annulé';
  }

  statusClass(status: AdminAppointment['status']): string {
    return status === 'CONFIRMED' ? 'confirmed'
      : status === 'PENDING' ? 'pending'
        : status === 'DONE' ? 'done' : 'cancelled';
  }

  actionLabel(action: string): string {
    const labels: Record<string, string> = {
      DOCTOR_VERIFY: 'Validation médecin',
      DOCTOR_REJECT: 'Refus médecin',
      DOCTOR_SUSPEND: 'Suspension médecin',
      DOCTOR_REACTIVATE: 'Réactivation médecin',
      PATIENT_SUSPEND: 'Suspension patient',
      PATIENT_REACTIVATE: 'Réactivation patient'
    };
    return labels[action] ?? action;
  }

  monthLabel(month: string): string {
    const [year, monthNumber] = month.split('-').map(Number);
    return new Intl.DateTimeFormat('fr-FR', { month: 'short' })
      .format(new Date(year, monthNumber - 1, 1));
  }

  max(values: number[]): number {
    return Math.max(...values, 0);
  }

  maxPage(totalPages: number): number {
    return Math.max(totalPages, 1);
  }

  maxRegistrations(): number {
    return this.max(this.stats()?.monthlyActivity.map(item => item.registrations) ?? []);
  }

  maxAppointments(): number {
    return this.max(this.stats()?.monthlyActivity.map(item => item.appointments) ?? []);
  }

  barHeight(value: number, maxValue: number): number {
    return maxValue === 0 ? 2 : Math.max(4, (value / maxValue) * 100);
  }

  signOut(): void {
    this.auth.logout();
    window.location.assign('/');
  }

  private runDoctorAction(
    doctor: AdminDoctor,
    request: () => import('rxjs').Observable<AdminDoctor>,
    successMessage: string
  ): void {
    this.busyId.set(doctor.id);
    request().subscribe({
      next: updated => {
        this.doctors.update(page => page
          ? { ...page, content: page.content.map(item => item.id === updated.id ? updated : item) }
          : page);
        this.stats.update(stats => stats
          ? {
              ...stats,
              pendingDoctors: Math.max(0, stats.pendingDoctors + (doctor.verificationStatus === 'PENDING' ? -1 : 0)),
              verifiedActiveDoctors: Math.max(0, stats.verifiedActiveDoctors
                + (doctor.verificationStatus === 'PENDING' && updated.enabled ? 1 : 0))
            }
          : stats);
        this.busyId.set(null);
        this.message.set(successMessage);
        this.loadStats();
        if (this.section() === 'pending') {
          this.loadDoctors(0);
        }
      },
      error: () => {
        this.error.set('L’action administrative a échoué. Actualisez les données et réessayez.');
        this.busyId.set(null);
      }
    });
  }
}
