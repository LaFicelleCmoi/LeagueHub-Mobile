# LeagueHub Android ⚽

Application **Android native en Kotlin** réunissant les 5 grands championnats européens — Premier League, La Liga, Serie A, Bundesliga, Ligue 1 — et le **tracker Ligue des Champions**, avec scores en direct, classements recalculés en temps réel et simulateur.

> ⚠️ Projet de fan, non officiel. Non affilié aux ligues ni à l'UEFA. Données issues de l'API publique non officielle d'ESPN, à titre indicatif.

Ce projet est la déclinaison mobile native de deux projets web existants, dont il réutilise la logique et les composants :

- **[LeagueHub](https://github.com/LaFicelleCmoi/LeagueHub)** (Next.js) — les 5 championnats : classements, direct, forme des clubs, coupes nationales, palmarès.
- **[Tracker LDC 2026-2027](https://github.com/LaFicelleCmoi/LDC-2026-2027-)** (vanilla JS) — phase de ligue 36 clubs, moteur de classement UEFA, simulateur, tirage officiel.

## Fonctionnalités

### Les 5 championnats
- **Classements** : points, bilan, buts, zones européennes et relégation, avec couleurs partagées.
- **Matchs en direct** : score, minute, buteurs, cartons rouges — rafraîchis par polling toutes les 20 s, mis en pause quand l'app est en arrière-plan.
- **Classement en direct** : recalculé en temps réel pendant les matchs (points et buts provisoires, flèches de mouvement), sans double comptage.
- **Calendrier** : derniers résultats et 14 prochains jours par championnat, consultable hors ligne.
- **Forme d'un club** : les 5 derniers matchs officiels toutes compétitions (V/N/D, score, adversaire, lieu), affichage dédié pour les coupes d'Europe.
- **Buteurs & actualités** : meilleurs buteurs, passeurs, derniers articles.
- **Club favori** : mémorisé sur l'appareil (DataStore, sans compte) — section « Mon club » sur l'accueil, étoile dorée dans les classements, célébration quand il marque en direct.
- **Recherche de club** avec palmarès complet et déroulé de la saison mois par mois.

### Tracker Ligue des champions
- **Phase de ligue 36 clubs** : classement complet selon l'article 18.01 du règlement UEFA, découpé en zones 1-8 / 9-24 / 25-36.
- **Phase finale** : cartes et arbre des éliminations directes.
- **Simulateur « What-If »** : injecte des scores fictifs et reclasse via le moteur, en direct.
- **Tirage officiel 2026-27** : les 8 adversaires de chaque club (4 ⌂ / 4 ✈), en repli si ESPN n'a pas encore publié le calendrier.
- **Compte à rebours** avant chaque coup d'envoi : ambre à 24 h, rouge dans la dernière heure, passage automatique en direct.

### Transversal
- **Thème clair / sombre / système**, appliqué avant la première frame.
- **i18n FR / EN**, portage du système `data-i18n`.
- **Widget d'écran d'accueil** (Glance) : prochain match du club favori.
- **Notifications** : coup d'envoi, but du club favori.
- **Mode hors-ligne** : classements et calendrier servis depuis Room, rafraîchis dès que le réseau revient (single source of truth).

## Stack

| Rôle | Outil |
|---|---|
| Langage | Kotlin |
| UI | Jetpack Compose, Material 3 |
| Architecture | MVVM + Clean Architecture (`app` / `domain` / `data`) |
| Injection | Hilt |
| Réseau | Retrofit + kotlinx.serialization (API publique ESPN) |
| Base locale | Room |
| Async | Coroutines + Flow (`StateFlow`, `combine`) |
| Persistance légère | DataStore (thème, club favori, langue) |
| Widgets | Glance |
| Tests | JUnit, Turbine, Compose UI tests |
| CI | GitHub Actions (build + tests + lint à chaque push) |

## Réutilisation des projets web

L'app n'est pas une simple copie des écrans : les **composants logiques** des deux repos sont portés en Kotlin et partagés par les 6 compétitions.

| Composant web (JS/TS) | Équivalent Kotlin | Réutilisé pour |
|---|---|---|
| `standings-engine.js` — moteur de classement UEFA pur, testé | `domain/standings/RankLeaguePhase.kt` (fonction pure, zéro Android) | LDC (et modèle pour les classements live des 5 championnats) |
| `simulate.js` — simulateur What-If | `domain/simulation/` | LDC |
| `schedule.js` — `getNextKickoff()`, `countdownParts()` | `domain/schedule/` | Comptes à rebours toutes compétitions |
| `i18n.js` — FR/EN, clé `ldc_lang` | `core/i18n/` + strings FR/EN | Toute l'app |
| `draw2026.js` — tirage officiel indexé par identifiant ESPN | `data/ldc/Draw2026.kt` | LDC |
| `coefficients2026.js` — coefficients clubs | `data/ldc/Coefficients.kt` | Dernier critère de départage LDC |
| `espn.js` / `src/lib/espn/` — couche d'accès ESPN | `data/espn/` (Retrofit + mappers → modèles du domaine) | Les 5 championnats + LDC |
| `src/lib/leagues.ts` — config des championnats | `domain/LeagueConfig.kt` — **une seule entrée pour ajouter un championnat** (code ESPN, ex. `ned.1` pour l'Eredivisie) | Extensibilité |

Comme sur le web, **le seul paramètre qui change la compétition est le slug ESPN** : l'ajout d'une compétition se fait par simple configuration.

## Architecture

```
app/src/main/kotlin/com/leaguehub/android/
├── app/                    Application, Hilt (di/), navigation Compose, WorkManager (work/), notifications
├── core/
│   ├── i18n/               FR/EN
│   ├── theme/              Clair / sombre / système
│   └── util/               Dates en français (heure de Paris), format
├── domain/
│   ├── model/              Match, Team, Standing, Competition…
│   ├── leagueconfig/       Configuration des compétitions (slug ESPN)
│   ├── standings/          RankLeaguePhase.kt — moteur de classement UEFA (pur, testé)
│   ├── live/               Règles du suivi en direct (quels matchs, quand)
│   ├── schedule/           Prochain coup d'envoi, comptes à rebours
│   ├── simulation/         Simulateur What-If
│   ├── knockout/           Phase finale : tours, confrontations aller-retour
│   └── repository/         Interfaces des repositories (implémentées dans data/)
├── data/                   FootballRepositoryImpl : repository unique, offline-first
│   ├── espn/               Retrofit + mappers JSON ESPN → domaine
│   ├── ldc/                Tirage 2026, coefficients UEFA
│   ├── db/                 Room : classements, calendrier, résultats
│   └── settings/           DataStore : thème, club favori, langue
├── widget/                 Widget Glance : prochain match du club favori
└── ui/
    ├── home/               Accueil : chiffres clés, mon club, matchs du jour
    ├── league/             Classement, matchs, buteurs (paramétré par LeagueConfig)
    ├── ldctracker/         Phase de ligue, phase finale, simulateur
    ├── club/               Fiche club : forme, palmarès, saison
    ├── matchdetail/        Chronologie, stats comparées, compositions
    ├── search/             Recherche de club
    ├── settings/           Thème, langue, club favori, notifications
    └── components/         Cartes de match, ligne de classement, compte à rebours, badge forme
```

### Données et direct

- Les appels ESPN passent par un **repository unique** : l'UI ne connaît que les modèles du domaine.
- **Offline-first** : Room sert le dernier état connu, le réseau rafraîchit en arrière-plan (WorkManager pour les recalages, polling pendant les matchs).
- Polling **uniquement pendant les matchs** (et dans les 10 minutes précédant le coup d'envoi), en pause quand l'app est masquée ; en cas d'erreur, les tentatives s'espacent jusqu'à 5 minutes.
- Timeout sur chaque requête (`OkHttp` timeout + `TimeoutException` mapping) ; un événement ESPN malformé est filtré, jamais crashant.
- Si ESPN ne répond pas, l'app affiche le dernier état valide en base — jamais d'écran vide.

### Classement en direct

Portage direct des règles du site : points provisoires, pas de double comptage (le bilan V-N-D fourni avec chaque match indique s'il est déjà inclus), départage points → différence de buts → buts marqués, et ordre officiel conservé hors match.

## Tests

Le moteur de classement est une fonction pure, testée comme sur le web :

- Reproduction du classement officiel LDC 2024-25 sur les 144 matchs (36 positions exactes).
- Critères de l'article 18.01 dans l'ordre, y compris fair-play et coefficients ; une donnée manquante marque l'équipe `unresolved` au lieu de sauter un critère.

```bash
./gradlew test          # tests unitaires (domaine, mappers ESPN)
./gradlew connectedAndroidTest   # tests UI Compose
```

## Développement

Android Studio Ladybug ou plus récent, JDK 17.

```bash
git clone https://github.com/LaFicelleCmoi/leaguehub-android.git
cd leaguehub-android
./gradlew installDebug
```

Aucune variable d'environnement n'est nécessaire : l'API ESPN est publique.

## Feuille de route

- [x] Moteur de classement UEFA porté et testé
- [x] Les 5 championnats : classements, matchs, direct
- [x] Tracker LDC : phase de ligue, simulateur, tirage
- [x] Fiche club : forme, palmarès, saison
- [x] Widgets Glance et notifications
- [x] Thème aux couleurs du club favori

---

Site et app non officiels, sans lien avec les ligues ni les clubs. Données fournies par ESPN.