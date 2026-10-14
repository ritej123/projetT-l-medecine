import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-auth',
  standalone: true,
  imports: [FormsModule, RouterLink],
  template: `
    <main class="auth-page">
      <a class="brand" routerLink="/" aria-label="ClairSanté, accueil">
        <span class="brand-icon" aria-hidden="true">+</span>
        <span>ClairSanté <small>La santé, plus proche</small></span>
      </a>
      <section class="auth-card" aria-labelledby="auth-title">
        <div class="eyebrow"><span></span> Un espace santé qui vous ressemble</div>
        <h1 id="auth-title">{{ registering() ? 'Bienvenue parmi nous' : 'Heureux de vous revoir' }}</h1>
        <p class="subtitle">
          {{ registering() ? 'Choisissez votre profil pour créer votre compte.' : 'Connectez-vous pour retrouver votre espace.' }}
        </p>

        @if (error()) {
          <div class="alert" role="alert">{{ error() }}</div>
        }
        @if (success()) {
          <div class="success" role="status">{{ success() }}</div>
        }

        <form (ngSubmit)="submit()" #authForm="ngForm">
          @if (registering()) {
            <label for="accountType">Je suis</label>
            <select id="accountType" name="accountType" [(ngModel)]="accountType" required>
              <option value="PATIENT">Patient</option>
              <option value="DOCTOR">Médecin</option>
            </select>
          }

          <label for="email">Adresse e-mail</label>
          <input id="email" name="email" type="email" [(ngModel)]="email"
                 autocomplete="email" placeholder="vous@exemple.fr" required email>

          <label for="password">Mot de passe</label>
          <div class="password-wrap">
            <input id="password" name="password" [type]="showPassword() ? 'text' : 'password'"
                   [(ngModel)]="password" [autocomplete]="registering() ? 'new-password' : 'current-password'"
                   minlength="12" maxlength="72" required placeholder="12 caractères minimum">
            <button type="button" (click)="showPassword.update(value => !value)"
                    [attr.aria-label]="showPassword() ? 'Masquer le mot de passe' : 'Afficher le mot de passe'">
              <span class="material-icons" aria-hidden="true">{{ showPassword() ? 'visibility_off' : 'visibility' }}</span>
            </button>
          </div>

          @if (registering()) {
            @if (accountType === 'DOCTOR') {
              <label for="specialty">Spécialité médicale</label>
              <input id="specialty" name="specialty" type="text" [(ngModel)]="specialty"
                     maxlength="120" required placeholder="Ex. Médecine générale">
              <label for="bio">Présentation professionnelle</label>
              <textarea id="bio" name="bio" [(ngModel)]="bio" maxlength="4000"
                        placeholder="Présentez brièvement votre expérience et votre pratique"></textarea>
              <p class="privacy">Votre demande sera vérifiée par l’administration. Vous pourrez vous connecter à l’espace médecin après son approbation.</p>
            } @else {
              <p class="privacy">Votre inscription crée un compte patient et vous donne accès à votre espace personnel.</p>
            }
            <label for="confirmation">Confirmer le mot de passe</label>
            <input id="confirmation" name="confirmation" type="password" [(ngModel)]="confirmation"
                   autocomplete="new-password" minlength="12" maxlength="72" required>
          }

          <button class="submit" type="submit" [disabled]="authForm.invalid || busy()">
            @if (busy()) { <span class="spinner" aria-hidden="true"></span> }
            {{ busy() ? 'Veuillez patienter…' : (registering() ? 'Créer mon compte' : 'Me connecter') }}
          </button>
        </form>

        <p class="switch-mode">
          {{ registering() ? 'Vous avez déjà un compte ?' : 'Pas encore de compte ?' }}
          <a [routerLink]="registering() ? '/connexion' : '/inscription'">
            {{ registering() ? 'Se connecter' : 'Créer un compte' }}
          </a>
        </p>
        <div class="secure-note"><span class="material-icons" aria-hidden="true">verified_user</span>
          Connexion chiffrée · Vos données restent confidentielles
        </div>
      </section>
      <a class="back-home" routerLink="/">← Retour à l’accueil</a>
    </main>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; }
    .auth-page { min-height: 100vh; display: flex; flex-direction: column; align-items: center; padding: 32px 18px; background: radial-gradient(ellipse at 15% 8%, #e0f3ef 0, transparent 42%), radial-gradient(ellipse at 90% 85%, #e9efff 0, transparent 35%), #f7faf9; }
    .brand { display: flex; align-items: center; gap: 12px; align-self: flex-start; color: #183d4a; font-weight: 800; text-decoration: none; }
    .brand-icon { display: grid; width: 42px; height: 42px; place-items: center; border-radius: 14px; background: #0b8076; color: #fff; font-size: 28px; }
    .brand small { display: block; margin-top: 3px; color: #809195; font-size: 11px; font-weight: 500; }
    .auth-card { width: min(100%, 460px); margin: auto; padding: 42px; border: 1px solid #e5eceb; border-radius: 26px; background: #ffffffed; box-shadow: 0 28px 80px #23463d12; animation: rise-in .45s ease both; }
    .eyebrow { display: flex; align-items: center; gap: 8px; color: #54837c; font-size: 12px; font-weight: 650; }
    .eyebrow span { width: 7px; height: 7px; border-radius: 50%; background: #21a087; }
    h1 { margin: 20px 0 7px; color: #173b47; font-size: 30px; letter-spacing: -.8px; }
    .subtitle { margin: 0 0 28px; color: #75868a; font-size: 14px; }
    form { display: grid; gap: 10px; }
    label { margin-top: 6px; color: #36505a; font-size: 13px; font-weight: 650; }
    input,select,textarea { width: 100%; box-sizing: border-box; min-height: 48px; border: 1px solid #dfe8e7; border-radius: 12px; padding: 0 14px; background: #fbfdfc; color: #193943; font: inherit; font-size: 14px; transition: border-color .18s, box-shadow .18s; }
    textarea { min-height: 90px; padding: 12px 14px; resize: vertical; }
    input:focus { border-color: #0c897d; outline: none; box-shadow: 0 0 0 4px #0c897d16; }
    .password-wrap { position: relative; }
    .password-wrap input { padding-right: 50px; }
    .password-wrap button { position: absolute; top: 4px; right: 4px; width: 40px; height: 40px; border: 0; border-radius: 9px; background: transparent; color: #718287; cursor: pointer; }
    .material-icons { font-size: 19px; }
    .privacy { margin: 2px 0 9px; color: #829197; font-size: 12px; line-height: 1.6; }
    .submit { display: flex; align-items: center; justify-content: center; gap: 10px; height: 50px; margin-top: 10px; border: 0; border-radius: 12px; background: #087e75; color: white; font: inherit; font-size: 14px; font-weight: 700; cursor: pointer; box-shadow: 0 8px 18px #087e752d; transition: transform .18s, background .18s; }
    .submit:hover:not(:disabled) { transform: translateY(-1px); background: #066f67; }
    .submit:disabled { opacity: .55; cursor: not-allowed; }
    .switch-mode { margin: 24px 0 20px; color: #75868a; text-align: center; font-size: 13px; }
    .switch-mode a { margin-left: 4px; color: #087e75; font-weight: 700; text-decoration: none; }
    .secure-note { display: flex; align-items: center; justify-content: center; gap: 7px; padding-top: 17px; border-top: 1px solid #edf1f0; color: #8a999d; font-size: 11px; }
    .secure-note .material-icons { color: #218b78; font-size: 16px; }
    .alert, .success { margin-bottom: 15px; border-radius: 10px; padding: 11px 13px; font-size: 13px; }
    .alert { background: #fff0ee; color: #a33e36; }
    .success { background: #e6f6ef; color: #236c51; }
    .spinner { width: 16px; height: 16px; border: 2px solid #ffffff70; border-top-color: #fff; border-radius: 50%; animation: spin .7s linear infinite; }
    .back-home { margin-top: 24px; color: #648078; font-size: 13px; text-decoration: none; }
    @keyframes rise-in { from { opacity: 0; transform: translateY(10px); } to { opacity: 1; transform: translateY(0); } }
    @keyframes spin { to { transform: rotate(360deg); } }
    @media (max-width: 520px) { .auth-page { padding: 20px 14px; } .auth-card { padding: 30px 22px; border-radius: 20px; } h1 { font-size: 26px; } }
  `]
})
export class AuthComponent implements OnInit {
  readonly registering = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly success = signal('');
  readonly showPassword = signal(false);
  email = '';
  password = '';
  confirmation = '';
  accountType: 'PATIENT' | 'DOCTOR' = 'PATIENT';
  specialty = '';
  bio = '';

  constructor(
    private readonly auth: AuthService,
    private readonly router: Router,
    private readonly route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.registering.set(this.route.snapshot.data['mode'] === 'register');
  }

  submit(): void {
    this.error.set('');
    this.success.set('');
    if (this.registering() && this.password !== this.confirmation) {
      this.error.set('Les mots de passe ne correspondent pas.');
      return;
    }
    this.busy.set(true);
    if (this.registering() && this.accountType === 'DOCTOR') {
      this.auth.registerDoctor(this.email.trim(), this.password, this.specialty.trim(), this.bio.trim()).subscribe({
        next: () => {
          this.busy.set(false);
          this.password = '';
          this.confirmation = '';
          this.success.set('Votre demande de compte médecin est envoyée. Vous pourrez vous connecter dès qu’elle aura été approuvée.');
        },
        error: error => {
          this.busy.set(false);
          this.setRequestError(error);
        }
      });
      return;
    }
    const request = this.registering()
      ? this.auth.registerPatient(this.email.trim(), this.password)
      : this.auth.login(this.email.trim(), this.password);
    request.subscribe({
      next: () => {
        this.busy.set(false);
        const claims = this.auth.claims();
        if (!claims) {
          this.error.set('La session reçue est invalide. Veuillez réessayer.');
          return;
        }
        this.success.set(this.registering() ? 'Votre espace patient est prêt.' : 'Connexion réussie.');
        const destination = claims.roles.includes('DOCTOR')
          ? '/medecin/dashboard'
          : claims.roles.includes('ADMIN')
            ? '/admin'
            : claims.roles.includes('PATIENT')
              ? '/patient'
              : '/';
        void this.router.navigateByUrl(destination);
      },
      error: error => {
        this.busy.set(false);
        this.setRequestError(error);
      }
    });
  }

  private setRequestError(error: { status: number }): void {
    if (error.status === 409) {
      this.error.set('Un compte ou une demande existe déjà pour cette adresse e-mail.');
    } else if (error.status === 401) {
      this.error.set('Adresse e-mail ou mot de passe incorrect.');
    } else if (error.status === 429) {
      this.error.set('Trop de tentatives. Réessayez dans une minute.');
    } else {
      this.error.set(error.status === 0
        ? 'Le service est momentanément inaccessible.'
        : 'Impossible de traiter la demande. Vérifiez les informations saisies.');
    }
  }
}
