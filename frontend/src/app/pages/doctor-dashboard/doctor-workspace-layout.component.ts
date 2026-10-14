import { Component } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-doctor-workspace-layout',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <div class="workspace">
      <aside class="sidebar">
        <a class="brand" routerLink="/"><span class="logo">+</span><span>ClairSanté<small>ESPACE MÉDECIN</small></span></a>
        <nav aria-label="Navigation médecin">
          @for (item of navigation; track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }"
               [queryParamsHandling]="item.path === 'consultations' || item.path === 'ordonnances' ? 'preserve' : ''">
              <span class="material-icons">{{ item.icon }}</span>{{ item.label }}
            </a>
          }
        </nav>
        <div class="sidebar-help"><span class="material-icons">health_and_safety</span><strong>Votre pratique, sereinement</strong><p>Vos consultations réunies dans un espace sécurisé.</p></div>
        <button class="logout" type="button" (click)="signOut()"><span class="material-icons">logout</span>Se déconnecter</button>
      </aside>
      <div class="content"><router-outlet /></div>
    </div>
  `,
  styles: [`
    :host { display:block; min-height:100vh; color:#405b60; background:#f7faf9; }
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
    .content { min-width:0; }
    @media(max-width:900px) { .workspace { grid-template-columns:200px minmax(0,1fr); } }
    @media(max-width:680px) {
      .workspace { display:block; }
      .sidebar { position:static; height:auto; flex-direction:row; align-items:center; justify-content:space-between; padding:12px 14px; border-right:0; border-bottom:1px solid #e5ecea; }
      .brand { flex:0 0 auto; font-size:13px; }
      .brand small,.sidebar-help { display:none; }
      nav { display:flex; flex:0 1 55vw; gap:2px; margin:0; overflow-x:auto; scrollbar-width:none; }
      nav a { flex:0 0 36px; padding:9px; font-size:0; }
      nav a .material-icons { font-size:20px; }
      .logout { flex:0 0 auto; margin:0; padding:9px; font-size:0; }
      .logout .material-icons { font-size:20px; }
    }
  `]
})
export class DoctorWorkspaceLayoutComponent {
  readonly navigation = [
    { path: 'dashboard', icon: 'grid_view', label: "Vue d'ensemble" },
    { path: 'agenda', icon: 'calendar_month', label: 'Mon agenda' },
    { path: 'disponibilites', icon: 'schedule', label: 'Mes disponibilités' },
    { path: 'patients', icon: 'groups', label: 'Mes patients' },
    { path: 'consultations', icon: 'description', label: 'Consultations' },
    { path: 'ordonnances', icon: 'medication', label: 'Ordonnances' },
    { path: 'notifications', icon: 'notifications', label: 'Notifications' },
    { path: 'profil', icon: 'person', label: 'Mon profil' }
  ];

  constructor(private readonly auth: AuthService, private readonly router: Router) {}

  signOut(): void {
    this.auth.logout();
    void this.router.navigateByUrl('/');
  }
}
