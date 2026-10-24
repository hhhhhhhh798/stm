import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

// MASTER_SECRET is intentionally NOT read here anymore. The Android app no longer holds,
// computes, or sends a shared secret to authenticate a device or obtain a token — device
// auth is Keystore/ECDSA challenge-response only (see SecureNetworkClient.kt / secure_lib.cpp).
// Any MASTER_SECRET this project still uses stays server-side only, never embedded in the
// APK/.so. Do not reintroduce it here without re-reading why it was removed.
const baseUrl = String(process.env.BASE_URL || '').trim();
if (!baseUrl || !/^https?:\/\//i.test(baseUrl)) {
  throw new Error('BASE_URL GitHub secret is required and must be an http(s) URL.');
}

const out = path.resolve('app/src/main/cpp/generated_secure_config.h');

// HKDF-SHA256 derives a per-project AES-256 key from a non-secret build seed.
// The application value is encrypted with AES-256-GCM below.
const seed = Buffer.from('StreamMaster-native-build-seed-v1|com.stream.master', 'utf8');
const salt = Buffer.from('StreamMaster-HKDF-salt-v1', 'utf8');
const info = Buffer.from('StreamMaster native configuration v1', 'utf8');
const key = Buffer.from(crypto.hkdfSync('sha256', seed, salt, info, 32));

function encrypt(plaintext) {
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ciphertext = Buffer.concat([cipher.update(Buffer.from(plaintext, 'utf8')), cipher.final()]);
  const tag = cipher.getAuthTag();
  return { iv, ciphertext, tag };
}

function arr(buf) {
  return [...buf].map(x => `0x${x.toString(16).padStart(2, '0')}`).join(', ');
}

const base = encrypt(baseUrl);
const generatedAt = new Date().toISOString();

const header = `#pragma once
#include <cstddef>
#include <cstdint>

namespace streammaster_native {

inline constexpr uint8_t kAesKey[32] = { ${arr(key)} };

inline constexpr uint8_t kBaseIv[${base.iv.length}] = { ${arr(base.iv)} };
inline constexpr uint8_t kBaseCiphertext[${base.ciphertext.length}] = { ${arr(base.ciphertext)} };
inline constexpr uint8_t kBaseTag[${base.tag.length}] = { ${arr(base.tag)} };
inline constexpr size_t kBasePlaintextLength = ${Buffer.byteLength(baseUrl, 'utf8')};

inline constexpr char kBuildMarker[] = "${generatedAt}";

} // namespace streammaster_native
`;

fs.mkdirSync(path.dirname(out), { recursive: true });
fs.writeFileSync(out, header, 'utf8');
console.log(`Generated ${out}`);
console.log(`Base URL bytes: ${base.ciphertext.length}`);
