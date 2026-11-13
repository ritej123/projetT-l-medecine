import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, ElementRef, Inject, OnDestroy, OnInit, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { firstValueFrom } from 'rxjs';
import { AccessTokenStore } from '../../core/access-token.store';
import { AuthService } from '../../core/auth.service';
import { DoctorConsultation, DoctorPatientDetail, Prescription, TelemedApiService } from '../../core/telemed-api.service';
import { TELEMED_API_BASE_URL, TELEMED_ICE_SERVERS } from './teleconsultation.config';

interface RoomEvent {
  appointmentId: string;
  senderId: string;
  type: 'JOIN' | 'LEAVE' | 'OFFER' | 'ANSWER' | 'ICE_CANDIDATE' | 'CHAT';
  payload: RTCSessionDescriptionInit | RTCIceCandidateInit | string | null;
  sentAt: string;
}

interface ChatMessage {
  id: number;
  sender: 'Vous' | 'Participant';
  text: string;
  sentAt: string;
}

@Component({
  selector: 'app-teleconsultation',
  standalone: true,
  imports: [FormsModule, RouterLink, DatePipe],
  template: `
    <main class="call-shell">
      <header class="call-header">
        <a routerLink="/" class="brand" aria-label="Retour à l'accueil ClairSanté">
          <span class="brand-mark" aria-hidden="true">+</span>
          <span>ClairSanté <small>Téléconsultation sécurisée</small></span>
        </a>
        <div class="call-status" role="status" aria-live="polite">
          <span class="status-dot" [class.connected]="connected()"></span>
          {{ status() }}
        </div>
      </header>

      @if (error()) {
        <p class="error-banner" role="alert">{{ error() }}</p>
      }

      @if (doctorMode()) {
        <section class="call-layout doctor-call-layout" aria-label="Salle de téléconsultation médecin">
          <div class="video-column">
            <div class="video-stage">
              <video #remoteVideo autoplay playsinline aria-label="Vidéo du participant"></video>
              @if (!remoteStream()) {
                <div class="video-placeholder">
                  <span class="avatar" aria-hidden="true">CS</span>
                  <p>{{ connected() ? 'En attente de l’autre participant…' : 'Votre consultation vidéo' }}</p>
                </div>
              }
              <div class="local-video">
                <video #localVideo autoplay muted playsinline aria-label="Aperçu de votre caméra"></video>
                @if (!cameraEnabled()) {
                  <span class="muted-overlay">Caméra désactivée</span>
                }
              </div>
              <div class="video-caption">Consultation privée</div>
            </div>

            <div class="controls" aria-label="Commandes de l'appel">
              <button type="button" class="control" (click)="toggleMicrophone()"
                      [disabled]="!inCall()" [attr.aria-pressed]="microphoneEnabled()"
                      [attr.aria-label]="microphoneEnabled() ? 'Couper le microphone' : 'Activer le microphone'">
                <span aria-hidden="true">{{ microphoneEnabled() ? 'mic' : 'mic_off' }}</span>
                <small>Micro</small>
              </button>
              <button type="button" class="control" (click)="toggleCamera()"
                      [disabled]="!inCall()" [attr.aria-pressed]="cameraEnabled()"
                      [attr.aria-label]="cameraEnabled() ? 'Couper la caméra' : 'Activer la caméra'">
                <span aria-hidden="true">{{ cameraEnabled() ? 'videocam' : 'videocam_off' }}</span>
                <small>Caméra</small>
              </button>
              <button type="button" class="control" (click)="toggleScreenShare()"
                      [disabled]="!inCall()" [attr.aria-pressed]="sharingScreen()">
                <span aria-hidden="true">present_to_all</span>
                <small>{{ sharingScreen() ? 'Arrêter le partage' : 'Partager écran' }}</small>
              </button>
              @if (!inCall()) {
                <button type="button" class="join-button" (click)="joinCall()" [disabled]="joining()">
                  {{ joining() ? 'Connexion…' : 'Rejoindre la consultation' }}
                </button>
              } @else {
                <button type="button" class="end-button" (click)="endCall()">
                  <span aria-hidden="true">call_end</span> Terminer l’appel
                </button>
              }
            </div>
            <p class="privacy-note">La vidéo et l’audio sont transmis directement entre les participants. La salle est réservée aux personnes du rendez-vous.</p>
          </div>

          <aside class="medical-panel" aria-label="Panneau médical">
            <div class="medical-tab-list" role="tablist" aria-label="Onglets médicaux">
              <button type="button" class="medical-tab" [class.active]="activeMedicalTab() === 'chat'" (click)="activeMedicalTab.set('chat')" role="tab" [attr.aria-selected]="activeMedicalTab() === 'chat'">Chat</button>
              <button type="button" class="medical-tab" [class.active]="activeMedicalTab() === 'dossier'" (click)="activeMedicalTab.set('dossier')" role="tab" [attr.aria-selected]="activeMedicalTab() === 'dossier'">Dossier</button>
              <button type="button" class="medical-tab" [class.active]="activeMedicalTab() === 'notes'" (click)="activeMedicalTab.set('notes')" role="tab" [attr.aria-selected]="activeMedicalTab() === 'notes'">Notes</button>
              <button type="button" class="medical-tab" [class.active]="activeMedicalTab() === 'ordonnance'" (click)="activeMedicalTab.set('ordonnance')" role="tab" [attr.aria-selected]="activeMedicalTab() === 'ordonnance'">Ordonnance</button>
            </div>

            @switch (activeMedicalTab()) {
              @case ('chat') {
                <div class="chat-panel" aria-label="Chat de la consultation">
                  <div class="chat-heading">
                    <div>
                      <h1>Conversation</h1>
                      <p>Messages privés de cette consultation</p>
                    </div>
                    <span class="chat-lock" aria-label="Conversation protégée">🔒</span>
                  </div>
                  <div class="chat-messages" aria-live="polite" aria-relevant="additions">
                    @if (messages().length === 0) {
                      <p class="empty-chat">Les messages de la consultation apparaîtront ici.</p>
                    }
                    @for (message of messages(); track message.id) {
                      <article class="message" [class.own-message]="message.sender === 'Vous'">
                        <span>{{ message.sender }}</span>
                        <p>{{ message.text }}</p>
                        <time>{{ message.sentAt }}</time>
                      </article>
                    }
                  </div>
                  <form class="chat-form" (submit)="sendChat($event)">
                    <label class="visually-hidden" for="doctor-chat-message">Votre message</label>
                    <input id="doctor-chat-message" type="text" maxlength="2000" autocomplete="off"
                           placeholder="Écrire un message…" [value]="chatDraft()"
                           (input)="updateChatDraft($event)" [disabled]="!connected()">
                    <button type="submit" [disabled]="!connected() || !chatDraft().trim()"
                            aria-label="Envoyer le message">Envoyer</button>
                  </form>
                </div>
              }
              @case ('dossier') {
                <div class="medical-content">
                  @if (dossierLoading()) {
                    <div class="medical-loading"><span class="loader"></span><p>Chargement du dossier…</p></div>
                  } @else if (patientRecord(); as patient) {
                    <div class="medical-card">
                      <div class="patient-header">
                        <div class="patient-avatar-large">{{ patientInitials(patient.email) }}</div>
                        <div>
                          <h2>Dossier patient</h2>
                          <p>{{ patient.email }}</p>
                        </div>
                      </div>

                      <div class="detail-grid">
                        <div class="mini-card">
                          <span>Identité</span>
                          <strong>{{ patient.email }}</strong>
                        </div>
                        <div class="mini-card">
                          <span>Consultations</span>
                          <strong>{{ patient.consultationHistory.length }}</strong>
                        </div>
                        <div class="mini-card">
                          <span>Historique</span>
                          <strong>{{ patient.appointmentHistory.length }} rendez-vous</strong>
                        </div>
                      </div>

                      <div class="medical-section">
                        <h3>Informations médicales</h3>
                        <div class="alert-list">
                          <span class="alert-pill danger">Allergies / antécédents</span>
                          <span class="alert-pill">Traitements</span>
                        </div>
                        <p>{{ patient.medicalRecord || 'Aucune information médicale enregistrée pour ce patient.' }}</p>
                      </div>

                      <div class="medical-section">
                        <h3>Historique des consultations</h3>
                        @if (patient.consultationHistory.length) {
                          @for (consultation of patient.consultationHistory; track consultation.appointmentId) {
                            <article class="history-item">
                              <strong>{{ consultation.scheduledAt | date:'d MMM y':'UTC':'fr' }}</strong>
                              @if (consultation.report) { <p><b>Compte rendu</b><br>{{ consultation.report }}</p> }
                              @if (consultation.observations) { <p><b>Observations</b><br>{{ consultation.observations }}</p> }
                              @if (consultation.diagnosis) { <p><b>Diagnostic</b><br>{{ consultation.diagnosis }}</p> }
                            </article>
                          }
                        } @else {
                          <p class="muted">Aucune consultation antérieure enregistrée.</p>
                        }
                      </div>
                    </div>
                  } @else {
                    <div class="medical-empty-state">
                      <span class="material-icons">folder_shared</span>
                      <h2>Dossier patient</h2>
                      <p>Le dossier s’affichera ici une fois le rendez-vous et le patient identifiés.</p>
                    </div>
                  }
                </div>
              }
              @case ('notes') {
                <div class="medical-content">
                  @if (notesLoading()) {
                    <div class="medical-loading"><span class="loader"></span><p>Chargement du compte-rendu…</p></div>
                  } @else {
                    <div class="medical-card">
                      <div class="editor-header">
                        <h2>Notes de consultation</h2>
                        <span class="save-pill" [class.saved]="noteSaved()">
                          <span class="status-dot" [class.connected]="noteSaved()"></span>
                          {{ noteSaved() ? 'Enregistré' : 'En cours…' }}
                        </span>
                      </div>

                      <div class="field-group">
                        <label>Motif de consultation</label>
                        <input type="text" [(ngModel)]="consultationReason" placeholder="Ex. : Suivi post-rééducation, douleurs persistantes..." />
                      </div>

                      <div class="field-group">
                        <label>Notes privées</label>
                        <textarea rows="5" [(ngModel)]="privateNotes" (ngModelChange)="scheduleConsultationSave()" placeholder="Observations, allergies signalées, contexte clinique…"></textarea>
                      </div>

                      <div class="field-group">
                        <label>Compte-rendu partagé</label>
                        <textarea rows="5" [(ngModel)]="sharedNotes" (ngModelChange)="scheduleConsultationSave()" placeholder="Résumé clair pour le patient…"></textarea>
                      </div>

                      <div class="field-group">
                        <label>Diagnostic</label>
                        <textarea rows="4" [(ngModel)]="diagnosis" (ngModelChange)="scheduleConsultationSave()" placeholder="Diagnostic retenu et conduite à tenir…"></textarea>
                      </div>

                      <div class="editor-actions">
                        <button type="button" (click)="saveConsultationNotes()" [disabled]="savingConsultationNotes()">
                          {{ savingConsultationNotes() ? 'Enregistrement…' : 'Enregistrer maintenant' }}
                        </button>
                      </div>
                    </div>
                  }
                </div>
              }
              @case ('ordonnance') {
                <div class="medical-content">
                  @if (prescriptionLoading()) {
                    <div class="medical-loading"><span class="loader"></span><p>Chargement de l’ordonnance…</p></div>
                  } @else {
                    <div class="medical-card">
                      <div class="editor-header">
                        <h2>Ordonnance</h2>
                        <span class="save-pill" [class.saved]="prescriptionSaved()">
                          <span class="status-dot" [class.connected]="prescriptionSaved()"></span>
                          {{ prescriptionSaved() ? 'Enregistrée' : 'Brouillon' }}
                        </span>
                      </div>

                      <div class="field-group">
                        <label>Lignes de médicaments</label>
                        <textarea rows="10" [(ngModel)]="prescriptionText" (ngModelChange)="schedulePrescriptionSave()" placeholder="Un médicament par ligne :&#10;Paracétamol 1 g - 1 comprimé - 2 fois/jour - 5 jours&#10;Amoxicilline 500 mg - 1 comprimé - 3 fois/jour - 7 jours"></textarea>
                      </div>

                      <div class="editor-actions">
                        <button type="button" class="secondary-btn" (click)="savePrescriptionDraft()" [disabled]="savingPrescription() || !prescriptionText.trim()">
                          {{ savingPrescription() ? 'Enregistrement…' : 'Enregistrer le brouillon' }}
                        </button>
                        <button type="button" (click)="validatePrescriptionDraft()" [disabled]="savingPrescription() || !prescriptionText.trim()">
                          Valider l’ordonnance
                        </button>
                        <button type="button" class="secondary-btn" (click)="previewPrescriptionPdf()" [disabled]="!prescriptionRecord || savingPrescription()">
                          Aperçu PDF
                        </button>
                      </div>
                    </div>
                  }
                </div>
              }
            }
          </aside>
        </section>
      } @else {
        <section class="call-layout patient-call-layout" aria-label="Salle de téléconsultation">
          <div class="video-column">
            <div class="video-stage">
              <video #remoteVideo autoplay playsinline aria-label="Vidéo du participant"></video>
              @if (!remoteStream()) {
                <div class="video-placeholder">
                  <span class="avatar" aria-hidden="true">CS</span>
                  <p>{{ connected() ? 'En attente de l’autre participant…' : 'Votre consultation vidéo' }}</p>
                </div>
              }
              <div class="local-video">
                <video #localVideo autoplay muted playsinline aria-label="Aperçu de votre caméra"></video>
                @if (!cameraEnabled()) {
                  <span class="muted-overlay">Caméra désactivée</span>
                }
              </div>
              <div class="video-caption">Consultation privée</div>
            </div>

            <div class="controls" aria-label="Commandes de l'appel">
              <button type="button" class="control" (click)="toggleMicrophone()"
                      [disabled]="!inCall()" [attr.aria-pressed]="microphoneEnabled()"
                      [attr.aria-label]="microphoneEnabled() ? 'Couper le microphone' : 'Activer le microphone'">
                <span aria-hidden="true">{{ microphoneEnabled() ? 'mic' : 'mic_off' }}</span>
                <small>Micro</small>
              </button>
              <button type="button" class="control" (click)="toggleCamera()"
                      [disabled]="!inCall()" [attr.aria-pressed]="cameraEnabled()"
                      [attr.aria-label]="cameraEnabled() ? 'Couper la caméra' : 'Activer la caméra'">
                <span aria-hidden="true">{{ cameraEnabled() ? 'videocam' : 'videocam_off' }}</span>
                <small>Caméra</small>
              </button>
              <button type="button" class="control" (click)="toggleScreenShare()"
                      [disabled]="!inCall()" [attr.aria-pressed]="sharingScreen()">
                <span aria-hidden="true">present_to_all</span>
                <small>{{ sharingScreen() ? 'Arrêter le partage' : 'Partager écran' }}</small>
              </button>
              @if (!inCall()) {
                <button type="button" class="join-button" (click)="joinCall()" [disabled]="joining()">
                  {{ joining() ? 'Connexion…' : 'Rejoindre la consultation' }}
                </button>
              } @else {
                <button type="button" class="end-button" (click)="endCall()">
                  <span aria-hidden="true">call_end</span> Terminer l’appel
                </button>
              }
            </div>
            <p class="privacy-note">La vidéo et l’audio sont transmis directement entre les participants. La salle est réservée aux personnes du rendez-vous.</p>
          </div>

          <aside class="chat-panel" aria-label="Chat de la consultation">
            <div class="chat-heading">
              <div>
                <h1>Conversation</h1>
                <p>Messages privés de cette consultation</p>
              </div>
              <span class="chat-lock" aria-label="Conversation protégée">🔒</span>
            </div>
            <div class="chat-messages" aria-live="polite" aria-relevant="additions">
              @if (messages().length === 0) {
                <p class="empty-chat">Les messages de la consultation apparaîtront ici.</p>
              }
              @for (message of messages(); track message.id) {
                <article class="message" [class.own-message]="message.sender === 'Vous'">
                  <span>{{ message.sender }}</span>
                  <p>{{ message.text }}</p>
                  <time>{{ message.sentAt }}</time>
                </article>
              }
            </div>
            <form class="chat-form" (submit)="sendChat($event)">
              <label class="visually-hidden" for="chat-message">Votre message</label>
              <input id="chat-message" type="text" maxlength="2000" autocomplete="off"
                     placeholder="Écrire un message…" [value]="chatDraft()"
                     (input)="updateChatDraft($event)" [disabled]="!connected()">
              <button type="submit" [disabled]="!connected() || !chatDraft().trim()"
                      aria-label="Envoyer le message">Envoyer</button>
            </form>
          </aside>
        </section>
      }
    </main>
  `,
  styles: [`
    :host { display: block; min-height: 100vh; background: #f3f7f8; color: #172b3a; }
    .call-shell { max-width: 1440px; margin: 0 auto; padding: 0 28px 32px; }
    .call-header { min-height: 76px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #dce7e9; }
    .brand { display: flex; align-items: center; gap: 12px; color: #15384a; text-decoration: none; font-weight: 750; }
    .brand small { display: block; margin-top: 2px; color: #748690; font-size: 12px; font-weight: 450; }
    .brand-mark { display: grid; width: 38px; height: 38px; place-items: center; border-radius: 12px; background: #087e75; color: white; font-size: 25px; }
    .call-status { display: flex; align-items: center; gap: 9px; color: #536771; font-size: 14px; }
    .status-dot { width: 9px; height: 9px; border-radius: 50%; background: #b4c0c4; }
    .status-dot.connected { background: #13836e; box-shadow: 0 0 0 4px #d9f0e9; }
    .call-layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 24px; padding-top: 24px; }
    .video-column { min-width: 0; }
    .video-stage { position: relative; display: grid; min-height: 420px; aspect-ratio: 16 / 9; place-items: center; overflow: hidden; border-radius: 22px; background: #182c39; box-shadow: 0 12px 34px #15384a18; }
    .video-stage > video { width: 100%; height: 100%; object-fit: cover; }
    .video-placeholder { position: absolute; display: grid; justify-items: center; color: #d7e1e4; }
    .video-placeholder p { margin: 16px 0 0; }
    .avatar { display: grid; width: 96px; height: 96px; place-items: center; border-radius: 50%; background: #285361; color: white; font-size: 27px; font-weight: 650; }
    .local-video { position: absolute; right: 18px; bottom: 18px; width: min(25%, 230px); aspect-ratio: 4 / 3; overflow: hidden; border: 2px solid #ffffffb8; border-radius: 14px; background: #29404c; box-shadow: 0 6px 18px #0004; }
    .local-video video { width: 100%; height: 100%; object-fit: cover; }
    .muted-overlay { position: absolute; inset: auto 0 0; padding: 7px; background: #162932b8; color: white; text-align: center; font-size: 12px; }
    .video-caption { position: absolute; left: 17px; bottom: 16px; padding: 7px 11px; border-radius: 20px; background: #12232cb8; color: white; font-size: 12px; }
    .controls { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 13px; padding: 22px 8px 10px; }
    .control { display: grid; min-width: 72px; justify-items: center; gap: 4px; border: 0; border-radius: 14px; padding: 10px; background: white; color: #31505c; cursor: pointer; }
    .control span, .end-button span { font-family: 'Material Icons', sans-serif; font-size: 22px; }
    .control small { font-size: 11px; }
    .control:disabled { opacity: .48; cursor: not-allowed; }
    .join-button, .end-button { min-height: 48px; border: 0; border-radius: 14px; padding: 0 20px; color: white; font-weight: 650; cursor: pointer; }
    .join-button { background: #087e75; }
    .end-button { display: flex; align-items: center; gap: 8px; background: #c74343; }
    .end-button span { font-size: 19px; }
    .join-button:disabled { opacity: .6; cursor: wait; }
    .privacy-note { margin: 12px 0; color: #70818a; text-align: center; font-size: 12px; }
    .chat-panel { display: flex; min-height: 490px; flex-direction: column; overflow: hidden; border: 1px solid #dfe9ea; border-radius: 20px; background: white; }
    .medical-panel { display: flex; min-height: 490px; flex-direction: column; overflow: hidden; border: 1px solid #dfe9ea; border-radius: 20px; background: white; }
    .medical-tab-list { display: flex; flex-wrap: wrap; gap: 8px; padding: 16px 16px 0; border-bottom: 1px solid #e8eeee; background: #fbfdfd; }
    .medical-tab { border: 1px solid #d6e4e4; border-radius: 999px; padding: 9px 12px; background: white; color: #46636a; font: inherit; font-size: 12px; font-weight: 600; cursor: pointer; }
    .medical-tab.active { border-color: #087e75; background: #e5f6f4; color: #087e75; }
    .medical-empty-state { display: grid; place-items: center; gap: 10px; min-height: 260px; padding: 28px 24px; text-align: center; color: #56707a; }
    .medical-empty-state .material-icons { font-size: 40px; color: #087e75; }
    .medical-empty-state h2 { margin: 0; font-size: 20px; color: #1d3e49; }
    .medical-empty-state p { max-width: 320px; margin: 0; line-height: 1.6; }
    .medical-content { padding: 16px; }
    .medical-card { display: grid; gap: 18px; }
    .patient-header { display: flex; align-items: center; gap: 14px; padding: 10px 0 4px; }
    .patient-avatar-large { display: grid; width: 52px; height: 52px; place-items: center; border-radius: 16px; background: #dff5f1; color: #0a7c73; font-weight: 700; }
    .patient-header h2 { margin: 0; }
    .patient-header p { margin: 4px 0 0; color: #60787d; }
    .detail-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
    .mini-card { display: grid; gap: 6px; border: 1px solid #e2eeed; border-radius: 12px; padding: 12px; background: white; }
    .mini-card span { color: #6f858b; font-size: 11px; }
    .mini-card strong { color: #1f3d46; font-size: 13px; }
    .medical-section { display: grid; gap: 10px; }
    .medical-section h3 { margin: 0; color: #1f3d46; font-size: 15px; }
    .alert-list { display: flex; flex-wrap: wrap; gap: 8px; }
    .alert-pill { display: inline-flex; align-items: center; border-radius: 999px; padding: 7px 10px; background: #edf5f3; color: #286d6b; font-size: 11px; font-weight: 600; }
    .alert-pill.danger { background: #fbe9e7; color: #a44941; }
    .history-item { border: 1px solid #e5efee; border-radius: 12px; padding: 12px; background: white; }
    .history-item strong { color: #1d3d47; }
    .history-item p { margin: 8px 0 0; color: #526d74; line-height: 1.6; }
    .muted { color: #71858b; font-size: 12px; }
    .editor-header { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
    .editor-header h2 { margin: 0; font-size: 18px; }
    .save-pill { display: inline-flex; align-items: center; gap: 7px; border-radius: 999px; padding: 7px 10px; background: #eef4f5; color: #4d686e; font-size: 11px; font-weight: 700; }
    .save-pill.saved { background: #e4f7ef; color: #116d61; }
    .field-group { display: grid; gap: 8px; }
    .field-group label { color: #445d64; font-size: 12px; font-weight: 650; }
    .field-group input, .field-group textarea { border: 1px solid #dfeae8; border-radius: 10px; padding: 11px 12px; background: white; color: #1d363d; font: inherit; resize: vertical; }
    .editor-actions { display: flex; flex-wrap: wrap; gap: 10px; }
    .editor-actions button { border: 0; border-radius: 10px; padding: 10px 16px; background: #087e75; color: white; font: inherit; font-weight: 700; cursor: pointer; }
    .editor-actions button:disabled { opacity: .6; cursor: not-allowed; }
    .editor-actions .secondary-btn { background: #edf5f3; color: #0d6f66; }
    .medical-loading { display: grid; place-items: center; gap: 10px; min-height: 200px; color: #597179; }
    .chat-heading { display: flex; align-items: center; justify-content: space-between; padding: 20px; border-bottom: 1px solid #e8eeee; }
    .chat-heading h1 { margin: 0; font-size: 17px; }
    .chat-heading p { margin: 5px 0 0; color: #75858b; font-size: 12px; }
    .chat-lock { font-size: 16px; }
    .chat-messages { display: flex; flex: 1; flex-direction: column; gap: 12px; overflow-y: auto; padding: 17px; }
    .empty-chat { margin: auto; color: #86949a; text-align: center; font-size: 13px; }
    .message { max-width: 88%; align-self: flex-start; border-radius: 14px 14px 14px 4px; padding: 10px 12px; background: #f0f5f5; }
    .message.own-message { align-self: flex-end; border-radius: 14px 14px 4px 14px; background: #e0f3ed; }
    .message > span { color: #4c6a70; font-size: 11px; font-weight: 650; }
    .message p { margin: 5px 0; overflow-wrap: anywhere; font-size: 14px; }
    .message time { color: #829197; font-size: 10px; }
    .chat-form { display: flex; gap: 8px; padding: 12px; border-top: 1px solid #e8eeee; }
    .chat-form input { min-width: 0; flex: 1; border: 1px solid #dce6e7; border-radius: 10px; padding: 11px; font: inherit; font-size: 13px; }
    .chat-form button { border: 0; border-radius: 10px; padding: 0 13px; background: #087e75; color: white; font-weight: 650; cursor: pointer; }
    .chat-form button:disabled { opacity: .5; cursor: not-allowed; }
    .error-banner { margin: 16px 0 0; border-radius: 10px; padding: 12px 16px; background: #fff0ef; color: #963b36; }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0, 0, 0, 0); white-space: nowrap; clip-path: inset(50%); }
    @media (max-width: 900px) { .call-layout { grid-template-columns: 1fr; } .chat-panel, .medical-panel { min-height: 360px; } }
    @media (max-width: 600px) { .call-shell { padding: 0 12px 20px; } .call-header { min-height: 66px; } .video-stage { min-height: 260px; border-radius: 15px; } .local-video { width: 32%; right: 10px; bottom: 10px; } .video-caption { left: 10px; bottom: 10px; } .controls { gap: 7px; } .control { min-width: 62px; padding: 8px 5px; } .join-button, .end-button { min-height: 44px; padding: 0 13px; } }
    @media (prefers-color-scheme: dark) { :host { background: #101c22; color: #e1ebed; } .call-header { border-color: #293b42; } .brand { color: #e1ebed; } .brand small, .call-status, .privacy-note { color: #a0b2b8; } .chat-panel, .control { background: #19292f; border-color: #304249; } .chat-heading, .chat-form { border-color: #304249; } .chat-heading p, .empty-chat { color: #a0b2b8; } .message { background: #26383e; } .message.own-message { background: #17453e; } .message > span { color: #b7cfcb; } .chat-form input { border-color: #40535a; background: #142229; color: #e1ebed; } }
  `]
})
export class TeleconsultationComponent implements OnInit, OnDestroy {
  @ViewChild('localVideo') private localVideoElement?: ElementRef<HTMLVideoElement>;
  @ViewChild('remoteVideo') private remoteVideoElement?: ElementRef<HTMLVideoElement>;

  readonly status = signal('Prêt à rejoindre');
  readonly error = signal('');
  readonly connected = signal(false);
  readonly joining = signal(false);
  readonly inCall = signal(false);
  readonly cameraEnabled = signal(true);
  readonly microphoneEnabled = signal(true);
  readonly sharingScreen = signal(false);
  readonly localStream = signal<MediaStream | null>(null);
  readonly remoteStream = signal<MediaStream | null>(null);
  readonly messages = signal<ChatMessage[]>([]);
  readonly chatDraft = signal('');
  readonly doctorMode = signal(false);
  readonly activeMedicalTab = signal<'chat' | 'dossier' | 'notes' | 'ordonnance'>('chat');
  readonly patientRecord = signal<DoctorPatientDetail | null>(null);
  readonly dossierLoading = signal(false);
  readonly notesLoading = signal(false);
  readonly prescriptionLoading = signal(false);
  readonly noteSaved = signal(false);
  readonly savingConsultationNotes = signal(false);
  readonly prescriptionRecord = signal<Prescription | null>(null);
  readonly prescriptionSaved = signal(false);
  readonly savingPrescription = signal(false);

  consultationReason = '';
  privateNotes = '';
  sharedNotes = '';
  diagnosis = '';
  prescriptionText = '';

  private appointmentId = '';
  private userId = '';
  private remotePeerId = '';
  private client?: Client;
  private subscription?: StompSubscription;
  private peerConnection?: RTCPeerConnection;
  private screenStream?: MediaStream;
  private readonly pendingCandidates: RTCIceCandidateInit[] = [];
  private nextMessageId = 0;
  private makingOffer = false;
  private ignoreOffer = false;
  private settingRemoteAnswer = false;
  private consultationAutoSaveTimer?: number;
  private prescriptionAutoSaveTimer?: number;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly tokenStore: AccessTokenStore,
    private readonly auth: AuthService,
    private readonly api: TelemedApiService,
    @Inject(TELEMED_API_BASE_URL) private readonly apiBaseUrl: string,
    @Inject(TELEMED_ICE_SERVERS) private readonly iceServers: RTCIceServer[]
  ) {}

  ngOnInit(): void {
    this.appointmentId = this.route.snapshot.paramMap.get('appointmentId') ?? '';
    if (!this.appointmentId) {
      this.error.set('Le rendez-vous demandé est introuvable.');
      return;
    }
    const token = this.tokenStore.getAccessToken();
    if (!token) {
      this.error.set('Connectez-vous pour accéder à la téléconsultation.');
      this.status.set('Authentification requise');
      return;
    }
    const roles = this.auth.claims()?.roles ?? [];
    this.doctorMode.set(roles.includes('DOCTOR'));
    this.userId = this.readUserId(token);
    if (this.doctorMode()) {
      void this.loadDoctorMedicalData();
    }
  }

  ngOnDestroy(): void {
    this.publishLeave();
    this.cleanupMedia();
    void this.client?.deactivate();
    if (this.consultationAutoSaveTimer) {
      window.clearTimeout(this.consultationAutoSaveTimer);
    }
    if (this.prescriptionAutoSaveTimer) {
      window.clearTimeout(this.prescriptionAutoSaveTimer);
    }
  }

  private async loadDoctorMedicalData(): Promise<void> {
    if (!this.doctorMode()) {
      return;
    }
    this.dossierLoading.set(true);
    this.notesLoading.set(true);
    this.prescriptionLoading.set(true);
    try {
      const appointments = await firstValueFrom(this.api.doctorAppointments());
      const appointment = appointments.find(item => item.id === this.appointmentId);
      if (!appointment) {
        this.error.set('Le rendez-vous médical n’a pas été retrouvé pour ce médecin.');
        return;
      }
      const patient = await firstValueFrom(this.api.doctorPatient(appointment.patientId));
      this.patientRecord.set(patient);

      const consultation = await firstValueFrom(this.api.doctorConsultation(this.appointmentId));
      if (consultation) {
        this.privateNotes = consultation.observations ?? '';
        this.sharedNotes = consultation.report ?? '';
        this.diagnosis = consultation.diagnosis ?? '';
        this.consultationReason = consultation.report?.split(/\s+/).slice(0, 12).join(' ') || '';
      } else {
        this.privateNotes = '';
        this.sharedNotes = '';
        this.diagnosis = '';
        this.consultationReason = '';
      }
      this.noteSaved.set(true);

      const prescription = await firstValueFrom(this.api.doctorPrescription(this.appointmentId));
      if (prescription) {
        this.prescriptionRecord.set(prescription);
        this.prescriptionText = prescription.content;
        this.prescriptionSaved.set(prescription.status === 'VALIDATED');
      } else {
        this.prescriptionRecord.set(null);
        this.prescriptionText = '';
        this.prescriptionSaved.set(false);
      }
    } catch {
      this.error.set('Impossible de charger le dossier médical et les notes de cette consultation.');
    } finally {
      this.dossierLoading.set(false);
      this.notesLoading.set(false);
      this.prescriptionLoading.set(false);
    }
  }

  scheduleConsultationSave(): void {
    if (!this.doctorMode()) {
      return;
    }
    this.noteSaved.set(false);
    window.clearTimeout(this.consultationAutoSaveTimer);
    this.consultationAutoSaveTimer = window.setTimeout(() => {
      void this.saveConsultationNotes();
    }, 900);
  }

  saveConsultationNotes(): Promise<void> {
    if (!this.doctorMode()) {
      return Promise.resolve();
    }
    const payload = {
      report: this.sharedNotes.trim() || null,
      observations: this.privateNotes.trim() || null,
      diagnosis: this.diagnosis.trim() || null,
      finalized: false
    };
    this.savingConsultationNotes.set(true);
    return firstValueFrom(this.api.saveDoctorConsultation(this.appointmentId, payload))
      .then(consultation => {
        this.noteSaved.set(true);
        this.consultationReason = consultation.report?.trim() || this.consultationReason || '';
        this.status.set('Compte-rendu enregistré');
      })
      .catch(() => {
        this.noteSaved.set(false);
        this.error.set('La sauvegarde du compte-rendu a échoué.');
      })
      .finally(() => {
        this.savingConsultationNotes.set(false);
      });
  }

  schedulePrescriptionSave(): void {
    if (!this.doctorMode()) {
      return;
    }
    const current = this.prescriptionRecord();
    if (current && current.status !== 'DRAFT') {
      this.prescriptionSaved.set(true);
      this.status.set('Ordonnance déjà validée — modification désactivée.');
      return;
    }
    this.prescriptionSaved.set(false);
    window.clearTimeout(this.prescriptionAutoSaveTimer);
    this.prescriptionAutoSaveTimer = window.setTimeout(() => {
      void this.savePrescriptionDraft();
    }, 900);
  }

  savePrescriptionDraft(): Promise<void> {
    if (!this.doctorMode() || !this.prescriptionText.trim()) {
      return Promise.resolve();
    }
    const existing = this.prescriptionRecord();
    if (existing && existing.status !== 'DRAFT') {
      this.prescriptionSaved.set(true);
      return Promise.resolve();
    }
    this.savingPrescription.set(true);
    const request = firstValueFrom(existing
      ? this.api.updateDoctorPrescription(existing.id, this.prescriptionText.trim())
      : this.api.createDoctorPrescription(this.appointmentId, this.prescriptionText.trim()));
    return request.then(updated => {
      this.prescriptionRecord.set(updated);
      this.prescriptionSaved.set(updated.status === 'VALIDATED');
      this.status.set('Ordonnance enregistrée');
    }).catch(error => {
      const backendMessage = error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
        ? error.error.message
        : undefined;
      this.error.set(backendMessage || (error instanceof HttpErrorResponse && error.status === 400
        ? 'L’ordonnance ne peut pas être enregistrée dans l’état actuel du rendez-vous.'
        : 'L’enregistrement de l’ordonnance a échoué.'));
    }).finally(() => {
      this.savingPrescription.set(false);
    });
  }

  validatePrescriptionDraft(): void {
    const current = this.prescriptionRecord();
    if (!this.doctorMode() || !this.prescriptionText.trim()) {
      return;
    }
    if (current && current.status === 'VALIDATED') {
      this.prescriptionSaved.set(true);
      this.status.set('Ordonnance déjà validée.');
      return;
    }
    if (!current) {
      void this.savePrescriptionDraft().then(() => this.validatePrescriptionDraft());
      return;
    }
    if (this.savingPrescription()) {
      return;
    }
    this.savingPrescription.set(true);
    this.api.validateDoctorPrescription(current.id).subscribe({
      next: updated => {
        this.prescriptionRecord.set(updated);
        this.prescriptionSaved.set(true);
        this.status.set('Ordonnance validée et disponible pour le patient');
        this.prescriptionText = updated.content;
        this.savingPrescription.set(false);
      },
      error: error => {
        this.savingPrescription.set(false);
        const backendMessage = error instanceof HttpErrorResponse && typeof error.error?.message === 'string'
          ? error.error.message
          : undefined;
        this.error.set(backendMessage || 'La validation de l’ordonnance a échoué.');
      }
    });
  }

  previewPrescriptionPdf(): void {
    const current = this.prescriptionRecord();
    if (!current) {
      this.error.set('Enregistrez d’abord l’ordonnance pour générer le PDF.');
      return;
    }
    if (current.status !== 'VALIDATED') {
      this.error.set('Validez d’abord l’ordonnance pour générer le PDF.');
      return;
    }
    this.api.doctorPrescriptionPdf(current.id).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `ordonnance-${current.id}.pdf`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      },
      error: () => this.error.set('Le PDF de l’ordonnance n’a pas pu être généré.')
    });
  }

  patientInitials(value: string | null | undefined): string {
    if (!value) {
      return 'P';
    }
    const seed = value.replace(/[^a-zA-Z0-9]/g, ' ').trim();
    const words = seed.split(/\s+/).filter(Boolean).slice(0, 2);
    if (words.length === 0) {
      return 'P';
    }
    return words.map(word => word.charAt(0).toUpperCase()).join('').slice(0, 2);
  }

  async joinCall(): Promise<void> {
    const token = this.tokenStore.getAccessToken();
    if (!token || !this.userId) {
      this.error.set('Connectez-vous pour accéder à la téléconsultation.');
      return;
    }
    this.error.set('');
    this.joining.set(true);
    this.status.set('Vérification du rendez-vous…');
    try {
      await firstValueFrom(this.api.checkTeleconsultationAccess(this.appointmentId));
      this.status.set('Activation de la caméra et du microphone…');
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: true });
      this.localStream.set(stream);
      this.cameraEnabled.set(true);
      this.microphoneEnabled.set(true);
      this.bindVideoElements();
      this.createPeerConnection();
      await this.connectSignaling(token);
      this.inCall.set(true);
      this.status.set('En attente de l’autre participant');
    } catch (error) {
      await this.client?.deactivate();
      this.client = undefined;
      this.cleanupMedia();
      if (error instanceof HttpErrorResponse && (error.status === 403 || error.status === 404)) {
        const message = error.error && typeof error.error.message === 'string'
          ? error.error.message
          : 'Ce compte n’est pas autorisé à rejoindre cette téléconsultation.';
        this.error.set(`Accès refusé : ${message}`);
      } else if (error instanceof DOMException && error.name === 'NotAllowedError') {
        this.error.set('Autorisez l’accès à la caméra et au microphone, puis réessayez.');
      } else if (error instanceof DOMException && error.name === 'NotFoundError') {
        this.error.set('Aucune caméra ou aucun microphone compatible n’a été détecté.');
      } else {
        this.error.set('Impossible de rejoindre la salle. Vérifiez les autorisations caméra/micro et la connexion au serveur.');
      }
      this.status.set('Connexion impossible');
    } finally {
      this.joining.set(false);
    }
  }

  toggleCamera(): void {
    const enabled = !this.cameraEnabled();
    this.localStream()?.getVideoTracks().forEach(track => track.enabled = enabled);
    this.cameraEnabled.set(enabled);
  }

  toggleMicrophone(): void {
    const enabled = !this.microphoneEnabled();
    this.localStream()?.getAudioTracks().forEach(track => track.enabled = enabled);
    this.microphoneEnabled.set(enabled);
  }

  async toggleScreenShare(): Promise<void> {
    if (this.sharingScreen()) {
      this.restoreCameraTrack();
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getDisplayMedia({ video: true });
      this.screenStream = stream;
      const screenTrack = stream.getVideoTracks()[0];
      const videoSender = this.peerConnection?.getSenders().find(sender => sender.track?.kind === 'video');
      if (!screenTrack || !videoSender) {
        throw new Error('A video sender is not available.');
      }
      await videoSender.replaceTrack(screenTrack);
      this.sharingScreen.set(true);
      screenTrack.onended = () => this.restoreCameraTrack();
    } catch {
      this.screenStream?.getTracks().forEach(track => track.stop());
      this.screenStream = undefined;
      this.error.set('Le partage d’écran n’a pas pu démarrer.');
    }
  }

  sendChat(event: Event): void {
    event.preventDefault();
    const text = this.chatDraft().trim();
    if (!text || text.length > 2000 || !this.connected()) {
      return;
    }
    if (!this.publishSignal('CHAT', text)) {
      return;
    }
    this.addMessage('Vous', text);
    this.chatDraft.set('');
  }

  updateChatDraft(event: Event): void {
    this.chatDraft.set((event.target as HTMLInputElement).value);
  }

  async endCall(): Promise<void> {
    this.publishLeave();
    this.cleanupMedia();
    this.inCall.set(false);
    this.connected.set(false);
    this.status.set('Appel terminé');
    await this.client?.deactivate();
    this.client = undefined;
  }

  private async connectSignaling(token: string): Promise<void> {
    this.client = new Client({
      brokerURL: this.websocketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 0,
      debug: () => undefined
    });
    await new Promise<void>((resolve, reject) => {
      if (!this.client) {
        reject(new Error('The signaling client was not created.'));
        return;
      }
      const client = this.client;
      client.onConnect = () => {
        this.connected.set(true);
        this.subscription = client.subscribe(
          `/topic/teleconsultations/${this.appointmentId}`,
          message => this.onRoomEvent(message)
        );
        client.publish({ destination: `/app/teleconsultations/${this.appointmentId}/join`, body: '{}' });
        resolve();
      };
      client.onStompError = () => {
        this.connected.set(false);
        this.error.set('Accès à la salle refusé. Vérifiez le rendez-vous et son horaire.');
        reject(new Error('The signaling server rejected the connection.'));
      };
      client.onWebSocketError = () => {
        this.connected.set(false);
        this.error.set('Le serveur de téléconsultation est indisponible.');
        reject(new Error('The signaling WebSocket could not connect.'));
      };
      client.onWebSocketClose = () => {
        this.connected.set(false);
        if (this.inCall()) {
          this.status.set('Connexion au serveur interrompue');
          this.error.set('La signalisation a été interrompue. Quittez puis rejoignez à nouveau la salle.');
        } else {
          reject(new Error('The signaling WebSocket closed before connecting.'));
        }
      };
      client.activate();
    });
  }

  private createPeerConnection(): void {
    const connection = new RTCPeerConnection({
      iceServers: this.iceServers
    });
    this.peerConnection = connection;
    const stream = this.localStream();
    if (!stream) {
      throw new Error('Local media must be initialized before creating the peer connection.');
    }
    stream.getTracks().forEach(track => connection.addTrack(track, stream));
    connection.onicecandidate = event => {
      if (event.candidate) {
        this.publishSignal('ICE_CANDIDATE', event.candidate.toJSON());
      }
    };
    connection.ontrack = event => {
      const stream = event.streams[0];
      if (stream) {
        this.remoteStream.set(stream);
        this.bindVideoElements();
      }
    };
    connection.onconnectionstatechange = () => {
      if (connection.connectionState === 'connected') {
        this.status.set('Consultation en cours');
      } else if (connection.connectionState === 'failed') {
        this.error.set('La connexion vidéo a échoué. Vérifiez votre réseau ou le serveur TURN configuré.');
      }
    };
  }

  private onRoomEvent(message: IMessage): void {
    let event: RoomEvent;
    try {
      event = JSON.parse(message.body) as RoomEvent;
    } catch {
      this.error.set('Un message de signalisation invalide a été reçu.');
      return;
    }
    if (event.appointmentId !== this.appointmentId || event.senderId === this.userId) {
      return;
    }
    if (event.type === 'JOIN') {
      this.remotePeerId = event.senderId;
      this.status.set('L’autre participant a rejoint');
      if (!this.peerConnection && this.localStream()) {
        this.createPeerConnection();
      }
      void this.createOffer();
    } else if (event.type === 'LEAVE') {
      this.status.set('L’autre participant a quitté la salle');
      this.remoteStream.set(null);
      if (this.remoteVideoElement) {
        this.remoteVideoElement.nativeElement.srcObject = null;
      }
      this.peerConnection?.close();
      this.peerConnection = undefined;
      this.remotePeerId = '';
      this.pendingCandidates.length = 0;
      this.makingOffer = false;
      this.ignoreOffer = false;
      this.settingRemoteAnswer = false;
    } else if (event.type === 'OFFER') {
      void this.receiveOffer(event.payload as RTCSessionDescriptionInit);
    } else if (event.type === 'ANSWER') {
      void this.receiveAnswer(event.payload as RTCSessionDescriptionInit);
    } else if (event.type === 'ICE_CANDIDATE') {
      void this.receiveCandidate(event.payload as RTCIceCandidateInit);
    } else if (event.type === 'CHAT' && typeof event.payload === 'string') {
      this.addMessage('Participant', event.payload);
    }
  }

  private async createOffer(): Promise<void> {
    if (!this.peerConnection || this.peerConnection.signalingState !== 'stable') {
      return;
    }
    try {
      this.makingOffer = true;
      const offer = await this.peerConnection.createOffer();
      await this.peerConnection.setLocalDescription(offer);
      this.publishSignal('OFFER', this.peerConnection.localDescription?.toJSON());
    } catch {
      this.error.set('Impossible de démarrer la connexion vidéo.');
    } finally {
      this.makingOffer = false;
    }
  }

  private async receiveOffer(offer: RTCSessionDescriptionInit): Promise<void> {
    if (!this.peerConnection || !offer) {
      return;
    }
    try {
      const connection = this.peerConnection;
      const offerCollision = this.makingOffer
        || (connection.signalingState !== 'stable' && !this.settingRemoteAnswer);
      this.ignoreOffer = !this.isPolitePeer() && offerCollision;
      if (this.ignoreOffer) {
        return;
      }
      if (offerCollision) {
        await connection.setLocalDescription({ type: 'rollback' });
      }
      await this.peerConnection.setRemoteDescription(offer);
      await this.flushCandidates();
      const answer = await this.peerConnection.createAnswer();
      await this.peerConnection.setLocalDescription(answer);
      this.publishSignal('ANSWER', this.peerConnection.localDescription?.toJSON());
    } catch {
      this.error.set('La connexion vidéo n’a pas pu être négociée.');
    }
  }

  private async receiveAnswer(answer: RTCSessionDescriptionInit): Promise<void> {
    if (!this.peerConnection || !answer) {
      return;
    }
    try {
      this.settingRemoteAnswer = true;
      await this.peerConnection.setRemoteDescription(answer);
      await this.flushCandidates();
    } catch {
      this.error.set('La réponse vidéo est invalide.');
    } finally {
      this.settingRemoteAnswer = false;
    }
  }

  private async receiveCandidate(candidate: RTCIceCandidateInit): Promise<void> {
    if (!this.peerConnection || !candidate) {
      return;
    }
    if (this.ignoreOffer) {
      return;
    }
    if (!this.peerConnection.remoteDescription) {
      this.pendingCandidates.push(candidate);
      return;
    }
    try {
      await this.peerConnection.addIceCandidate(candidate);
    } catch {
      this.error.set('Un candidat réseau vidéo n’a pas pu être appliqué.');
    }
  }

  private async flushCandidates(): Promise<void> {
    const connection = this.peerConnection;
    if (!connection) {
      return;
    }
    for (const candidate of this.pendingCandidates.splice(0)) {
      await connection.addIceCandidate(candidate);
    }
  }

  private publishSignal(type: string, payload?: unknown): boolean {
    if (!this.connected() || !this.client?.active) {
      return false;
    }
    this.client.publish({
      destination: `/app/teleconsultations/${this.appointmentId}/signal`,
      body: JSON.stringify({ type, payload: payload ?? null })
    });
    return true;
  }

  private publishLeave(): void {
    if (this.connected() && this.client?.active) {
      this.client.publish({ destination: `/app/teleconsultations/${this.appointmentId}/leave`, body: '{}' });
    }
  }

  private restoreCameraTrack(): void {
    const cameraTrack = this.localStream()?.getVideoTracks()[0];
    const videoSender = this.peerConnection?.getSenders().find(sender => sender.track?.kind === 'video');
    if (cameraTrack && videoSender) {
      void videoSender.replaceTrack(cameraTrack).catch(() => {
        this.error.set('La caméra n’a pas pu reprendre après le partage d’écran.');
      });
    }
    this.screenStream?.getTracks().forEach(track => track.stop());
    this.screenStream = undefined;
    this.sharingScreen.set(false);
  }

  private isPolitePeer(): boolean {
    return this.userId.localeCompare(this.remotePeerId) > 0;
  }

  private addMessage(sender: ChatMessage['sender'], text: string): void {
    this.messages.update(messages => [...messages, {
        id: this.nextMessageId++,
        sender,
        text,
        sentAt: new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' }).format(new Date())
      }].slice(-200));
  }

  private bindVideoElements(): void {
    if (this.localVideoElement) {
      this.localVideoElement.nativeElement.srcObject = this.localStream();
    }
    if (this.remoteVideoElement) {
      this.remoteVideoElement.nativeElement.srcObject = this.remoteStream();
    }
  }

  private readUserId(token: string): string {
    try {
      const payload = token.split('.')[1];
      const claims = JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/'))) as { sub?: string };
      return claims.sub ?? '';
    } catch {
      this.error.set('La session de connexion est invalide. Reconnectez-vous.');
      return '';
    }
  }

  private websocketUrl(): string {
    const url = new URL('/ws', this.apiBaseUrl);
    url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
    return url.toString();
  }

  private cleanupMedia(): void {
    this.subscription?.unsubscribe();
    this.subscription = undefined;
    this.peerConnection?.close();
    this.peerConnection = undefined;
    this.localStream()?.getTracks().forEach(track => track.stop());
    this.screenStream?.getTracks().forEach(track => track.stop());
    this.screenStream = undefined;
    this.localStream.set(null);
    this.remoteStream.set(null);
    this.sharingScreen.set(false);
    this.cameraEnabled.set(false);
    this.microphoneEnabled.set(false);
  }
}
