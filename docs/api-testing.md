# Testing the API from Hoppscotch

The API is fully exercisable from the [Hoppscotch desktop app](https://hoppscotch.io/download).
The web app works for everything except `GET /users/login` (browsers cannot send a body with GET
requests; the desktop app can).

## 1. Point Hoppscotch at the API

**Prerequisite (macOS with colima):** the Docker daemon runs inside colima.
If `docker ps` fails, start it with `colima start` before running the
commands below. Check with `docker ps` or `colima status`.

Start the stack:

```bash
docker-compose up -d     # MongoDB
./gradlew bootRun        # API on http://localhost:8080
```

The CORS config already allows `https://hoppscotch.io`, so the Hoppscotch web app can call the
API directly once it is running.

## 2. Create an account

```
POST http://localhost:8080/users/login
Content-Type: application/json

{"username": "astronaut1", "password": "secret"}
```

Response: a JSON string with the token, e.g. `"3f2c9a1e-..."`. Copy it.

## 3. Set the Authorization header

For every `/users/...` request below, add the header:

```
Authorization: Bearer <token>
```

(In Hoppscotch: open the *Auth* tab, choose *Bearer*, paste the token.)

## 4. Log in again (GET with credentials in the body)

```
GET http://localhost:8080/users/login
Content-Type: application/json

{"username": "astronaut1", "password": "secret"}
```

Returns the same token. This must be sent from the Hoppscotch desktop app (or curl), not the
web app, because it is a GET with a body.

## 5. Explore the static data (no auth needed)

* `GET /data/risks` — every risk factor (IDs, names, categories, child measurement IDs, reasons)
* `GET /data/risks/{riskID}` — one risk, e.g. `/data/risks/immune_dysregulation`
* `GET /data/measurements/{measurementID}` — one measurement merged with its instructions,
  e.g. `/data/measurements/white_blood_cells`

Use the IDs from these responses in the URLs below — nothing needs to be hardcoded.

## 6. User endpoints (Bearer token required)

* `POST /users/measurements/white_blood_cells` — log a reading:

  ```json
  {"values": [5200.0]}
  ```

  Visual measurements instead take `{"image": "<base64>"}`.
* `GET /users/measurements/white_blood_cells` — the logged history, newest first.

  Baseline-relative measurements (`body_mass_change`, `grip_strength_change`,
  `memory_score_change`, `balance_score_change`, `retinal_thickness_change`) take
  **plain raw values** (e.g. body mass in kilograms). The first logged value becomes
  the user's baseline, and the history is served as the change from that baseline —
  percent change for the percent-unit measurements, plain difference in the
  measurement's unit for retinal thickness. Absolute measurements are returned as-is.
* `GET /users/measurements/body_mass_change/trend` — the direction of the two most
  recent baseline-adjusted readings: `{"measurementId": "...", "direction": "up"}`.
  Direction is `"up"`, `"down"`, `"flat"`, or `null` with fewer than two readings.
* `PUT /users/measurements/white_blood_cells/frequency` — days between reminders:

  ```json
  {"frequencyDays": 3}
  ```
* `GET /users/upcoming?withinDays=30` — reminders due in the next N days, ordered by due date
* `GET /users/risks` — every risk score for the user
* `GET /users/risks/immune_dysregulation` — one risk's score, advice, and reason
* `GET /users/digest` — the daily/weekly/monthly reminder digest, the exact same
  buckets the scheduled notification sender delivers:

  ```json
  {"username": "astronaut1", "daily": [...], "weekly": [...], "monthly": [...]}
  ```

## 7. Expected status codes

| Situation                          | Code |
| ---------------------------------- | ---- |
| Success                            | 200  |
| Missing `Authorization` header     | 400  |
| Invalid token / wrong password     | 401  |
| Unknown measurement or risk ID     | 404  |
| Duplicate username on creation     | 409  |
| Validation error (e.g. `frequencyDays: 0`) | 400 |
