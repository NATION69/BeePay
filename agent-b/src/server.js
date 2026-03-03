import crypto from "node:crypto";
import express from "express";
import dotenv from "dotenv";

dotenv.config();

const app = express();
const port = Number(process.env.PORT || 8080);

const TELEGRAM_BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const TELEGRAM_CHAT_ID = process.env.TELEGRAM_CHAT_ID;
const AGENT_SHARED_SECRET = process.env.AGENT_SHARED_SECRET;
const CONTROL_SHARED_SECRET = process.env.CONTROL_SHARED_SECRET || "";
const MASK_OTP = String(process.env.MASK_OTP || "true").toLowerCase() === "true";

if (!TELEGRAM_BOT_TOKEN || !TELEGRAM_CHAT_ID || !AGENT_SHARED_SECRET) {
  console.error("Missing required env vars. Check TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID, AGENT_SHARED_SECRET.");
  process.exit(1);
}

app.use(
  express.json({
    verify: (req, _res, buf) => {
      req.rawBody = buf;
    },
  })
);

const seen = new Map();
const DEDUPE_TTL_MS = 5 * 60 * 1000;

let routes = [];
let nextRouteId = 1;

function cleanupSeen() {
  const now = Date.now();
  for (const [hash, ts] of seen.entries()) {
    if (now - ts > DEDUPE_TTL_MS) {
      seen.delete(hash);
    }
  }
}

setInterval(cleanupSeen, 30_000).unref();

function timingSafeEqualText(a, b) {
  const aBuf = Buffer.from(a || "", "utf8");
  const bBuf = Buffer.from(b || "", "utf8");
  if (aBuf.length !== bBuf.length) return false;
  return crypto.timingSafeEqual(aBuf, bBuf);
}

function verifySignature(req) {
  const provided = req.header("x-agent-signature") || "";
  if (!provided.startsWith("sha256=")) {
    return false;
  }

  const expectedHex = crypto
    .createHmac("sha256", AGENT_SHARED_SECRET)
    .update(req.rawBody || Buffer.from(""))
    .digest("hex");

  const actual = Buffer.from(provided.slice(7), "hex");
  const expected = Buffer.from(expectedHex, "hex");

  if (actual.length !== expected.length) {
    return false;
  }

  return crypto.timingSafeEqual(actual, expected);
}

function verifyControlSecret(req) {
  if (!CONTROL_SHARED_SECRET) {
    return true;
  }
  const provided = req.header("x-control-secret") || "";
  return timingSafeEqualText(provided, CONTROL_SHARED_SECRET);
}

function maskOtp(text) {
  if (!MASK_OTP) {
    return text;
  }

  return text.replace(/\b\d{4,8}\b/g, "****");
}

function formatTelegramMessage(payload) {
  const sender = payload.sender || "unknown";
  const body = typeof payload.body === "string" ? payload.body : "";
  const receivedAt = payload.received_at || new Date().toISOString();
  const deviceId = payload.device_id || "unknown-device";

  return [
    "#SMS_FORWARD",
    `Device: ${deviceId}`,
    `From: ${sender}`,
    `At: ${receivedAt}`,
    "Message:",
    maskOtp(body),
  ].join("\n");
}

async function sendToTelegram(text, targetChatId) {
  const url = `https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage`;
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      chat_id: targetChatId || TELEGRAM_CHAT_ID,
      text,
      disable_web_page_preview: true,
    }),
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`Telegram API error: ${response.status} ${body}`);
  }
}

const connectors = {
  telegram: async ({ text, targetId }) => {
    await sendToTelegram(text, targetId || TELEGRAM_CHAT_ID);
  },
};

function normalizeSender(input) {
  return String(input || "").trim().toLowerCase();
}

function resolveTargets(sender) {
  const normalized = normalizeSender(sender);
  if (routes.length === 0) {
    return [{ type: "telegram", targetId: TELEGRAM_CHAT_ID, routeId: 0 }];
  }

  const matched = routes.filter((r) => normalized.includes(r.senderContains.toLowerCase()));
  return matched.map((r) => ({ type: r.targetType, targetId: r.targetId, routeId: r.id }));
}

function addRoute({ senderContains, targetType = "telegram", targetId = TELEGRAM_CHAT_ID }) {
  const route = {
    id: nextRouteId++,
    senderContains: senderContains.trim(),
    targetType,
    targetId,
    createdAt: new Date().toISOString(),
  };
  routes.push(route);
  return route;
}

function listRoutesText() {
  if (routes.length === 0) {
    return "No routes configured.\nUse: \u062d\u0648\u0644 \u0631\u0633\u0627\u0626\u0644 <sender> \u0644\u0644\u062a\u0644\u064a\u062c\u0631\u0627\u0645";
  }

  return routes
    .map((r) => `#${r.id} sender~"${r.senderContains}" => ${r.targetType}(${r.targetId})`)
    .join("\n");
}

function parseChatCommand(messageRaw) {
  const text = String(messageRaw || "").trim();
  const lower = text.toLowerCase();

  if (!text) return { action: "help" };

  if (
    lower === "show routes" ||
    lower === "list routes" ||
    (lower.includes("\u0627\u0639\u0631\u0636") && lower.includes("\u0642\u0648\u0627\u0639\u062f"))
  ) {
    return { action: "list" };
  }

  if (
    lower === "clear routes" ||
    lower.includes("\u0627\u0645\u0633\u062d \u0627\u0644\u0642\u0648\u0627\u0639\u062f") ||
    lower.includes("\u062d\u0630\u0641 \u0627\u0644\u0642\u0648\u0627\u0639\u062f")
  ) {
    return { action: "clear" };
  }

  const deleteMatch = lower.match(
    /(?:delete rule|remove rule|\u0627\u062d\u0630\u0641 \u0642\u0627\u0639\u062f\u0629|\u0627\u0645\u0633\u062d \u0642\u0627\u0639\u062f\u0629)\s*(\d+)/i
  );
  if (deleteMatch) {
    return { action: "delete", id: Number(deleteMatch[1]) };
  }

  const arabicMatch = text.match(
    /(?:\u062d\u0648\u0644|\u062d\u0648\u0651\u0644)\s*(?:\u0631\u0633\u0627\u064a\u0644|\u0631\u0633\u0627\u0626\u0644)?\s*(.+?)\s*(?:\u0644\u0644\u062a\u0644\u064a\u062c\u0631\u0627\u0645|\u0644\u062a\u064a\u0644\u064a\u062c\u0631\u0627\u0645|\u0627\u0644\u0649 \u0627\u0644\u062a\u0644\u064a\u062c\u0631\u0627\u0645|\u0625\u0644\u0649 \u0627\u0644\u062a\u0644\u064a\u062c\u0631\u0627\u0645)$/i
  );
  if (arabicMatch) {
    return { action: "create", senderContains: arabicMatch[1].trim() };
  }

  const englishMatch = text.match(/(?:route|forward|send)\s+(.+?)\s+(?:to|->)\s+telegram/i);
  if (englishMatch) {
    return { action: "create", senderContains: englishMatch[1].trim() };
  }

  return { action: "help" };
}

app.get("/health", (_req, res) => {
  res.json({ ok: true, service: "agent-b", ts: new Date().toISOString() });
});

app.get("/control/routes", (req, res) => {
  if (!verifyControlSecret(req)) {
    return res.status(401).json({ ok: false, error: "invalid_control_secret" });
  }

  return res.json({ ok: true, routes });
});

app.post("/control/routes", (req, res) => {
  if (!verifyControlSecret(req)) {
    return res.status(401).json({ ok: false, error: "invalid_control_secret" });
  }

  const senderContains = String(req.body?.sender_contains || "").trim();
  const targetType = String(req.body?.target_type || "telegram").trim().toLowerCase();
  const targetId = String(req.body?.target_id || TELEGRAM_CHAT_ID).trim();

  if (!senderContains) {
    return res.status(400).json({ ok: false, error: "missing_sender_contains" });
  }

  if (!connectors[targetType]) {
    return res.status(400).json({ ok: false, error: "unsupported_target_type" });
  }

  const route = addRoute({ senderContains, targetType, targetId });
  return res.json({ ok: true, route });
});

app.post("/control/chat", (req, res) => {
  if (!verifyControlSecret(req)) {
    return res.status(401).json({ ok: false, error: "invalid_control_secret" });
  }

  const message = req.body?.message;
  const cmd = parseChatCommand(message);

  if (cmd.action === "list") {
    return res.json({ ok: true, reply: listRoutesText(), routes });
  }

  if (cmd.action === "clear") {
    routes = [];
    return res.json({ ok: true, reply: "All routes cleared.", routes });
  }

  if (cmd.action === "delete") {
    const before = routes.length;
    routes = routes.filter((r) => r.id !== cmd.id);
    if (routes.length === before) {
      return res.json({ ok: true, reply: `Route #${cmd.id} not found.`, routes });
    }
    return res.json({ ok: true, reply: `Deleted route #${cmd.id}.`, routes });
  }

  if (cmd.action === "create") {
    const route = addRoute({ senderContains: cmd.senderContains });
    return res.json({
      ok: true,
      reply: `Created route #${route.id}: sender contains \"${route.senderContains}\" -> telegram(${route.targetId})`,
      route,
      routes,
    });
  }

  return res.json({
    ok: true,
    reply: [
      "Command not recognized.",
      "Examples:",
      "- \u062d\u0648\u0644 \u0631\u0633\u0627\u0626\u0644 CIB \u0644\u0644\u062a\u0644\u064a\u062c\u0631\u0627\u0645",
      "- show routes",
      "- clear routes",
      "- delete rule 2",
    ].join("\n"),
    routes,
  });
});

app.post("/ingest", async (req, res) => {
  try {
    if (!verifySignature(req)) {
      return res.status(401).json({ ok: false, error: "invalid_signature" });
    }

    const payload = req.body || {};
    const msgHash = payload.msg_hash;

    if (!payload.sender || !payload.body || !msgHash) {
      return res.status(400).json({ ok: false, error: "missing_fields" });
    }

    if (seen.has(msgHash)) {
      return res.json({ ok: true, deduped: true });
    }

    seen.set(msgHash, Date.now());

    const message = formatTelegramMessage(payload);
    const targets = resolveTargets(payload.sender);

    if (targets.length === 0) {
      return res.json({ ok: true, skipped: true, reason: "no_matching_route" });
    }

    const uniqueTargets = new Map();
    for (const t of targets) {
      const key = `${t.type}:${t.targetId}`;
      uniqueTargets.set(key, t);
    }

    for (const target of uniqueTargets.values()) {
      const connector = connectors[target.type];
      if (!connector) {
        continue;
      }
      await connector({ text: message, targetId: target.targetId });
    }

    return res.json({ ok: true, routed_to: [...uniqueTargets.keys()] });
  } catch (error) {
    console.error("/ingest failed", error);
    return res.status(500).json({ ok: false, error: "internal_error" });
  }
});

app.listen(port, () => {
  console.log(`Agent B listening on http://0.0.0.0:${port}`);
});
