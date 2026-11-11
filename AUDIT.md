# Audit technique — plateforme de télémédecine

**Date :** 11 novembre 2026
**Périmètre :** code et configuration présents dans le workspace. Il s’agit d’un audit technique et fonctionnel, pas d’un audit réglementaire ni d’un test d’intrusion.

## Stack et architecture

- **Frontend :** Angular standalone 21, TypeScript 5.9, Angular Material, Tailwind CSS et RxJS. Le navigateur dialogue avec l’API REST ; les jetons sont conservés dans `sessionStorage`.
- **Backend :** Java 21, Spring Boot 3.5, Spring Security, API REST et STOMP/WebSocket. Les responsabilités sont réparties par domaines (authentification, rendez-vous, médecins, patients, ordonnances, etc.) ; MapStruct gère des mappings DTO.
- **Données :** PostgreSQL 16, JPA/Hibernate et migrations Flyway.
- **Fonctions transverses :** JWT HS256 avec access/refresh tokens, BCrypt, envoi SMTP via une outbox, PDFBox pour les ordonnances et WebRTC pair-à-pair avec STUN par défaut.
- **Hébergement :** aucune cible cloud ou configuration de production n’est définie. Docker Compose ne décrit que la base PostgreSQL.

## Vérifications d’exécution

| Vérification | Résultat |
|---|---|
| `npm run build` dans `frontend/` | Réussite. Bundle initial : 685,48 kB brut (sous le budget de 750 kB). Node.js 25.6 affiche un avertissement : version impaire, hors LTS ; le README demande Node 22.12+ ou 24 LTS. |
| `npm test` dans `frontend/` | Réussite : 2 tests, Chrome Headless, mode single-run. Le script npm a été ajusté car le Chrome interactif par défaut ne terminait pas correctement dans cet environnement. |
| `mvn test` dans `backend/` | Réussite : 63 tests, 0 échec. Avertissements Mockito/Byte Buddy sur le chargement dynamique d’agent Java et avertissement Commons Logging. |
| PostgreSQL neuf → Flyway → backend `prod` | Réussite sur une instance PostgreSQL 18 temporaire isolée : les 11 migrations s’appliquent, Hibernate valide le schéma et `GET /api/v1/health` retourne 200 avec les variables obligatoires configurées. |
| Interface locale | `ng serve` démarre. L’accueil et la connexion s’affichent ; aucune erreur JavaScript observée. À 390 px, ces deux pages ne présentent pas de débordement horizontal. Une connexion avec un compte fictif retourne le message d’erreur attendu. |
| API locale déjà active | `GET /api/v1/health` retourne 200 ; une tentative de connexion avec un compte fictif retourne 401, attendu. Les routes nécessitant une authentification ne sont pas parcourues avec un compte réel. |
| Dépendances d’exécution | Un PostgreSQL et une API préexistants étaient présents sur 5433/8080 ; ils n’ont pas été modifiés. Docker n’est pas disponible : le fichier Compose a été vérifié mais pas lancé. La validation des migrations et du démarrage a été faite sur un cluster PostgreSQL temporaire séparé. |

Les contrôles responsive et navigateur sont des vérifications ponctuelles, pas une couverture exhaustive de toutes les pages et tailles d’écran. Le parcours authentifié complet n’a pas été exécuté.

## A) Fonctionnalités qui ne fonctionnent pas ou blocages observés

| Page / fonctionnalité | Symptôme | Cause probable | Fichiers concernés | Gravité |
|---|---|---|---|---|
| Démarrage PostgreSQL documenté par Docker Compose | **Corrigé P0 :** le port hôte 5433 est maintenant mappé au port PostgreSQL 5432 du conteneur. | Le mapping de port hôte/interne a été corrigé et vérifié par revue de configuration ; Docker n’était pas disponible pour exécuter Compose. | [docker-compose.yml](docker-compose.yml), [README.md](README.md) | Haute — résolu |
| Connexion backend ↔ PostgreSQL au démarrage | **Corrigé P0 :** le nom de base et l’utilisateur sont alignés ; URL, utilisateur et mot de passe sont configurables par variables d’environnement et les secrets sont requis hors profil `dev`. | Les propriétés datasource et Compose ont été alignées ; les 11 migrations Flyway puis le backend ont été vérifiés sur une instance PostgreSQL temporaire neuve. | [application.yml](backend/src/main/resources/application.yml), [application-prod.yml](backend/src/main/resources/application-prod.yml), [docker-compose.yml](docker-compose.yml), [README.md](README.md) | Haute — résolu |
| Tests unitaires frontend | Les deux tests passent, mais le processus Karma/Chrome reste instable et ne sort pas avec un résultat propre. | Le navigateur lancé par Karma effectue un rechargement complet puis plante ou se déconnecte ; la cause précise n’a pas été isolée. | [dashboard.component.spec.ts](frontend/src/app/pages/dashboard/dashboard.component.spec.ts), configuration Angular dans [angular.json](frontend/angular.json) | Faible |

Sur les pages publiques vérifiées, aucun lien cassé, image manquante ou défaut responsive n’a été observé. Les fonctions qui exigent un compte (réservation, dossiers, téléconsultation et espaces métier) restent à valider de bout en bout avec des comptes de test.

## B) Fonctionnalités présentes mais incomplètes

| Fonctionnalité | État observé / limite |
|---|---|
| Cycle de session | Le backend fournit le renouvellement et la rotation des refresh tokens, et le frontend sait appeler l’endpoint de renouvellement. Toutefois, aucun parcours Angular ne l’appelle : après l’expiration de l’access token (15 min par défaut), les requêtes protégées échouent sans renouvellement automatique. Les access et refresh tokens sont accessibles au JavaScript dans `sessionStorage`. Voir [auth.service.ts](frontend/src/app/core/auth.service.ts) et [telemed-api.service.ts](frontend/src/app/core/telemed-api.service.ts). |
| Demande médecin | Les demandes et validations administratives existent. Le justificatif professionnel est un champ URL, pas un dépôt de fichier avec stockage et contrôle d’accès ; la vérification effective du document reste manuelle. Voir [auth.component.ts](frontend/src/app/pages/auth/auth.component.ts). |
| Téléconsultation | La salle WebRTC inclut audio/vidéo, signalisation et chat temporaire ; les messages ne sont pas conservés. STUN est configuré par défaut et aucun TURN n’est livré : des utilisateurs derrière certains pare-feux ou NAT ne pourront pas établir l’appel sans configuration d’exploitation. Pas de test caméra/micro avant entrée ni de partage de fichiers. Voir [teleconsultation.config.ts](frontend/src/app/pages/teleconsultation/teleconsultation.config.ts) et [teleconsultation.component.ts](frontend/src/app/pages/teleconsultation/teleconsultation.component.ts). |
| Notifications | Envoi d’e-mails de rendez-vous via SMTP/outbox et notifications in-app médecin présentes. L’envoi réel dépend de la configuration SMTP ; pas de SMS, push ni centre de notifications patient identifié. Voir [README.md](README.md). |
| Tableau de bord admin | Gestion des comptes, rendez-vous, validation médecins et journal d’activité sont présents. « Paramètres » indique qu’aucun paramètre métier n’est administrable ; « Mon profil » affiche essentiellement l’adresse et le rôle. Ce sont des vues informatives, pas des fonctions de gestion. Voir [admin-dashboard.component.ts](frontend/src/app/pages/admin-dashboard/admin-dashboard.component.ts). |
| Données de profil | Le tableau patient déduit le prénom de la partie locale de l’e-mail et ne propose pas de formulaire patient pour compléter ou gérer son profil médical. L’espace médecin présente des informations de profil, mais l’interface d’inscription ne permet que de fournir une URL de justificatif. Voir [patient-dashboard.component.ts](frontend/src/app/pages/patient-dashboard/patient-dashboard.component.ts) et [auth.component.ts](frontend/src/app/pages/auth/auth.component.ts). |
| Recherche et rendez-vous | La réservation comprend un calendrier mensuel, des créneaux libres, la recherche par nom/spécialité/ville et l’annulation patient. Le report est disponible côté médecin ; aucun report patient n’a été identifié. Les comparaisons d’horaires du frontend sont construites autour de `Europe/Paris`, alors que le fuseau est configurable côté backend. Voir [booking.component.ts](frontend/src/app/pages/booking/booking.component.ts), [patient-dashboard.component.ts](frontend/src/app/pages/patient-dashboard/patient-dashboard.component.ts) et [application.yml](backend/src/main/resources/application.yml). |
| Tests | La suite backend couvre plusieurs services et règles métier, mais ce sont des tests unitaires. Côté frontend, un seul fichier de test est présent ; aucune suite end-to-end ou d’intégration navigateur/API n’a été trouvée. |

Aucun marqueur `TODO` ou `FIXME` n’a été trouvé dans les sources Angular et Java parcourues. Cette recherche ne constitue pas une preuve d’absence de code mort ou de duplication.

## C) Fonctionnalités manquantes par rapport à une plateforme complète

| Domaine | Déjà présent | Manquant ou à compléter | Priorité indicative |
|---|---|---|---|
| Authentification et rôles | Inscription patient, demande médecin avec approbation admin, connexion, JWT, rôles patient/médecin/admin, logout et renouvellement API | Mot de passe oublié/réinitialisation, vérification e-mail/téléphone, 2FA, rôle secrétaire, parcours frontend de renouvellement du jeton | P1 |
| Profil patient | Compte patient et consultation du rendez-vous/ordonnance | Profil éditable, antécédents, allergies, traitements, coordonnées, consentements et pièces médicales gérés par le patient | P1 |
| Profil médecin | Spécialité, biographie, coordonnées, numéro professionnel, disponibilités et statut de vérification | Diplômes vérifiables, tarifs, langues, dépôt sécurisé des justificatifs et disponibilité/fuseau par praticien | P2 |
| Recherche médecin | Annuaire et filtres nom, spécialité et ville ; sélection d’une disponibilité | Filtres langue, tarif, note, disponibilité directement dans la recherche ; pages détaillées et avis vérifiés | P2 |
| Rendez-vous | Créneaux, réservation, annulation et replanification médecin ; e-mail/rappels configurables | Report patient, préférences de fuseau clairement gérées par utilisateur, SMS de rappel et gestion métier des absences côté patient | P1–P2 |
| Téléconsultation | Appel WebRTC 1:1, chat en session et partage d’écran | TURN exploité avec identifiants éphémères, test préalable caméra/micro, transfert sécurisé de fichiers et conservation/portabilité éventuelle des échanges selon politique de santé | P1–P2 |
| Dossier médical | Notes de consultation médecin avec historique de révisions et accès métier limité | Historique médical patient complet, résultats d’analyses, documents et pièces jointes, visualisation patient et export structuré | P1 |
| Ordonnances | Brouillon, validation, PDF, HMAC d’intégrité et historique de révisions | Service de signature électronique conforme au niveau juridique requis ; l’HMAC applicatif documenté ne constitue pas une signature qualifiée | P1 |
| Paiement | Aucun flux de paiement identifié | Prestataire adapté au pays (par ex. Stripe, Konnect, Paymee ou Flouci), factures, remboursements et gestion des échecs | P2 |
| Notifications et messagerie | E-mail rendez-vous, notifications in-app médecin, chat temporaire en salle vidéo | SMS/push, notifications patient et messagerie sécurisée asynchrone persistante patient ↔ médecin | P2 |
| Avis et notations | Aucun | Avis après consultation, modération et prévention des avis non vérifiés | P2 |
| Administration | Statistiques, listes comptes/rendez-vous, approbation médecins et journal d’activité | Paramètres métiers, gestion de rôles étendue/secrétariat, modération et workflows d’administration complétés | P2 |
| Pages institutionnelles | Page d’accueil | À propos, services, tarifs, FAQ, contact, blog, CGU, politique de confidentialité, mentions légales et consentement explicite à la téléconsultation | P1 pour les pages légales, P3 pour le contenu |
| Langues | Interface française | Anglais, arabe et support RTL si les utilisateurs ciblés le requièrent | P2 |
| Urgences | Aucun avertissement ou parcours d’urgence identifié | Message visible indiquant que la plateforme n’est pas un service d’urgence et numéro local approprié | P1 |

## D) Sécurité, conformité et qualité

### Contrôles présents

- Mots de passe hachés avec BCrypt (facteur 12), access tokens courts, refresh tokens stockés sous forme hachée et rotation côté backend.
- API stateless, rôles contrôlés par Spring Security et contrôles métier par ressource ; les contrôleurs WebSocket revérifient l’appartenance au rendez-vous avant de transmettre les signaux.
- Origines CORS explicites, sans wildcard ni credentials cross-origin ; validation serveur via Jakarta Validation et requêtes JPA/JPQL paramétrées dans les parties inspectées.
- Contraintes transactionnelles/concurrentes de réservation et tests backend unitaires présents.
- Le frontend utilise les interpolations Angular standard ; aucune utilisation directe de `innerHTML` n’a été repérée dans les sources inspectées.

### Risques et écarts

- **Critique — secrets précédemment codés en dur / historique Git.** Les valeurs fixes JWT, HMAC et datasource ont été retirées de la configuration de travail ; Compose exige maintenant le mot de passe depuis `.env`, ignoré par Git. Une clé HMAC connue permettait de fabriquer des attestations d’intégrité valides pour cette installation. Les anciennes valeurs peuvent toujours exister dans les commits précédents : il faut les faire tourner et purger l’historique Git avant diffusion. Le rapport ne reproduit aucune valeur sensible.
- **Haute — configuration de développement dangereuse si promue en production.** Le Compose contient des identifiants statiques et le dépôt ne définit ni profil de production, ni déploiement TLS/HTTPS, ni hébergement certifié santé. L’accès aux données en transit doit être imposé par l’infrastructure ; la présence de CORS ne remplace pas TLS.
- **Critique avant tout usage clinique — données de santé au repos et conformité.** Le README reconnaît l’absence de chiffrement applicatif des données au repos, de consentement complet et de politique de conservation. Aucun parcours complet de consentement, suppression ni export des données personnelles n’a été identifié. Évaluer les obligations de la loi tunisienne n° 2004-63 et les exigences de l’INPDP, ainsi que le RGPD si des résidents européens sont concernés, avec conseil juridique et hébergement adapté.
- **Haute — journalisation et erreurs (corrigé P0).** Le gestionnaire retourne maintenant un message générique pour `DataIntegrityViolationException` et journalise uniquement le type d’exception inattendue, sans contenu ni message d’erreur susceptible de contenir des données de santé. Voir [GlobalExceptionHandler.java](backend/src/main/java/com/telemed/platform/common/exception/GlobalExceptionHandler.java).
- **Moyenne — limitation de débit non distribuée.** La limite de connexion/inscription est conservée en mémoire, par instance, et repose sur l’adresse réseau observée. Elle ne constitue pas une limite cohérente en multi-instance ; derrière un proxy, l’adresse cliente doit être établie par une configuration de proxy de confiance. Voir [LoginRateLimitFilter.java](backend/src/main/java/com/telemed/platform/auth/security/LoginRateLimitFilter.java).
- **Moyenne — exposition des jetons au JavaScript.** Les jetons dans `sessionStorage` seraient accessibles en cas de faille XSS. Aucune faille XSS directe n’a été démontrée durant cet audit ; durcir CSP/headers et réévaluer le stockage de session (par exemple cookie `HttpOnly`, selon l’architecture retenue).
- **Moyenne — audit d’accès médical.** Un journal d’actions administratives est présent, mais aucun journal réglementaire exhaustif des lectures et exports de dossiers médicaux n’a été établi. Vérifier également l’absence de données de santé dans les journaux techniques.
- **Qualité / accessibilité / SEO.** L’accueil et la connexion ont été vérifiés à une largeur mobile ; les espaces authentifiés, contrastes WCAG, navigation clavier complète et lecteurs d’écran n’ont pas été audités. Les composants médecin/admin sont très volumineux et contiennent templates/styles inline ; la plupart des routes sont importées eager plutôt que lazy. La taille initiale reste sous le budget Angular, mais des tests d’accessibilité, d’intégration, de performance et SEO sont à ajouter.

## Plan d’action priorisé

Estimations indicatives en jours-personne, hors achats de services, procédures de certification et revue juridique.

| Priorité | Action | Effort estimé |
|---|---|---:|
| **P0 — immédiat** | **Code/configuration corrigés :** secrets injectés par environnement, `.env` ignoré, profil `dev` à clés éphémères, profil `prod` strict et exemple sans valeur secrète. **Action manuelle restante :** faire tourner les secrets déjà exposés, puis purger l’historique Git. | 0,5–1,5 j |
| **P0 — bloquant démarrage** | **Corrigé et vérifié :** aligner les ports, nom de base, identifiants, variables Spring et documentation ; valider le démarrage propre sur une base PostgreSQL temporaire neuve et les migrations. | 0,5–1 j |
| **P0 — avant données réelles** | Définir l’hébergement et TLS, chiffrement et sauvegardes des données de santé, contrôle des accès opérationnels et exigences de la loi tunisienne n° 2004-63 / INPDP applicables. | 2–5 j techniques, hors revue juridique et cadrage INPDP |
| **P1** | Compléter consentement, politique de conservation, export/suppression, notices et traçabilité des consultations/exports de dossiers ; revue conformité INPDP/RGPD selon le public visé. | 4–10 j + revue juridique |
| **P1** | Implémenter le renouvellement frontend des sessions et une stratégie de stockage des jetons/headers de sécurité adaptée à l’architecture. | 1–3 j |
| **P1** | Ajouter mot de passe oublié, vérification d’adresse, avertissement d’urgence et parcours patient pour consulter/compléter les données médicales utiles. | 4–8 j |
| **P2** | Ajouter paiement/facturation, messagerie persistante, notifications patient SMS/push, avis modérés, report patient et filtres avancés de médecins. | 10–25 j selon prestataires et périmètre |
| **P2** | Durcir la téléconsultation : TURN avec identifiants temporaires, test audio/vidéo, partage de documents et tests d’interopérabilité réseau. | 3–7 j + exploitation TURN |
| **P2** | Compléter les parcours de profil médecin/patient, justificatifs sécurisés, documents médicaux et workflows admin. | 5–12 j |
| **P3** | Internationalisation FR/EN/AR et RTL, pages institutionnelles/SEO, revue WCAG, découpage/lazy loading des gros composants et tests end-to-end stables. | 6–15 j |

---

Cet audit décrit l’état observé dans le workspace au 11 novembre 2026. Le dépôt avait déjà 47 entrées modifiées avant la création de ce rapport ; ces changements préexistants n’ont pas été modifiés.
