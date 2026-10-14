# Plateforme de télémédecine

Socle de plateforme de télémédecine avec Angular, Spring Boot, PostgreSQL et Flyway. L'API sépare contrôleurs, services, repositories, DTO et mappers MapStruct ; aucune entité JPA n'est renvoyée directement.

## Prérequis

- Node.js 22.12+ (ou 24 LTS) et npm
- Java 21 et Maven 3.9+
- PostgreSQL 16+ (ou Docker)

## Démarrage rapide

1. Démarrer PostgreSQL :

   ```powershell
   docker compose up -d db
   ```

2. Démarrer l'API (PowerShell) :

   ```powershell
   $env:DB_URL = "jdbc:postgresql://localhost:5433/basetélé"
   $env:DB_USERNAME = basetélé
   $env:DB_PASSWORD = ritej
   $env:JWT_SECRET = 
   $env:PRESCRIPTION_SIGNING_SECRET = "remplace-par-une-valeur-secrète-d-au-moins-32-octets"
   $env:BOOTSTRAP_ADMIN_EMAIL = "admin@gmail.com"
   $env:BOOTSTRAP_ADMIN_PASSWORD = "RemplaceParUnMotDePasseFortDe12A72Octets"
   cd backend
   mvn spring-boot:run
   ```

   Le secret JWT et le secret de signature des ordonnances doivent contenir au moins 32 octets. Configurez l'adresse `admin@gmail.com` et son mot de passe bootstrap (12 à 72 octets). Au premier démarrage, le compte ADMIN est créé uniquement si cette adresse n'existe pas ; si elle est déjà utilisée par un compte non administrateur, l'application refuse de démarrer plutôt que d'élever silencieusement ses droits. Un compte ADMIN existant et son mot de passe ne sont pas modifiés. En production, fournissez ces valeurs depuis un gestionnaire de secrets, sans les stocker dans le dépôt.

3. Démarrer Angular dans un autre terminal :

   ```powershell
   cd frontend
   npm install
   npm start
   ```

   L'interface est disponible sur <http://localhost:4200> et l'API sur <http://localhost:8080>.

   Les parcours Angular incluent l'accueil, l'inscription/connexion, les tableaux de bord patient, médecin et administrateur, ainsi que la réservation mensuelle des créneaux libres. Le navigateur conserve les jetons dans `sessionStorage`; les routes patient/médecin/administrateur sont protégées par le rôle du jeton et chaque appel API envoie le jeton d'accès. Après connexion, `admin@gmail.com` ouvre l'espace ADMIN (`/admin`) pour consulter les statistiques, gérer les comptes et rendez-vous, valider les médecins et consulter le journal d'audit. L'espace patient permet de réserver, consulter ses ordonnances et annuler ses rendez-vous actifs. L'espace médecin s'appuie sur les API sécurisées pour consulter les statistiques, gérer son agenda et ses indisponibilités, suivre ses patients autorisés, rédiger des comptes rendus, gérer les ordonnances et retrouver ses notifications.

Sans Docker, créez une base PostgreSQL `telemed` et un utilisateur `telemed`, puis fournissez `DB_URL`, `DB_USERNAME` et `DB_PASSWORD`.

## Structure

```text
backend/   API Spring Boot, sécurité, configuration PostgreSQL et tests
frontend/  application Angular standalone, Angular Material et Tailwind CSS
```

## API

Les routes `/api/v1/patient/**`, `/api/v1/doctor/**` et `/api/v1/admin/**` sont respectivement réservées aux rôles `PATIENT`, `DOCTOR` et `ADMIN`, en complément des contrôles d'accès métier des contrôleurs et services. La salle de téléconsultation est réservée aux patients et médecins authentifiés participant au rendez-vous.

- `POST /api/v1/auth/login` : connexion par e-mail/mot de passe ; retourne un access token (15 min) et un refresh token (14 jours).
- `POST /api/v1/auth/register` : inscription publique d'un compte patient uniquement ; elle crée un profil sans dossier médical et retourne les jetons de session. La connexion et l'inscription sont limitées ensemble à cinq requêtes par minute et par adresse réseau observée.
- `POST /api/v1/auth/register/doctor` : dépôt public d'une demande de compte médecin (`email`, `password`, `specialty`, `bio`). Le compte est désactivé jusqu'à validation administrative et ne reçoit aucun jeton à l'inscription.
- `GET /api/v1/admin/accounts/doctors/pending` : liste les demandes médecins en attente, réservée à `ADMIN`.
- `PATCH /api/v1/admin/accounts/doctors/{doctorId}/approve` : approuve une demande et active l'accès médecin, réservée à `ADMIN`.
- `/api/v1/admin/dashboard/stats`, `/doctors`, `/patients`, `/appointments` et `/audit-logs` : statistiques, listes paginées/filtrables et journal d'activité, réservés à `ADMIN`.
- `PATCH /api/v1/admin/doctors/{doctorId}/verify`, `/reject` et `/status`, ainsi que `/api/v1/admin/patients/{patientId}/status` : validation/refus des médecins et suspension/réactivation des comptes. Les actions administratives sont auditées.
- `POST /api/v1/auth/refresh` : renouvelle et fait tourner le refresh token ; seul son hash est conservé.
- `POST /api/v1/auth/logout` : révoque le refresh token fourni.
- `POST /api/v1/admin/accounts/doctors` et `/patients` : création des comptes et profils, réservée à `ADMIN`.
- `/api/v1/doctor/availabilities` et `/api/v1/doctor/appointments` : gestion médecin.
- `POST /api/v1/doctor/availabilities` définit une plage hebdomadaire avec sa durée de créneau (`slotDurationMinutes`, 5 à 240 minutes) ; `GET` retourne les plages du médecin connecté.
- `PUT` et `DELETE /api/v1/doctor/availabilities/{id}` modifient ou suppriment une plage. L'API refuse les changements qui rendraient incompatibles des rendez-vous actifs déjà réservés.
- `/api/v1/doctor/availability-exceptions` enregistre les congés et indisponibilités exceptionnelles, sur une journée ou une plage horaire ; la réservation filtre ces dates et périodes.
- `GET /api/v1/doctor/dashboard/stats` fournit des statistiques calculées pour le médecin authentifié et dans le fuseau de rendez-vous configuré.
- `PATCH /api/v1/doctor/appointments/{id}/reschedule` replanifie un rendez-vous futur en vérifiant les disponibilités, exceptions, réservations concurrentes et conflits. Le patient reçoit une notification par e-mail via l'outbox existante.
- `GET /api/v1/doctor/patients` retourne une liste paginée limitée aux patients ayant un rendez-vous confirmé ou terminé avec ce médecin ; `GET /api/v1/doctor/patients/{id}` vérifie la même relation avant de retourner le dossier médical et l'historique autorisé.
- `GET` et `PUT /api/v1/doctor/appointments/{id}/consultation` enregistrent le compte rendu, les observations et le diagnostic, avec une révision conservée à chaque sauvegarde. Une consultation finalisée devient en lecture seule et termine le rendez-vous.
- `/api/v1/doctor/profile` permet au médecin de gérer les informations de contact sans changer son rôle, son statut de vérification ou le statut de vérification de son numéro professionnel.
- `/api/v1/doctor/notifications` retourne les notifications du médecin connecté et permet de marquer une notification comme lue ; les rappels in-app sont créés pour les rendez-vous proches.
- `GET /api/v1/doctor/appointments/{id}/prescription` permet de retrouver l'ordonnance liée au rendez-vous ; la rédaction reste manuelle et la validation/signature existante demeure requise.
- `GET /api/v1/patient/doctors` retourne l'annuaire des médecins et leurs spécialités, sans exposer les comptes utilisateur.
- `GET /api/v1/patient/appointments/doctors/{doctorId}/slots?from=YYYY-MM-DD&to=YYYY-MM-DD` retourne uniquement les créneaux libres, pour une période maximale de 31 jours.
- `POST /api/v1/patient/appointments` réserve un début de créneau exact ; une annulation médecin/patient libère le créneau.
- `/api/v1/patient/appointments` et `/api/v1/patient/prescriptions` : gestion patient ; seules les ordonnances validées sont retournées.
- `POST /api/v1/doctor/appointments/{appointmentId}/prescription/draft` : génère un brouillon d'ordonnance à partir des notes, après anonymisation côté serveur, pendant la plage d'une consultation confirmée ou après un rendez-vous terminé.
- `PUT /api/v1/doctor/prescriptions/{id}` : modification du brouillon ; `GET .../{id}/history` : historique des révisions, réservé au médecin traitant.
- `PATCH /api/v1/doctor/prescriptions/{id}/validate` : relecture/validation et signature par le médecin ; PDF disponible à `/api/v1/doctor/prescriptions/{id}/pdf` et pour le patient concerné à `/api/v1/patient/prescriptions/{id}/pdf`.
- `GET /api/v1/health` : endpoint public ne retournant que l'état du service.
- `/teleconsultation/{appointmentId}` : salle vidéo Angular, accessible avec un jeton de session valide.

Les routes métier exigent `Authorization: Bearer <access_token>` et vérifient le rôle requis. Le secret JWT doit être fourni par environnement. Les mots de passe sont hachés avec BCrypt (facteur 12). La limite de connexion est de 5 tentatives par minute et par adresse réseau observée par le serveur ; le compteur est local au processus, à remplacer par un stockage partagé avant un déploiement multi-instance ou derrière un proxy. `CORS_ALLOWED_ORIGINS` accepte une liste d'origines exactes séparées par des virgules, sans joker ; les credentials cross-origin sont désactivés.

Les réservations sont transactionnelles. Un verrou optimiste versionné sur chaque créneau, des contraintes d'exclusion PostgreSQL contre les chevauchements de réservations et de plages de disponibilité, ainsi que les contraintes uniques, empêchent les doubles réservations et les horaires qui se chevauchent ; les conflits concurrents retournent HTTP 409. La migration `V3` utilise l'extension PostgreSQL `btree_gist`.

Les migrations additives `V6` et `V7` étendent le profil professionnel, les comptes rendus et leur historique, les indisponibilités datées, le centre de notifications et l'outbox de replanification. Elles conservent les comptes et rendez-vous déjà présents ; aucun effacement ni réinitialisation du schéma n'est requis.

Les confirmations sont placées dans une outbox transactionnelle à l'enregistrement ; un rappel est planifié une heure avant le rendez-vous si celui-ci est réservé plus d'une heure à l'avance. Les notifications sont envoyées par SMTP (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`) avec reprises et délai exponentiel. En cas d'annulation, les messages encore en attente sont supprimés de la file. Le fuseau horaire est configurable avec `APPOINTMENT_ZONE_ID` (défaut `Europe/Paris`). Configurez SMTP pour activer l'envoi réel.

Les dossiers patients et contenus d'ordonnance sont des données de santé : ils ne doivent jamais être écrits dans les journaux. Ce squelette ne met pas en œuvre le chiffrement applicatif des données au repos, l'audit réglementaire, le consentement, la politique complète de conservation ni l'infrastructure HDS ; ces contrôles restent indispensables avant une mise en production.

### Brouillons d'ordonnance assistés

La génération LLM est uniquement serveur et utilise une API compatible Chat Completions. Configurez `LLM_PRESCRIPTION_ENDPOINT` (HTTPS), `LLM_PRESCRIPTION_API_KEY` et `LLM_PRESCRIPTION_MODEL`. Si ces paramètres sont absents, la génération répond `503`. La génération est réservée au médecin du rendez-vous, soit pendant le créneau d'un rendez-vous confirmé, soit après un rendez-vous terminé. Aucun identifiant de compte, rendez-vous ou dossier patient n'est envoyé. `MedicalNoteAnonymizer` masque les identifiants courants (champs nom/patient, noms précédés d'un titre, date de naissance explicite, téléphone, e-mail, NIR et adresses) avant l'appel ; il ne constitue pas une garantie de désidentification parfaite. Le médecin doit vérifier le texte anonymisé et choisir un fournisseur/juridiction/contrat autorisé pour des données de santé avant tout déploiement.

La consigne LLM interdit d'inventer médicaments ou posologies et impose un brouillon clairement marqué comme non validé. Le brouillon n'est jamais visible dans l'espace patient. Le médecin peut l'éditer ; chaque création, génération, modification et validation est conservée comme révision avec l'identifiant du compte acteur. La validation enregistre un HMAC-SHA256 lié au contenu, au médecin et à l'instant de signature (`PRESCRIPTION_SIGNING_SECRET`, secret distinct d'au moins 32 octets), et l'export PDF vérifie le HMAC et l'empreinte du contenu. Cela fournit une attestation d'intégrité applicative, pas une signature électronique qualifiée ni un service de confiance réglementé.

### Téléconsultation vidéo

La route Angular `/teleconsultation/{appointmentId}` propose la caméra, le microphone, le partage d'écran, un chat temporaire en mémoire et la fin d'appel. L'application doit placer l'access token reçu du endpoint de connexion dans `sessionStorage` via `AccessTokenStore.setAccessToken()` ; il n'est pas passé dans l'URL. Le point `/ws` valide ce jeton sur `CONNECT`, puis limite les abonnements et messages STOMP aux deux participants liés au rendez-vous. Un participant ne peut rejoindre que pendant l'intervalle `[scheduledAt, scheduledAt + durationMinutes)` d'un rendez-vous `PENDING` ou `CONFIRMED`.

Les messages STOMP utilisent `/app/teleconsultations/{appointmentId}/{join|leave|signal}` et la salle `/topic/teleconsultations/{appointmentId}`. L'audio et la vidéo sont échangés en pair-à-pair via WebRTC ; le serveur ne les reçoit ni ne les stocke. Le chat et les descriptions de session transitent par la signalisation et ne sont pas persistés. Les origines STOMP doivent être configurées explicitement avec `CORS_ALLOWED_ORIGINS`. En production, terminez TLS (WSS/HTTPS), et fournissez un serveur TURN avec identifiants éphémères pour les réseaux restrictifs ; `TELEMED_API_BASE_URL` et `TELEMED_ICE_SERVERS` sont des tokens Angular surchargeables au bootstrap. La configuration par défaut n'inclut qu'un serveur STUN public et convient au développement, pas à une garantie de connectivité en production.

La plateforme reste un socle : chiffrement applicatif des dossiers au repos, audit réglementaire, consentement, conservation, infrastructure HDS, gestion de consentement caméra/micro et procédures opérationnelles restent nécessaires avant tout usage clinique.

## Tests

```powershell
cd backend
mvn test
```

```powershell
cd frontend
npm test -- --watch=false
```
