**Important:** Provide daily/weekly/monthly notifications to take measurements and monitor health

## Front-end (React)

* **Screens:**
  * **Login screen**
    * Has two fields for a username and a password as well as two buttons: one that logs into an existing account, and another that uses the above to create a new account
  * **Home screen with overview + each function for an astronaut**
    * Left half has a grid of different risks with color-coded risk scores (detailed later)
    * Clicking one of the squares on the left leads you to one of the risk screens, clicking a measurement on the right leads you to one of its screens, and clicking the header above the measurements on the right leads you to the reminders screen.
  * **Reminders screen**
    * Provides a scrollable list of the upcoming measurements in the next day, week, and month
    * Clicking on a measurement brings you to its screen.
  * **A screen for each risk factor**
    * Provides a title, the category, and risk score from 1-100, provided by the backend, as well as a visual of the area. Also includes a reason to pay attention to the risk. Below the risk score, it includes recommendations to improve health in the case of a low score.
    * Below, includes a list of measurements ordered by urgency.
    * Clicking on a measurement brings you to its screen.
  * **Measurement overview screens for each measurement**
    * Top has a graph with an overview of numerical data or visual data and an analysis of trends.
    * Dropdown below the graph allows you to edit the frequency of reminders (in days between measurements)
    * Button at the bottom goes to the measurement performance screen.
  * **Measurement performance screens for each measurement**
    * Middle contains a detailed text explanation of how to perform the measurement with images.
    * Bottom (fixed) has a field to enter or upload the measurement and a button to finalize it and return to the home screen.
* **Approach:**
  * Mostly depend on the backend to calculate risk scores and list measurements for each risk. In this way, urls are also parameterized so that nothing is hardcoded. (Risks and measurements come with ids provided by the backend)
  * Stores a token upon login that is continually passed to the backend.
* **Style:**
  * Sleek, minimalistic style that looks fairly unique and non-AI.
  * Google-esque design language?

---

## Back-end (Spring Java w/ MongoDB repositories)

* **Controllers**
  * **LoginController**
    * Manages user logins (login credentials in body)
    * `POST /users/login`
      * Creates a new account, returning the token
      * Outputs `String`
    * `GET /users/login`
      * Gets the token for an account
      * Outputs `String`
  * **UserController**
    * Manages user data, reminders, and risks (token passed as an `Authorization: Bearer <token>` header)
    * `GET /users/measurements/{measurementID}`
      * Gets the user’s measurements from the database
      * Outputs `List<MeasurementSet>`
    * `POST /users/measurements/{measurementID}`
      * Logs the user’s measurements in the database
      * Outputs `Unit` (Nothing)
    * `PUT /users/measurements/{measurementID}/frequency`
      * Updates the reminder frequency (days between measurements) for one measurement
      * Outputs `Unit` (Nothing)
    * `GET /users/upcoming`
      * Outputs a list of the upcoming measurements the user needs to take in a time period, ordered by date to be taken.
      * Outputs `List<Reminder>`
    * `GET /users/risks`
      * Returns all of a user’s risk scores
      * Outputs `List<RiskEvaluation>`
    * `GET /users/risks/{riskID}`
      * Returns a user’s risk score, trends, and advices
      * Outputs `RiskEvaluation`
  * **DataController**
    * Serves measurement instructions as well as risk overviews
    * `GET /data/measurements/{measurementID}`
      * Returns a measurement’s instructions
      * Outputs `Measurement`
    * `GET /data/risks/{riskID}`
      * Returns a risk’s children measurements and its justification
      * Outputs `RiskFactor`
    * `GET /data/risks`
      * Returns all risk factors, so the frontend hardcodes nothing
      * Outputs `List<RiskFactor>`
* **Services**
  * **UserDataService**
    * Stores and retrieves user data from the repository (Makes sure to update measurement dates when logging one)
  * **UserLoginService**
    * Handles the login procedure and creates new accounts
  * **UserReminderService**
    * Compiles every measurement and how soon it should be taken based on measurement times and frequencies.
  * **DataRetrievalService**
    * Gets the risk data and measurement instructions using Jackson and records
  * **JudgementService**
    * Rule-based judging against guideline ranges (no statistics, no AI). Each numerical measurement’s latest value is classified against the ranges in `measurementDetails.json`: below `abnormalLow` (or above `abnormalHigh`) is abnormal, and beyond `dangerousLow`/`dangerousHigh` is dangerous. Scoring starts at 100; each abnormal measurement subtracts 40 and each dangerous one subtracts 75, stacking and floored at 1. The recommendation string matching the violated side of every abnormal measurement is collected into the advice (a list), ordered most severe first. Visual measurements are stored but excluded from scoring for the time being.
* **Repositories / Databases**
  * **UserData**
    * Stores user logins, passwords, measurement times and frequencies, and measurement data.
* **Classes**
  * **Enum classes:**
    * `RiskCategory`
      * Physical or Psychological, used in RiskFactor
    * `MeasurementCategory`
      * Numerical or Visual, used in Measurement
  * **“Static” classes:**
    * `RiskFactor`
      * Uses a string ID as well as a category and contains child measurements’ IDs and a string for the explanation of the risk’s purpose
    * `Measurement`
      * Uses a string ID as well as a category and contains instructions alongside two optional recommendation strings
    * `Instruction`
      * Contains an image reference and text.
  * **Non-static classes:**
    * `User` + `User (document)`
      * Contains a user’s username, hashed password, and current token (?)
      * Document class contains a map of measurement IDs to MeasurementSet objects
    * `MeasurementSet`
      * Contains a list / array of numbers or images along with the user ID it is associated with and the Measurement ID it corresponds to
    * `RiskEvaluation`
      * Contains a string ID correlating it to a risk factor as well as an integer score, a string suggesting how to improve the score, and an optional string describing the reason for the score
    * `Reminder`
      * Contains a measurement ID and a date/time.
* **Other**
  * **Folder with data**
    * `riskInfo.json`
      * Includes physical and psychological risks and the reasons to address them: A list of risks, which each have an ID as well as a category, a list of child measurements’ IDs (which correspond to those on the other JSON), and a string explaining the reason to pay attention to the risk.
      * Will be adjusted after the code is complete
    * `measurementInstructions.json`
      * Contains the instructions for each measurement: A list of measurements, which each have an ID and a list of instructions with an image and text for each.
      * Will also be adjusted after the code is complete
    * `measurementDetails.json`
      * Contains each measurement’s display name, type (numerical, visual), unit, and for numerical values: upper and lower bounds around abnormal and dangerous levels (4 doubles, each optional — a missing side does not apply), a boolean indicating whether it can be negative, and 2 optional recommendation strings for an unusually low and high number.

---

## Implementation addendum

* **Stack:** Spring Boot 4 (Jackson 3), Java 25, Gradle; MongoDB 7 via `docker-compose.yml`. Start dependencies with `docker-compose up -d` (the standalone binary; the Docker CLI has no compose plugin). On macOS the Docker daemon runs inside colima: run `colima start` first if `docker ps` fails.
* **Auth:** Tokens are generated with `UUID` on account creation, stored on the user document, and passed as an `Authorization: Bearer <token>` header on every user endpoint. Passwords are hashed with BCrypt (`spring-security-crypto`).
* **Data model:** `UserDocument` stores, keyed by measurement ID: `measurementFrequencies` (days between reminders), `measurementLastTaken`, and `measurementHistory` (lists of `MeasurementSet`). New accounts initialize every measurement frequency to 7 days (configurable via `app.defaults.measurement-frequency-days` in `application.yaml`).
* **`Measurement` output:** `DataRetrievalService` merges `measurementDetails.json` (name, type, unit, ranges, recommendations) with `measurementInstructions.json` (steps) into one `Measurement` record. JSON files are read from the `jsonData/` folder at startup (path configurable via `app.data-dir`) and parsed into records with Jackson; loading fails fast on malformed data or a risk referencing an unknown measurement ID.
* **Reminders:** `GET /users/upcoming?withinDays=N` returns every measurement whose next due date (`lastTaken + frequencyDays`, or now if never taken) falls within the window, ordered by due date. The frontend groups these into day/week/month buckets. Never-taken measurements are due immediately, so a fresh account's first reminder list is the complete baseline checklist.
* **Testability:** All services inject a `Clock` bean (`config/ClockConfig`) instead of calling `Instant.now()`, so date math is deterministic under test with a fixed `Clock`. Controllers are pure delegation (resolve the user, call one service); all logic lives in services.
* **Notifications:** A `NotificationSender` interface delivers each user's daily/weekly/monthly digest; `LoggingNotificationSender` is the development implementation (push/email plug in later behind the same interface). `NotificationScheduler` (`@Scheduled` daily, cron via `app.notifications.cron`, gated by `app.notifications.enabled`) builds a `ReminderDigest` per user from a single 30-day snapshot of `UserReminderService.getUpcoming`, then derives the day (1) and week (7) buckets from it — so the three buckets share identical `Reminder` objects, always nest, and the due-date math lives in one place. Failures sending to one user do not stop the others.
* **`ReminderDigest`:** New DTO alongside `Reminder`: the username plus daily, weekly, and monthly `Reminder` lists.
* **CORS & Hoppscotch:** `app.cors.allowed-origins` includes the React dev server and `https://hoppscotch.io`, so Hoppscotch (web or desktop) can call the API. `GET /users/login` keeps its credentials body, which the Hoppscotch desktop app can send (browsers cannot send GET bodies). A full request-by-request walkthrough lives in `docs/api-testing.md`.
* **Testing:** Two-layer suite. Unit tests (plain JUnit + Mockito; services with mocked dependencies and a fixed `Clock`, controllers via standalone MockMvc with mocked services) run with `./gradlew test` and need no external services. Functional tests (tagged `functional`, `@SpringBootTest(RANDOM_PORT)` driving the fully booted app over real HTTP against the Dockerized MongoDB in the dedicated `healthmonitor-functional` database) run with `./gradlew functionalTest` after `docker-compose up -d`; they cover the complete user journey, auth and error paths (missing header → 400, invalid token → 401, unknown IDs → 404, duplicate username → 409, validation errors → 400), the data endpoints, CORS preflight/actual requests for the Hoppscotch origin, and the notification digests.
* **Package layout:** `controller/`, `service/`, `repository/`, `domain/{dto,document}/`, `config/` (CORS + error handling + Clock + scheduling).
* **Status:** All spec classes exist; controllers are wired to services; service logic is implemented as described above. Controllers remain pure delegation. The daily/weekly/monthly reminder notifications (the spec's headline requirement) are implemented and tested; the test suite (68 unit + 23 functional tests) is green.

---

## Future steps

### Backend
1. **User baselines** — several measurements are expressed as "percent change from pre-flight/baseline"; store baseline values on the user document and convert raw readings server-side instead of requiring pre-computed change values.
2. **Visual measurement scoring** — replace the neutral placeholder for visual measurements (e.g. `oct_fundus_scan`) with real analysis, or a manual score-entry path for the Crew Medical Officer.
3. **Image storage** — visual `MeasurementSet` images are base64 in MongoDB documents; move to GridFS or object storage before real use (16 MB document limit).
4. **Per-measurement weights** — add an optional `weight` per measurement in `riskInfo.json` so some measurements count more toward a risk's score.
5. **Trend hints** — lightweight last-vs-previous direction indicator per measurement (no statistical modelling) for the frontend graphs.
6. **Token hardening** — token rotation on login, logout/invalidation endpoint, and possibly signed JWTs if more structure is needed.
7. **Notification delivery & scheduling coverage** — replace `LoggingNotificationSender` with a real push/email channel behind the same `NotificationSender` interface, and cover the `@Scheduled` trigger itself (currently only smoke-tested; the digest logic is fully unit-tested).
8. **API documentation & ops** — OpenAPI/Swagger UI, Actuator health endpoints, and a production deployment story (containerized app + MongoDB Atlas or a managed Mongo).

*(Delivered since the original list: the two-layer test suite — unit tests for `UserReminderService`, `JudgementService`, `UserLoginService`, `DataRetrievalService`, `NotificationScheduler`, and controller tests, plus functional tests over real HTTP against the Dockerized Mongo — and the scheduled daily/weekly/monthly notifications via `NotificationSender`/`NotificationScheduler`.)*

### Frontend (React)
1. **Screens** — login, home (risk grid + measurement list), reminders (day/week/month), risk detail, measurement overview (graph + frequency dropdown), and measurement performance (instructions + input) screens, all parameterized by backend IDs.
2. **Token handling** — store the token from login and attach it as an `Authorization: Bearer <token>` header on every request.
3. **Graphs** — render `GET /users/measurements/{id}` history as line charts with range bands from the measurement's guideline thresholds.

### Content
1. **Instruction images** — the `images/...` paths referenced by `measurementInstructions.json` need actual images.
2. **JSON tuning** — revisit ranges, recommendations, and risk-measurement groupings once the app is exercised end-to-end (per the original spec note).