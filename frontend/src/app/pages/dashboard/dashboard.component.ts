import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatButtonModule, MatIconModule, RouterLink],
  template: `
    <div class="app-shell min-h-screen bg-slate-50 text-slate-800 transition-colors dark:bg-slate-950 dark:text-slate-100">
      <header class="border-b border-slate-200/80 bg-white/90 dark:border-slate-800 dark:bg-slate-900/90">
        <div class="mx-auto flex max-w-7xl items-center justify-between px-5 py-4 sm:px-8">
          <a class="flex items-center gap-3 rounded-lg focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-medical-600"
             href="/" aria-label="ClairSanté, accueil">
            <span class="flex h-10 w-10 items-center justify-center rounded-xl bg-medical-600 text-white">
              <mat-icon aria-hidden="true">health_and_safety</mat-icon>
            </span>
            <span>
              <span class="block text-lg font-bold tracking-tight text-slate-900 dark:text-white">ClairSanté</span>
              <span class="block text-xs text-slate-500 dark:text-slate-400">Votre santé, en toute confiance</span>
            </span>
          </a>
          <div class="flex items-center gap-2 sm:gap-4">
            <span class="hidden text-sm text-slate-600 dark:text-slate-300 sm:inline">
              {{ auth.claims()?.email ?? 'Votre espace santé' }}
            </span>
            <button mat-icon-button type="button" (click)="toggleTheme()"
                    [attr.aria-label]="isDark() ? 'Activer le thème clair' : 'Activer le thème sombre'">
              <mat-icon aria-hidden="true">{{ isDark() ? 'light_mode' : 'dark_mode' }}</mat-icon>
            </button>
            <button mat-stroked-button type="button" class="!rounded-full !border-slate-300 !px-4 dark:!border-slate-600"
                    [routerLink]="auth.isAuthenticated ? dashboardPath : '/connexion'">
              {{ auth.isAuthenticated ? 'Mon espace' : 'Se connecter' }}
            </button>
          </div>
        </div>
      </header>

      <main class="mx-auto max-w-7xl px-5 py-12 sm:px-8 sm:py-16">
        <section class="grid items-center gap-12 lg:grid-cols-[1.1fr_0.9fr] lg:gap-16" aria-labelledby="welcome-title">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-medical-50 px-3 py-1.5 text-sm font-medium text-medical-700 dark:bg-medical-900/50 dark:text-medical-100">
              <span class="h-2 w-2 rounded-full bg-medical-500" aria-hidden="true"></span>
              Les soins, plus proches de vous
            </span>
            <h1 id="welcome-title" class="mt-6 max-w-2xl text-4xl font-bold leading-tight tracking-tight text-slate-950 dark:text-white sm:text-5xl">
              Prenez soin de vous, <span class="text-medical-600 dark:text-medical-400">simplement.</span>
            </h1>
            <p class="mt-5 max-w-xl text-lg leading-8 text-slate-600 dark:text-slate-300">
              Échangez avec un professionnel de santé et gérez vos rendez-vous depuis un espace pensé pour vous.
            </p>
            <div class="mt-8 flex flex-col gap-3 sm:flex-row">
              <button mat-flat-button color="primary" class="!h-12 !rounded-xl !bg-medical-600 !px-6 !text-base !font-semibold !text-white"
                      [routerLink]="auth.isAuthenticated ? dashboardPath : '/inscription'">
                Découvrir la plateforme
                <mat-icon class="!ml-2" aria-hidden="true">arrow_forward</mat-icon>
              </button>
              <button mat-stroked-button class="!h-12 !rounded-xl !border-slate-300 !px-6 !text-base dark:!border-slate-600"
                      routerLink="/inscription">
                Créer mon compte
              </button>
            </div>
            <div class="mt-10 flex items-center gap-3 text-sm text-slate-500 dark:text-slate-400">
              <mat-icon class="!h-5 !w-5 !text-medical-600 dark:!text-medical-400" aria-hidden="true">lock</mat-icon>
              <span>Un espace conçu autour de la confidentialité de vos échanges.</span>
            </div>
          </div>

          <div class="relative">
            <div class="absolute -inset-4 rounded-[2rem] bg-gradient-to-br from-medical-100 via-sky-50 to-blue-100 blur-2xl dark:from-medical-900/30 dark:via-slate-900 dark:to-blue-900/30" aria-hidden="true"></div>
            <div class="relative overflow-hidden rounded-3xl border border-white/80 bg-white p-6 shadow-xl shadow-slate-200/60 dark:border-slate-700 dark:bg-slate-900 dark:shadow-black/20 sm:p-8">
              <div class="flex items-start justify-between">
                <div>
                  <p class="text-sm font-medium text-slate-500 dark:text-slate-400">Votre espace santé</p>
                  <h2 class="mt-1 text-xl font-bold text-slate-900 dark:text-white">Tout au même endroit</h2>
                </div>
                <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-blue-50 text-ocean-700 dark:bg-blue-950 dark:text-blue-300">
                  <mat-icon aria-hidden="true">medical_services</mat-icon>
                </span>
              </div>
              <div class="mt-7 space-y-3">
                @for (feature of features; track feature.title) {
                  <article class="flex items-center gap-4 rounded-2xl border border-slate-100 bg-slate-50/70 p-4 dark:border-slate-800 dark:bg-slate-800/50">
                    <span class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white text-medical-700 shadow-sm dark:bg-slate-700 dark:text-medical-100">
                      <mat-icon aria-hidden="true">{{ feature.icon }}</mat-icon>
                    </span>
                    <div>
                      <h3 class="font-semibold text-slate-800 dark:text-slate-100">{{ feature.title }}</h3>
                      <p class="mt-0.5 text-sm text-slate-500 dark:text-slate-400">{{ feature.description }}</p>
                    </div>
                    <mat-icon class="ml-auto !text-slate-400" aria-hidden="true">chevron_right</mat-icon>
                  </article>
                }
              </div>
              <div class="mt-6 flex items-center gap-3 rounded-xl bg-medical-50 px-4 py-3 dark:bg-medical-900/40">
                <mat-icon class="!text-medical-700 dark:!text-medical-200" aria-hidden="true">verified_user</mat-icon>
                <p class="text-sm text-medical-900 dark:text-medical-100">Vos informations restent privées et protégées.</p>
              </div>
            </div>
          </div>
        </section>

        <section class="mt-20 border-t border-slate-200 pt-10 dark:border-slate-800" aria-label="Informations sur la plateforme">
          <div class="grid gap-8 sm:grid-cols-3">
            <article class="flex gap-4">
              <mat-icon class="!text-medical-600 dark:!text-medical-400" aria-hidden="true">schedule</mat-icon>
              <div><h2 class="font-semibold">À votre rythme</h2><p class="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">Retrouvez vos soins en quelques étapes, où que vous soyez.</p></div>
            </article>
            <article class="flex gap-4">
              <mat-icon class="!text-medical-600 dark:!text-medical-400" aria-hidden="true">groups</mat-icon>
              <div><h2 class="font-semibold">Un accompagnement humain</h2><p class="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">Une expérience conçue pour rapprocher patients et professionnels.</p></div>
            </article>
            <article class="flex gap-4">
              <mat-icon class="!text-medical-600 dark:!text-medical-400" aria-hidden="true">shield</mat-icon>
              <div><h2 class="font-semibold">La confidentialité d'abord</h2><p class="mt-1 text-sm leading-6 text-slate-500 dark:text-slate-400">La protection de vos informations guide chaque étape du parcours.</p></div>
            </article>
          </div>
        </section>
      </main>

      <footer class="border-t border-slate-200 dark:border-slate-800">
        <div class="mx-auto flex max-w-7xl flex-col gap-2 px-5 py-6 text-sm text-slate-500 dark:text-slate-400 sm:flex-row sm:items-center sm:justify-between sm:px-8">
          <span>© 2026 ClairSanté</span>
          <span>Une plateforme de santé numérique à visage humain.</span>
        </div>
      </footer>
    </div>
  `
})
export class DashboardComponent {
  constructor(readonly auth: AuthService) {}

  get dashboardPath(): string {
    const roles = this.auth.claims()?.roles ?? [];
    if (roles.includes('ADMIN')) {
      return '/admin';
    }
    if (roles.includes('DOCTOR')) {
      return '/medecin/dashboard';
    }
    return roles.includes('PATIENT') ? '/patient' : '/';
  }

  readonly isDark = signal(false);
  readonly features = [
    { icon: 'calendar_month', title: 'Rendez-vous', description: 'Organisez votre suivi en toute simplicité.' },
    { icon: 'videocam', title: 'Téléconsultation', description: 'Consultez à distance, en toute sérénité.' },
    { icon: 'folder_shared', title: 'Espace personnel', description: 'Retrouvez vos informations essentielles.' }
  ];

  toggleTheme(): void {
    this.isDark.update((isDark) => !isDark);
    document.documentElement.classList.toggle('dark', this.isDark());
  }
}
