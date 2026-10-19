const express = require('express');
const cors = require('cors');
const crypto = require('crypto');
const admin = require('firebase-admin');
const app = express();
app.use(cors());
app.use(express.json({ limit: '20kb' }));

// Configure FIREBASE_SERVICE_ACCOUNT in Render. Never commit secrets to GitHub.
if (!admin.apps.length) {
  const raw = process.env.FIREBASE_SERVICE_ACCOUNT;
  if (raw) admin.initializeApp({
    credential: admin.credential.cert(JSON.parse(raw)),
    projectId: process.env.FIREBASE_PROJECT_ID || undefined
  });
  else console.error('Missing FIREBASE_SERVICE_ACCOUNT environment variable.');
}
const db = () => admin.firestore();
const validPurposes = ['REGISTRATION', 'FORGOT_PASSWORD'];
function validateEmail(raw) {
  if (typeof raw !== 'string') return null;
  const email = raw.trim();
  if (email.length > 254 || /\s/.test(email)) return null;
  const parts = email.split('@');
  if (parts.length !== 2) return null;
  const [local, domain] = parts;
  if (!local || local.length > 64 || local.startsWith('.') || local.endsWith('.') || local.includes('..')) return null;
  if (!/^[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+(\.[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+)*$/.test(local)) return null;
  const labels = domain.split('.');
  if (!domain || domain.length > 253 || labels.length < 2 || labels.some(x => !x || x.length > 63 || !/^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?$/.test(x))) return null;
  if (labels[labels.length - 1].length < 2 || /^\d+$/.test(labels[labels.length - 1])) return null;
  return `${local}@${domain.toLowerCase()}`;
}
const challengeId = (purpose, email) => crypto.createHash('sha256').update(`${purpose}:${email.toLowerCase()}`).digest('hex');
async function sendEmail(email, code) {
  if (!process.env.RESEND_API_KEY || !process.env.EMAIL_FROM) throw new Error('Resend email settings missing');
  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: { Authorization: `Bearer ${process.env.RESEND_API_KEY}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      from: process.env.EMAIL_FROM, to: [email], subject: 'Your UERI Verification Code',
      text: `Your UERI verification code is: ${code}\n\nThis code expires in 10 minutes. Do not share it.`,
      html: `<div style="font-family:Arial,sans-serif;color:#1e293b"><h2>UERI Verification Code</h2><p>Your 6-digit code:</p><p style="font-size:28px;font-weight:bold;letter-spacing:5px">${code}</p><p>Expires in 10 minutes. Do not share it.</p></div>`
    })
  });
  if (!response.ok) { console.error('Resend rejected email:', response.status, (await response.text()).slice(0, 300)); throw new Error('Email delivery failed'); }
}
app.get('/', (_req, res) => res.json({ service: 'UERI backend', status: 'ok' }));
app.get('/health', (_req, res) => res.json({ status: 'ok' }));

app.post('/api/send-email-otp', async (req, res) => {
  try {
    if (!admin.apps.length) return res.status(503).json({ error: 'Firebase credentials are not configured.' });
    const email = validateEmail(req.body?.email), purpose = req.body?.purpose;
    if (!email) return res.status(400).json({ error: 'Please enter a valid email address.' });
    if (!validPurposes.includes(purpose)) return res.status(400).json({ error: 'Invalid verification purpose.' });
    const ref = db().collection('otpVerifications').doc(challengeId(purpose, email));
    const snap = await ref.get(), now = Date.now();
    if (snap.exists) {
      const old = snap.data();
      if (old.lastSentAt && now - old.lastSentAt < 60000) return res.status(429).json({ error: `Please wait ${Math.ceil((60000 - (now - old.lastSentAt)) / 1000)} seconds before requesting a new code.` });
    }
    const code = String(crypto.randomInt(100000, 1000000));
    const salt = crypto.randomBytes(16).toString('hex');
    const codeHash = crypto.createHash('sha256').update(code + salt).digest('hex');
    await sendEmail(email, code);
    await ref.set({ targetHash: challengeId(purpose, email), purpose, codeHash, salt, createdAt: now, lastSentAt: now, expiresAt: now + 600000, attemptsRemaining: 3, isVerified: false });
    return res.json({ accepted: true, message: 'Verification email sent.' });
  } catch (err) { console.error('send OTP error:', err.message); return res.status(500).json({ error: 'Could not send verification email. Check backend configuration.' }); }
});

app.post('/api/verify-email-otp', async (req, res) => {
  try {
    if (!admin.apps.length) return res.status(503).json({ error: 'Firebase credentials are not configured.' });
    const email = validateEmail(req.body?.email), purpose = req.body?.purpose;
    const code = typeof req.body?.code === 'string' ? req.body.code.trim() : '';
    if (!email || !validPurposes.includes(purpose) || !/^\d{6}$/.test(code)) return res.status(400).json({ error: 'Invalid or expired verification code.' });
    const ref = db().collection('otpVerifications').doc(challengeId(purpose, email));
    const snap = await ref.get();
    if (!snap.exists) return res.status(400).json({ error: 'Invalid or expired verification code.' });
    const record = snap.data(), now = Date.now();
    if (record.purpose !== purpose || now > record.expiresAt || record.isVerified) return res.status(400).json({ error: 'Invalid, expired, or already-used verification code.' });
    if (record.attemptsRemaining <= 0) { await ref.delete(); return res.status(429).json({ error: 'Too many failed attempts. Request a new code.' }); }
    const expected = crypto.createHash('sha256').update(code + record.salt).digest('hex');
    const a = Buffer.from(record.codeHash, 'hex'), b = Buffer.from(expected, 'hex');
    const match = a.length === b.length && crypto.timingSafeEqual(a, b);
    if (!match) {
      const left = record.attemptsRemaining - 1;
      if (left <= 0) await ref.delete(); else await ref.update({ attemptsRemaining: left });
      return res.status(left <= 0 ? 429 : 400).json({ error: left <= 0 ? 'Too many failed attempts. Request a new code.' : 'Invalid or expired verification code.' });
    }
    await ref.update({ isVerified: true, verifiedAt: now });
    return res.json({ success: true, verified: true });
  } catch (err) { console.error('verify OTP error:', err.message); return res.status(500).json({ error: 'Could not verify code. Please try again.' }); }
});

const port = Number(process.env.PORT || 3000);
app.listen(port, '0.0.0.0', () => console.log(`UERI backend listening on ${port}`));
