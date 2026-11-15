# nasa_space_app_vnk_back

Health Monitor — the API backend for a crew-health monitoring app built for NASA Space Apps.
Astronauts log measurements (white blood cell counts, body mass, sleep, mood, OCT scans, …);
the backend scores them against guideline ranges into per-risk health scores (1–100),
tracks each measurement against the user's own baseline, and produces daily/weekly/monthly
measurement reminders. A React frontend will consume this API (not part of this repo yet).

## Features

- **Rule-based risk scoring** — no statistics, no AI. Each measurement's latest reading is
  classified against the abnormal/dangerous ranges in `jsonData/measurementDetails.json`:
  −40 per abnormal and −75 per dangerous reading, stacked, floored at 1. Visual measurements
  (e.g. OCT fundus scans) are stored but not scored yet.
- **User baselines** — measurements expressed as "percent change from pre-flight" take plain
  raw values (e.g. body mass in kg); the first reading becomes the user's baseline and every
  reading is converted server-side (percent or plain difference, per measurement).
- **Reminders** — per-measurement frequency (days between reminders); a measurement is due
  when `lastTaken + frequencyDays` has passed, or immediately if never taken.
- **Daily/weekly/monthly notifications** — a scheduled job builds every user's digest
  (disjoint day/week/month buckets) and hands it to a pluggable `NotificationSender`
  (logging implementation now; email/push later). The same buckets are served over
  HTTP via `GET /users/digest`.
- **Nothing hardcoded** — risks, measurements, thresholds, recommendations, and instructions
  all come from JSON files and are served to clients by ID.

## Stack

| Piece      | Choice                                          |
| ---------- | ----------------------------------------------- |
| Language   | Java 25                                         |
| Framework  | Spring Boot 4 (Jackson 3)                       |
| Database   | MongoDB 7 via `docker-compose.yml`              |
| Build      | Gradle wrapper                                  |
| Auth       | UUID bearer tokens, BCrypt-hashed passwords     |

## Getting started

Prerequisites: JDK 25, Docker (on macOS with colima: `colima start` if `docker ps` fails).

```bash
docker-compose up -d     # MongoDB on localhost:27017
./gradlew bootRun        # API on http://localhost:8080
```

Data lives in the `healthmonitor` database (see *Configuration* below). Try the API with
Hoppscotch — a request-by-request walkthrough is in [`docs/api-testing.md`](docs/api-testing.md).

## API overview

Auth: `POST` (create account) and `PUT` (log in) on `/users/login` take credentials in the
body and return a token; send it as
`Authorization: Bearer <token>` on every `/users/**` request. `/data/**` is public.

Ready-made API clients live in `docs/`:

- **`docs/openapi.yaml`** — OpenAPI 3.1 specification; imports into Hoppscotch
  (Import from OpenAPI), Postman, Insomnia, and most API tools. Note that Hoppscotch
  does not import request bodies from OpenAPI, so bodies must be typed by hand.

| Method & path | Purpose |
| --- | --- |
| `POST /users/login` | Create an account, returns the token (409 if taken) |
| `PUT /users/login` | Log in, returns the account's token (idempotent; browser-friendly) |
| `GET /users/measurements/{id}` | Measurement history, newest first (baseline-adjusted values) |
| `POST /users/measurements/{id}` | Log a reading — `{"values":[...]}` or `{"image":"<base64>"}` |
| `PUT /users/measurements/{id}/frequency` | Days between reminders (`{"frequencyDays":3}`) |
| `GET /users/measurements/{id}/trend` | Direction of the two latest readings: `up`/`down`/`flat`/`null` |
| `GET /users/upcoming?withinDays=30` | Reminders due in the window, ordered by due date |
| `GET /users/risks` | All risk scores for the user |
| `GET /users/risks/{riskId}` | One risk's score, advice list, and reason |
| `GET /users/digest` | Daily/weekly/monthly digest (same buckets as the notifications) |
| `GET /data/measurements/{id}` | A measurement's definition and step-by-step instructions |
| `GET /data/risks/{riskId}` | A risk's name, category, reason, and child measurements |
| `GET /data/risks` | All risk factors |

Errors: 400 missing header/validation, 401 bad token or credentials, 404 unknown ID,
409 duplicate username. Full field-level details live in the
[spec's API section](docs/space_apps_health_monitoring_design_spec.md).

## Testing

Two-layer suite, all green (91 unit + 29 functional):

```bash
./gradlew test            # unit tests (JUnit + Mockito, MockMvc); no services needed
./gradlew functionalTest  # full app over real HTTP against the Dockerized MongoDB
```

Functional tests run against a dedicated `healthmonitor-functional` database, which is
dropped before each test class and again when the test JVM exits — runs leave no permanent
data behind. (Note for Spring Boot 4: database URIs must use `spring.mongodb.uri`;
the old `spring.data.mongodb.uri` key is deprecated at error level and silently ignored.)

## Configuration (`src/main/resources/application.yaml`)

| Key | Default | Meaning |
| --- | --- | --- |
| `spring.mongodb.uri` | `mongodb://localhost:27017/healthmonitor` | Dev database |
| `app.data-dir` | `jsonData` | Where the risk/measurement JSON files live |
| `app.cors.allowed-origins` | React dev server + Hoppscotch | CORS for browser clients |
| `app.defaults.measurement-frequency-days` | `7` | Reminder frequency for new accounts |
| `app.notifications.enabled` | `true` | Gates the scheduled digest *sending* only |
| `app.notifications.cron` | `0 0 8 * * *` | When the daily digests go out |

## Project layout

```
src/main/java/org/vnk/healthmonitor/
├── controller/   # Login, User, Data controllers (pure delegation)
├── service/      # Login, user data, reminders, scoring, notifications
├── repository/   # MongoDB repository
├── domain/       # dto/ (API records) and document/ (MongoDB documents)
└── config/       # CORS, error handling, Clock bean, scheduling
jsonData/          # riskInfo, measurementInstructions, measurementDetails
docs/              # full spec + Hoppscotch walkthrough
```

## Known gaps / next steps

- React frontend (login, home risk grid, reminders day/week/month, risk detail,
  measurement overview + performance screens) — specified, not started.
- Real notification delivery (email/push) behind the existing `NotificationSender` interface.
- Instruction images referenced by `measurementInstructions.json`.
- Several risks (hypoxia/CO₂, decompression sickness, EVA injury, …) have no measurements
  yet and always score 100.
- Image storage: visual measurements are base64 in documents (16 MB limit) — GridFS before
  real use.

The full design, the implementation addendum, and the tracked future-steps list are in
[`docs/space_apps_health_monitoring_design_spec.md`](docs/space_apps_health_monitoring_design_spec.md).
