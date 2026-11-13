import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { PatientPrescription, TelemedApiService } from '../../core/telemed-api.service';

interface PrescriptionGroup {
  doctorId: string;
  doctorName: string;
  doctorSpecialty: string;
  prescriptions: PatientPrescription[];
}

@Component({
  selector: 'app-patient-prescriptions',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <main class="page">
      <header class="topbar">
        <a class="brand" routerLink="/patient"><span class="logo">+</span>ClairSanté</a>
        <a class="back-link" routerLink="/patient">← Retour à mon espace</a>
      </header>

      <section class="intro">
        <span class="eyebrow">DOCUMENTS MÉDICAUX</span>
        <h1>Mes ordonnances</h1>
        <p>Consultez les ordonnances validées, classées par médecin.</p>
      </section>

      @if (error()) {
        <div class="notice" role="alert">{{ error() }}</div>
      } @else if (loading()) {
        <div class="empty" role="status">Chargement de vos ordonnances…</div>
      } @else if (groups().length === 0) {
        <div class="empty">
          <span class="material-icons">folder_open</span>
          <h2>Aucune ordonnance disponible</h2>
          <p>Les ordonnances validées par vos médecins apparaîtront ici.</p>
        </div>
      } @else {
        <div class="groups">
          @for (group of groups(); track group.doctorId) {
            <section class="doctor-group" [attr.aria-labelledby]="'doctor-' + group.doctorId">
              <header class="doctor-heading">
                <span class="material-icons doctor-icon">medical_services</span>
                <div>
                  <h2 [id]="'doctor-' + group.doctorId">Dr {{ group.doctorName }}</h2>
                  <p>{{ group.doctorSpecialty }}</p>
                </div>
                <span class="count">{{ group.prescriptions.length }} ordonnance{{ group.prescriptions.length === 1 ? '' : 's' }}</span>
              </header>

              @for (prescription of group.prescriptions; track prescription.id) {
                <article class="prescription">
                  <div class="prescription-meta">
                    <span class="material-icons">description</span>
                    <div>
                      <h3>Ordonnance médicale</h3>
                      <p>Consultation du {{ prescription.consultationAt | date:'d MMMM y':'':'fr' }}
                        @if (prescription.date) { · Émise le {{ prescription.date | date:'d MMMM y':'':'fr' }} }
                      </p>
                    </div>
                    <button type="button" class="download" (click)="download(prescription)">
                      Télécharger le PDF <span class="material-icons">download</span>
                    </button>
                  </div>
                  <details>
                    <summary>Lire le contenu de l’ordonnance</summary>
                    <pre>{{ prescription.content }}</pre>
                  </details>
                </article>
              }
            </section>
          }
        </div>
      }
    </main>
  `,
  styles: [`
    :host { display:block; min-height:100vh; background:#f5f8f7; color:#18343d; }
    .page { width:min(960px,100%); margin:0 auto; padding:0 28px 48px; }
    .topbar { display:flex; min-height:76px; align-items:center; justify-content:space-between; border-bottom:1px solid #e5ecea; }
    .brand { display:flex; align-items:center; gap:10px; color:#183d49; font-weight:800; text-decoration:none; }
    .logo { display:grid; width:36px; height:36px; place-items:center; border-radius:12px; background:#087e75; color:white; font-size:26px; }
    .back-link { color:#087e75; font-size:13px; font-weight:700; text-decoration:none; }
    .intro { padding:38px 0 24px; }
    .eyebrow { color:#087e75; font-size:10px; font-weight:800; letter-spacing:1.5px; }
    h1 { margin:8px 0; color:#183d49; font-size:30px; }
    .intro p,.doctor-heading p,.prescription-meta p { margin:0; color:#718287; font-size:13px; }
    .groups { display:grid; gap:18px; }
    .doctor-group { overflow:hidden; border:1px solid #e2ebe8; border-radius:16px; background:#fff; }
    .doctor-heading { display:flex; align-items:center; gap:12px; padding:18px 20px; background:#f9fcfb; }
    .doctor-icon { color:#087e75; }
    .doctor-heading h2 { margin:0 0 4px; color:#183d49; font-size:16px; }
    .count { margin-left:auto; color:#718287; font-size:12px; }
    .prescription { padding:18px 20px; border-top:1px solid #edf2f0; }
    .prescription-meta { display:flex; align-items:center; gap:12px; }
    .prescription-meta > .material-icons { color:#168879; }
    .prescription-meta h3 { margin:0 0 5px; color:#24434b; font-size:14px; }
    .prescription-meta p { font-size:12px; }
    .download { display:flex; align-items:center; gap:6px; margin-left:auto; padding:9px 12px; border:0; border-radius:9px; background:#eaf5f1; color:#087e75; font:inherit; font-size:12px; font-weight:700; cursor:pointer; }
    .download .material-icons { font-size:17px; }
    details { margin:14px 0 0 32px; color:#486166; font-size:13px; }
    summary { width:max-content; color:#087e75; font-weight:700; cursor:pointer; }
    pre { overflow:auto; margin:12px 0 0; padding:14px; border-radius:10px; background:#f7faf9; color:#29484f; font:inherit; line-height:1.65; white-space:pre-wrap; }
    .empty { margin-top:24px; padding:40px 20px; border:1px solid #e2ebe8; border-radius:16px; background:#fff; color:#718287; text-align:center; }
    .empty .material-icons { color:#8ca8a3; font-size:32px; }
    .empty h2 { margin:10px 0 6px; color:#24434b; font-size:17px; }
    .notice { margin-top:20px; padding:13px 15px; border-radius:10px; background:#fff1f0; color:#9d3e37; font-size:13px; }
    @media(max-width:600px) {
      .page { padding:0 16px 30px; }
      .topbar { min-height:64px; }
      .intro { padding:28px 0 18px; }
      h1 { font-size:25px; }
      .doctor-heading,.prescription { padding:15px; }
      .count { font-size:11px; }
      .prescription-meta { flex-wrap:wrap; }
      .prescription-meta > div { flex:1; min-width:130px; }
      .download { margin:4px 0 0 32px; }
      details { margin-left:32px; }
    }
  `]
})
export class PatientPrescriptionsComponent implements OnInit {
  readonly loading = signal(true);
  readonly error = signal('');
  readonly prescriptions = signal<PatientPrescription[]>([]);
  readonly groups = computed(() => {
    const groups = new Map<string, PrescriptionGroup>();
    for (const prescription of this.prescriptions()) {
      let group = groups.get(prescription.doctorId);
      if (!group) {
        group = {
          doctorId: prescription.doctorId,
          doctorName: prescription.doctorName,
          doctorSpecialty: prescription.doctorSpecialty,
          prescriptions: []
        };
        groups.set(prescription.doctorId, group);
      }
      group.prescriptions.push(prescription);
    }
    return [...groups.values()];
  });

  constructor(private readonly api: TelemedApiService) {}

  ngOnInit(): void {
    this.api.patientPrescriptions().subscribe({
      next: prescriptions => {
        this.prescriptions.set(prescriptions);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Impossible de charger vos ordonnances. Veuillez réessayer.');
        this.loading.set(false);
      }
    });
  }

  download(prescription: PatientPrescription): void {
    this.api.prescriptionPdf(prescription.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `ordonnance-${prescription.id}.pdf`;
        document.body.appendChild(anchor);
        anchor.click();
        document.body.removeChild(anchor);
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => this.error.set('Le téléchargement de cette ordonnance a échoué. Réessayez dans quelques instants.')
    });
  }
}
