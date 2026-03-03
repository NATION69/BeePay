# BeePay SMS Forwarding + Control Chat (2 Agents)

This repository now contains:

- `agent-a`: Android app (SMS collector + settings + control chat UI)
- `agent-b`: Relay/Control agent (verify signatures, route rules, Telegram connector)

## Architecture

`Android SMS -> Agent A -> /ingest -> Agent B -> Target Connector (Telegram now, more agents later)`
`App Chat -> Agent A -> /control/chat -> Agent B Route Engine`

## Agent B (Node.js relay + control)

### 1) Requirements

- Node.js 18+
- Telegram bot token
- Telegram channel/chat id

### 2) Setup

```bash
cd agent-b
cp .env.example .env
```

Edit `.env`:

- `TELEGRAM_BOT_TOKEN`: token from BotFather
- `TELEGRAM_CHAT_ID`: target channel id (usually starts with `-100...`)
- `AGENT_SHARED_SECRET`: long random secret shared with Android app
- `CONTROL_SHARED_SECRET`: optional secret for `/control/*` endpoints
- `MASK_OTP=true`: mask numeric OTP-like values in forwarded text

### 3) Run

```bash
npm install
npm run start
```

Health check:

```bash
curl http://localhost:8080/health
```

Control endpoints:

- `POST /control/chat` with `{ "message": "حول رسائل CIB للتليجرام" }`
- `GET /control/routes`
- `POST /control/routes`

## Agent A (Android app)

### 1) Open project

Open `agent-a` folder in Android Studio.

### 2) Configure from app Settings screen

After launching the app, fill these fields then tap **Save settings**:

- `Ingest URL`: e.g. `http://192.168.1.100:8080/ingest`
- `Control Chat URL`: e.g. `http://192.168.1.100:8080/control/chat`
- `Control Routes URL`: e.g. `http://192.168.1.100:8080/control/routes`
- `Shared secret`: same value as `AGENT_SHARED_SECRET` in Agent B
- `Control secret`: same value as `CONTROL_SHARED_SECRET` (if set)
- `Device ID`: any label for this phone
- `Allowed senders`: one per line (empty = allow all)
- `Blocked senders`: one per line
- `Required keywords`: one per line (empty = no keyword filter)
- `Mask OTP locally before sending`: optional privacy layer
- The UI uses a pixel font (`Press Start 2P`)

### 3) Use control chat from app

From the app chat box, send commands like:

- `حول رسائل CIB للتليجرام`
- `show routes`
- `clear routes`
- `delete rule 1`

### 4) Build + install

- Connect Android phone (or emulator with SMS capability)
- Build and run app from Android Studio
- Open app once and grant SMS permission when prompted

### 5) Generate APK for easy install

Debug APK:

```bash
cd agent-a
gradlew.bat assembleDebug
```

APK path:

- `agent-a/app/build/outputs/apk/debug/app-debug.apk`

### 6) Build APK using GitHub (no Android Studio required)

1. Push this project to a GitHub repository.
2. Open GitHub repo -> `Actions` -> `Build Android APK`.
3. Click `Run workflow`.
4. After success, open the workflow run -> `Artifacts`.
5. Download `app-debug-apk` (contains `app-debug.apk`).
6. Upload APK to Google Drive and share link with phone.

Quick helper from repo root:

- `run.cmd -BuildApk`
- `run.cmd -StartAgentB`
- `run.cmd -ServeApk`
- `run.cmd -BuildApk -StartAgentB -ServeApk`

When `-ServeApk` runs, root file `download` is updated with a phone-ready URL.

Release APK (signed):

- Android Studio -> `Build` -> `Generate Signed Bundle / APK` -> `APK`

### 7) Android notes

- Works on Android only (iOS does not allow general SMS reading)
- Keep app installed; receiver listens to incoming SMS broadcasts
- On some devices, disable battery optimization for reliable background behavior

## End-to-end test

1. Start Agent B (`npm run start`)
2. Install Agent A on phone and grant SMS permission
3. From app chat send: `حول رسائل CIB للتليجرام`
4. Send an SMS from CIB sender to that phone
5. Confirm message appears in Telegram channel

## Security checklist

- Use long random shared secret
- If `CONTROL_SHARED_SECRET` is enabled, keep it private in app settings
- Keep Agent B behind firewall/VPN when possible
- Use HTTPS (reverse proxy) in production
- Keep `MASK_OTP=true` if messages contain sensitive codes

## Current limitations

- Agent B dedupe store is in-memory (resets on restart)
- Agent A retry queue is not persisted yet
- Route rules are in-memory (not DB persisted yet)
