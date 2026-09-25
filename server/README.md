# ZERO STRESS — instant push bridge (Firestore → Knock)

`server/` is a small always-on Node process that watches the Firestore
`notifications` collection and triggers the Knock workflow (`zs-push`) the
moment a document appears. That is what turns push from "up to 30 minutes"
(the GitHub Actions relay's schedule) into "a couple of seconds".

It is **optional infrastructure**. Without it the app still works: the
`notifications` collection stays the source of truth for the in-app list and
`.github/workflows/free-cron.yml` still drains the queue every 30 minutes.

```
app writes notifications/{id}  ─┐
                                ├─► bridge (this service, ~1s) ─► Knock ─► FCM ─► device
                                └─► GitHub relay (*/30 fallback, same pushSent flag)
```

## Why not Cloud Functions?

`functions/index.js` is the native Firestore-trigger version, but it needs the
paid **Blaze** plan (outbound networking). The bridge exists so the free Spark
plan can still push in seconds.

## What it does

1. Listens to the newest 200 `notifications` documents (`orderBy timestamp desc`).
2. For each **new** document:
   - `pushSent: true` → skip (never delivered twice, by either sender).
   - `push: false` → in-app only; flag it and stop.
   - `uid: "<uid>"` → look up that player's `fcmToken`; `uid: null` → every
     player with a token (chunked into Knock's 1000-recipient batches).
   - Trigger `POST /v1/workflows/zs-push/trigger` with the token passed
     **inline as channel data** (`{ "<fcm-channel-uuid>": { tokens: [...] } }`),
     so no client-side Knock SDK or recipient sync is required.
3. On success it writes `pushSent: true` (plus `pushSentVia` and the Knock run
   id) back to the document.
4. If Knock fails, it falls back to sending the same payload **directly over
   FCM** with the same service account the GitHub relay uses
   (`DIRECT_FCM_FALLBACK=false` disables this). Only if both fail is
   `pushSent` left unset, so the 30-minute relay retries later.

The Android app needs **no changes** — it already writes `uid`/`uid: null` and
already stores `players/{uid}.fcmToken`.

## Environment variables

| Variable | Required | Notes |
| --- | --- | --- |
| `KNOCK_API_KEY` | yes | Knock secret key (`sk_…`) for the environment that owns the `zs-push` workflow |
| `FIREBASE_SERVICE_ACCOUNT` | yes\* | Service-account JSON, base64 of it, or a path to the `.json` file |
| `KNOCK_WORKFLOW_KEY` | no | default `zs-push` |
| `KNOCK_FCM_CHANNEL_ID` | no | default is the channel UUID baked into the code |
| `KNOCK_API_BASE` | no | default `https://api.knock.app` |
| `DIRECT_FCM_FALLBACK` | no | `false` disables the direct-FCM safety net |
| `RECONCILE_WINDOW_MINUTES` | no | restart catch-up window, default `10` |
| `PORT` | no | health-check port, default `8080` |

\* Optional only if `GOOGLE_APPLICATION_CREDENTIALS` points at a service-account
file instead (for example on a Google Cloud VM). Run the container with
`-v /srv:/srv:ro` and keep the key at `/srv/zs-sa.json` to use this form — then
nothing has to be base64-encoded, which is the only practical route when your
only device is a phone.

Local runs inside the Freebuff workspace pick these up from the workspace
`.env` automatically — do not commit that file.

## Run it

```bash
cd server
npm install
npm test          # unit tests for the payload/recipient helpers
npm start         # starts the listener + http://localhost:8080/health
```

Docker (recommended for a server):

```bash
docker build -t zs-knock-bridge ./server
docker run -d --name zs-knock-bridge --restart unless-stopped -p 8080:8080 \
  -v /srv:/srv:ro --env-file ./server/knock-bridge.env zs-knock-bridge
```

`knock-bridge.env` is a local, never-committed file:

```ini
KNOCK_API_KEY=sk_...
FIREBASE_SERVICE_ACCOUNT={"type":"service_account",...}
```

> Pasting multi-line JSON into a shell or `.env` is error-prone; base64 is
> accepted and easiest: `base64 -w0 service-account.json`.

### Where to host it

It must be a host that keeps a process **running 24/7** — serverless platforms
(Vercel/Netlify/Cloudflare Workers) and free web-service tiers that sleep after
inactivity will not work, because the listener has to stay connected.

- **Google Cloud always-free `e2-micro` VM** — best fit if you already use
  Firebase: same Google account, free forever in one US region, real Docker.
  ```bash
  gcloud compute instances create zs-knock-bridge --machine-type=e2-micro \
    --zone=us-central1-a --image-family=debian-12 --image-project=debian-cloud
  # then: install docker, copy the env file, and run the container
  ```
  Keep it alive with the bundled `deploy/zs-knock-bridge.service` systemd unit.
- **Oracle Cloud Always Free ARM VM** — same idea, more RAM, more signup friction.
- **Fly.io / Railway / any VPS** — `fly launch` from `server/` uses the
  `Dockerfile` as-is; on Fly, remove the `[http_service]` port if you do not
  want a public health check.
- **A spare always-on machine/device with Docker** — also fine.

The GitHub Actions relay keeps running as the fallback either way, so the
worst case when the bridge is offline is the old 30-minute delay — never a lost
notification.

### No computer? Set it up from your phone

Google Cloud Shell is a free terminal that runs in a browser tab, so nothing
has to be installed locally. The repository is public, so the VM can clone it
itself — no GitHub login and no file copying involved.

> Push your branch first (the Changes panel). Until the push lands, the clone
> on the VM will not contain `server/`.

1. Open **https://shell.cloud.google.com** on the phone (same Google account as
   Firebase) and create the VM:
   ```bash
   gcloud config set project zerostress-3a536
   gcloud services enable compute.googleapis.com
   gcloud compute instances create zs-knock-bridge --machine-type=e2-micro \
     --zone=us-central1-a --image-family=debian-12 --image-project=debian-cloud \
     --boot-disk-size=30GB
   ```
2. Log into it and install Docker:
   ```bash
   gcloud compute ssh zs-knock-bridge --zone=us-central1-a
   curl -fsSL https://get.docker.com | sudo sh
   ```
3. Clone and build on the VM:
   ```bash
   git clone -b ZS3.1 https://github.com/sronoop-art/Zero-stress-app.git
   cd Zero-stress-app && sudo docker build -t zs-knock-bridge server
   ```
4. Create the Firebase credentials. In a second Cloud Shell tab (leaving the
   SSH session open):
   ```bash
   gcloud iam service-accounts list --project zerostress-3a536   # copy the firebase-adminsdk-… email
   gcloud iam service-accounts keys create ~/zs-sa.json \
     --iam-account=firebase-adminsdk-XXXXX@zerostress-3a536.iam.gserviceaccount.com
   gcloud compute scp ~/zs-sa.json zs-knock-bridge:/tmp/ --zone=us-central1-a
   ```
   If the project blocks key creation, download the key from the Firebase
   console instead (Project settings → Service accounts → **Generate new private
   key**) and upload it with **⋮ → Upload file** in Cloud Shell.
   Then, back in the SSH session:
   ```bash
   sudo install -m 600 /tmp/zs-sa.json /srv/zs-sa.json
   ```
5. The env file is then two short lines — no base64, no multi-line paste:
   ```bash
   sudo install -m 600 /dev/null /etc/zs-knock-bridge.env
   sudo nano /etc/zs-knock-bridge.env
   # KNOCK_API_KEY=sk_…
   # GOOGLE_APPLICATION_CREDENTIALS=/srv/zs-sa.json
   ```
6. Install the service and check it:
   ```bash
   sudo cp deploy/zs-knock-bridge.service /etc/systemd/system/
   sudo systemctl daemon-reload && sudo systemctl enable --now zs-knock-bridge
   curl -s localhost:8080/health
   ```

## Knock dashboard checklist

Both the channel and the workflow must live in the **same environment** as the
`KNOCK_API_KEY` you deploy.

1. **Channels and sources → FCM (Push)** — configured with Firebase project
   `zerostress-3a536` and its **full service-account JSON**.
2. **Workflow `zs-push`** with a **Push** step whose title/body come from the
   trigger data: `{{ data.title }}` / `{{ data.message }}`, and a data payload
   that forwards `type`, `uid`, `channelId` and `notificationId` (the app reads
   `title`, `body`, `type` and `uid`).
3. Android channel override (optional, recommended) so foreground/background
   pushes use the same high-importance channel:
   ```json
   { "android": { "priority": "high",
       "notification": { "channel_id": "{{ data.channelId }}",
                         "notification_priority": "PRIORITY_HIGH" } } }
   ```
4. Trigger data never contains a `uid` for broadcasts — the app drops any push
   whose `uid` differs from the signed-in user, so the bridge omits the field
   entirely instead of sending `"all"`.

## Verify it actually works

First prove the Knock side on its own (no bridge, no app needed):

```bash
cd server
node verify.js --dry-run                 # print the exact request, send nothing
node verify.js --uid <playerUid>         # real push to that player's device
```

On a server the image can run the check for you with the exact env file the
service uses, so nothing has to be installed on the host:

```bash
sudo docker run --rm -v /srv:/srv:ro --env-file /etc/zs-knock-bridge.env \
  zs-knock-bridge node verify.js --uid <playerUid>
```

It uses the same helpers as the bridge, so a green run proves the workflow key,
the FCM channel UUID and (with `--uid`) the Firebase credentials. `HTTP 200`
means Knock accepted the trigger — check Knock → Workflows → `zs-push` → Runs
for per-recipient FCM delivery. `--token <fcmToken>` tests a single raw token
without Firebase credentials; `--broadcast --yes` hits every device.

Then the service itself:

```bash
curl -s localhost:8080/health
# {"status":"ok","listenerAttached":true,"sent":0,"lastError":null, ...}
```

Then send a notification from the admin app (Send Notification → **All players**)
and watch the service log:

```
[sent] <docId>: 12 recipient(s) via Knock
```

- `[sent] … via direct FCM fallback` → Knock rejected the call; `lastError`
  holds the reason (usually the workflow key or channel UUID).
- `[skip] … no FCM token registered` → no device has registered a token yet;
  the 30-minute relay retries automatically.
- `[retry-later]` → both paths failed; the relay retries.

Check the run in the Knock dashboard (Workflows → `zs-push` → Runs) to confirm
delivery per recipient.

## Troubleshooting

| Symptom | Cause / fix |
| --- | --- |
| `Knock trigger failed (401)` | `KNOCK_API_KEY` is from a different environment, or was rotated |
| `Knock trigger failed (404)` | Workflow key is not `zs-push` in the environment the key belongs to |
| `Knock trigger failed (422)` | Recipient is missing channel data (wrong `KNOCK_FCM_CHANNEL_ID`) |
| Health says `degraded` | The Firestore listener could not attach — wrong/missing `FIREBASE_SERVICE_ACCOUNT`, or no network egress |
| Push arrives but no status-bar notification when the app is backgrounded | The Push step has no notification title/body (only data), or the FCM channel is not configured |
| Push arrives twice | Both senders raced before `pushSent` landed; harmless if rare, but check they use the same Firebase project |
| Nothing at all, no log lines | The document has no `timestamp`, or it was written before the service started and older than `RECONCILE_WINDOW_MINUTES` |
