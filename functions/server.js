
const express = require('express');
const cors = require('cors');
const crypto = require('crypto');

const app = express();

app.use(cors());
app.use(express.json({ limit: '20kb' }));

const PORT = Number(process.env.PORT || 3000);
const SUPABASE_URL = (process.env.SUPABASE_URL || '').replace(/\/+$/, '');
const SUPABASE_SECRET_KEY = process.env.SUPABASE_SECRET_KEY;
const RESEND_API_KEY = process.env.RESEND_API_KEY;
const EMAIL_FROM = process.env.EMAIL_FROM;

const VALID_PURPOSES = ['REGISTRATION', 'FORGOT_PASSWORD'];
const OTP_EXPIRY_MS = 10 * 60 * 1000;
const RESEND_COOLDOWN_MS = 60 * 1000;
const MAX_ATTEMPTS = 3;

function validateEmail(value) {
  if (typeof value !== 'string') return null;

  const email = value.trim().toLowerCase();

  if (
    email.length > 254 ||
    !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  ) {
    return null;
  }

  return email;
}

function challengeId(email, purpose) {
  return crypto
    .createHash('sha256')
    .update(`${purpose}:${email}`)
    .digest('hex');
}

function hashOtp(code, salt) {
  return crypto
    .createHash('sha256')
    .update(code + salt)
    .digest('hex');
}

async function supabaseRequest(path, options = {}) {
  if (!SUPABASE_URL || !SUPABASE_SECRET_KEY) {
    throw new Error('Supabase environment variables are missing.');
  }

  const response = await fetch(
    `${SUPABASE_URL}/rest/v1/${path}`,
    {
      ...options,
      headers: {
        apikey: SUPABASE_SECRET_KEY,
        Authorization: `Bearer ${SUPABASE_SECRET_KEY}`,
        'Content-Type': 'application/json',
        ...options.headers
      }
    }
  );

  const text = await response.text();

  if (!response.ok) {
    console.error('Supabase error:', response.status, text.slice(0, 300));
    throw new Error('Supabase database request failed.');
  }

  return text ? JSON.parse(text) : null;
}

async function sendEmail(email, code) {
  if (!RESEND_API_KEY || !EMAIL_FROM) {
    throw new Error('Resend environment variables are missing.');
  }

  const response = await fetch('https://api.resend.com/emails', {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${RESEND_API_KEY}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      from: EMAIL_FROM,
      to: [email],
      subject: 'Your UERI Verification Code',
      text:
        `Your UERI verification code is: ${code}\n\n` +
        'This code expires in 10 minutes. Do not share it.',
      html: `
        <div style="font-family:Arial,sans-serif;color:#1e293b">
          <h2>UERI Verification Code</h2>
          <p>Your 6-digit verification code is:</p>
          <p style="font-size:28px;font-weight:bold;letter-spacing:5px">
            ${code}
          </p>
          <p>This code expires in 10 minutes. Do not share it.</p>
        </div>
      `
    })
  });

  if (!response.ok) {
    const errorText = await response.text();
    console.error('Resend error:', response.status, errorText.slice(0, 300));
    throw new Error('Resend could not send the email.');
  }
}

app.get('/', (_req, res) => {
  res.json({ service: 'UERI backend', status: 'ok' });
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

app.post('/api/send-email-otp', async (req, res) => {
  try {
    const email = validateEmail(req.body?.email);
    const purpose = req.body?.purpose;

    if (!email) {
      return res.status(400).json({
        error: 'Please enter a valid email address.'
      });
    }

    if (!VALID_PURPOSES.includes(purpose)) {
      return res.status(400).json({
        error: 'Invalid verification purpose.'
      });
    }

    const id = challengeId(email, purpose);
    const rows = await supabaseRequest(
      `otp_verifications?select=*&email=eq.${encodeURIComponent(email)}` +
      `&purpose=eq.${encodeURIComponent(purpose)}&limit=1`
    );

    const previous = rows?.[0];
    const now = Date.now();

    if (previous && now - Number(previous.last_sent_at) < RESEND_COOLDOWN_MS) {
      const waitSeconds = Math.ceil(
        (RESEND_COOLDOWN_MS - (now - Number(previous.last_sent_at))) / 1000
      );

      return res.status(429).json({
        error: `Please wait ${waitSeconds} seconds before requesting another code.`
      });
    }

    const code = String(crypto.randomInt(100000, 1000000));
    const salt = crypto.randomBytes(16).toString('hex');

    const record = {
      email,
      purpose,
      code_hash: hashOtp(code, salt),
      salt,
      created_at: now,
      last_sent_at: now,
      expires_at: now + OTP_EXPIRY_MS,
      attempts_remaining: MAX_ATTEMPTS,
      is_verified: false,
      verified_at: null
    };

    // Save the OTP record first, so failed email delivery does not
    // leave an older code active after a new request.
    await supabaseRequest(
      'otp_verifications?on_conflict=email,purpose',
      {
        method: 'POST',
        headers: {
          Prefer: 'resolution=merge-duplicates,return=minimal'
        },
        body: JSON.stringify(record)
      }
    );

    try {
      await sendEmail(email, code);
    } catch (emailError) {
      // Invalidate the code if delivery fails.
      await supabaseRequest(
        `otp_verifications?email=eq.${encodeURIComponent(email)}` +
        `&purpose=eq.${encodeURIComponent(purpose)}`,
        {
          method: 'PATCH',
          headers: { Prefer: 'return=minimal' },
          body: JSON.stringify({
            expires_at: 0,
            attempts_remaining: 0
          })
        }
      );

      throw emailError;
    }

    return res.json({
      accepted: true,
      message: 'Verification email sent.'
    });
  } catch (error) {
    console.error('Send OTP error:', error.message);

    return res.status(500).json({
      error: 'Could not send verification email. Check backend configuration.'
    });
  }
});

app.post('/api/verify-email-otp', async (req, res) => {
  try {
    const email = validateEmail(req.body?.email);
    const purpose = req.body?.purpose;
    const code =
      typeof req.body?.code === 'string' ? req.body.code.trim() : '';

    if (
      !email ||
      !VALID_PURPOSES.includes(purpose) ||
      !/^\d{6}$/.test(code)
    ) {
      return res.status(400).json({
        error: 'Invalid or expired verification code.'
      });
    }

    const rows = await supabaseRequest(
      `otp_verifications?select=*&email=eq.${encodeURIComponent(email)}` +
      `&purpose=eq.${encodeURIComponent(purpose)}&limit=1`
    );

    const record = rows?.[0];
    const now = Date.now();

    if (
      !record ||
      record.purpose !== purpose ||
      now > Number(record.expires_at) ||
      record.is_verified
    ) {
      return res.status(400).json({
        error: 'Invalid, expired, or already-used verification code.'
      });
    }

    if (Number(record.attempts_remaining) <= 0) {
      return res.status(429).json({
        error: 'Too many failed attempts. Request a new code.'
      });
    }

    const expected = hashOtp(code, record.salt);
    const actualBuffer = Buffer.from(record.code_hash, 'hex');
    const expectedBuffer = Buffer.from(expected, 'hex');

    const matches =
      actualBuffer.length === expectedBuffer.length &&
      crypto.timingSafeEqual(actualBuffer, expectedBuffer);

    if (!matches) {
      const remaining = Number(record.attempts_remaining) - 1;

      await supabaseRequest(
        `otp_verifications?email=eq.${encodeURIComponent(email)}` +
        `&purpose=eq.${encodeURIComponent(purpose)}` +
        `&is_verified=eq.false`,
        {
          method: 'PATCH',
          headers: { Prefer: 'return=minimal' },
          body: JSON.stringify({
            attempts_remaining: remaining,
            ...(remaining <= 0 ? { expires_at: 0 } : {})
          })
        }
      );

      return res.status(remaining <= 0 ? 429 : 400).json({
        error:
          remaining <= 0
            ? 'Too many failed attempts. Request a new code.'
            : 'Invalid or expired verification code.'
      });
    }

    await supabaseRequest(
      `otp_verifications?email=eq.${encodeURIComponent(email)}` +
      `&purpose=eq.${encodeURIComponent(purpose)}` +
      '&is_verified=eq.false',
      {
        method: 'PATCH',
        headers: { Prefer: 'return=minimal' },
        body: JSON.stringify({
          is_verified: true,
          verified_at: now
        })
      }
    );

    return res.json({
      success: true,
      verified: true
    });
  } catch (error) {
    console.error('Verify OTP error:', error.message);

    return res.status(500).json({
      error: 'Could not verify code. Please try again.'
    });
  }
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`UERI backend listening on port ${PORT}`);
});
