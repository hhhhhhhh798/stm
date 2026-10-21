#include <jni.h>
#include <android/log.h>
#include <array>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include "generated_secure_config.h"

namespace {

using streammaster_native::kAesKey;

static uint8_t xtime(uint8_t x) {
    return static_cast<uint8_t>((x << 1) ^ ((x & 0x80u) ? 0x1Bu : 0u));
}

static uint8_t gfMul8(uint8_t a, uint8_t b) {
    uint8_t r = 0;
    for (int i = 0; i < 8; ++i) {
        if (b & 1u) r ^= a;
        a = xtime(a);
        b >>= 1u;
    }
    return r;
}

static uint8_t aesSbox(uint8_t x) {
    if (x == 0) return 0x63;
    uint8_t r = 1;
    uint8_t b = x;
    uint32_t e = 254;
    while (e) {
        if (e & 1u) r = gfMul8(r, b);
        b = gfMul8(b, b);
        e >>= 1u;
    }
    return static_cast<uint8_t>(
        r ^ static_cast<uint8_t>((r << 1) | (r >> 7)) ^
        static_cast<uint8_t>((r << 2) | (r >> 6)) ^
        static_cast<uint8_t>((r << 3) | (r >> 5)) ^
        static_cast<uint8_t>((r << 4) | (r >> 4)) ^ 0x63u);
}

static uint32_t subWord(uint32_t w) {
    return (static_cast<uint32_t>(aesSbox(static_cast<uint8_t>(w >> 24))) << 24) |
           (static_cast<uint32_t>(aesSbox(static_cast<uint8_t>(w >> 16))) << 16) |
           (static_cast<uint32_t>(aesSbox(static_cast<uint8_t>(w >> 8))) << 8) |
           static_cast<uint32_t>(aesSbox(static_cast<uint8_t>(w)));
}

static uint32_t rotWord(uint32_t w) { return (w << 8) | (w >> 24); }

static uint32_t rcon(int n) {
    uint8_t c = 1;
    for (int i = 1; i < n; ++i) c = xtime(c);
    return static_cast<uint32_t>(c) << 24;
}

class AES256 {
public:
    explicit AES256(const uint8_t key[32]) {
        for (int i = 0; i < 8; ++i) {
            rk_[i] = (static_cast<uint32_t>(key[4 * i]) << 24) |
                     (static_cast<uint32_t>(key[4 * i + 1]) << 16) |
                     (static_cast<uint32_t>(key[4 * i + 2]) << 8) |
                     static_cast<uint32_t>(key[4 * i + 3]);
        }
        for (int i = 8; i < 60; ++i) {
            uint32_t t = rk_[i - 1];
            if (i % 8 == 0) t = subWord(rotWord(t)) ^ rcon(i / 8);
            else if (i % 8 == 4) t = subWord(t);
            rk_[i] = rk_[i - 8] ^ t;
        }
    }

    void encryptBlock(const uint8_t in[16], uint8_t out[16]) const {
        uint8_t s[16];
        std::memcpy(s, in, sizeof(s));
        addRoundKey(s, 0);
        for (int round = 1; round <= 13; ++round) {
            subBytes(s);
            shiftRows(s);
            mixColumns(s);
            addRoundKey(s, round);
        }
        subBytes(s);
        shiftRows(s);
        addRoundKey(s, 14);
        std::memcpy(out, s, sizeof(s));
    }

private:
    uint32_t rk_[60]{};

    void addRoundKey(uint8_t s[16], int round) const {
        for (int c = 0; c < 4; ++c) {
            const uint32_t w = rk_[round * 4 + c];
            s[4 * c] ^= static_cast<uint8_t>(w >> 24);
            s[4 * c + 1] ^= static_cast<uint8_t>(w >> 16);
            s[4 * c + 2] ^= static_cast<uint8_t>(w >> 8);
            s[4 * c + 3] ^= static_cast<uint8_t>(w);
        }
    }

    static void subBytes(uint8_t s[16]) {
        for (int i = 0; i < 16; ++i) s[i] = aesSbox(s[i]);
    }

    static void shiftRows(uint8_t s[16]) {
        uint8_t t[16];
        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 4; ++col) {
                t[4 * col + row] = s[4 * ((col + row) % 4) + row];
            }
        }
        std::memcpy(s, t, sizeof(t));
    }

    static void mixColumns(uint8_t s[16]) {
        for (int c = 0; c < 4; ++c) {
            uint8_t *a = s + 4 * c;
            const uint8_t x0 = a[0], x1 = a[1], x2 = a[2], x3 = a[3];
            a[0] = xtime(x0) ^ (xtime(x1) ^ x1) ^ x2 ^ x3;
            a[1] = x0 ^ xtime(x1) ^ (xtime(x2) ^ x2) ^ x3;
            a[2] = x0 ^ x1 ^ xtime(x2) ^ (xtime(x3) ^ x3);
            a[3] = (xtime(x0) ^ x0) ^ x1 ^ x2 ^ xtime(x3);
        }
    }
};

static void xor16(uint8_t a[16], const uint8_t b[16]) {
    for (int i = 0; i < 16; ++i) a[i] ^= b[i];
}

static void ghashMultiply(const uint8_t x[16], const uint8_t y[16], uint8_t out[16]) {
    uint8_t z[16]{};
    uint8_t v[16];
    std::memcpy(v, y, 16);
    for (int bit = 0; bit < 128; ++bit) {
        if ((x[bit / 8] >> (7 - (bit % 8))) & 1u) xor16(z, v);
        const bool lsb = (v[15] & 1u) != 0;
        for (int i = 15; i > 0; --i) {
            v[i] = static_cast<uint8_t>((v[i] >> 1) | ((v[i - 1] & 1u) << 7));
        }
        v[0] >>= 1;
        if (lsb) v[0] ^= 0xE1u;
    }
    std::memcpy(out, z, 16);
}

static void ghash(const uint8_t h[16], const uint8_t *ciphertext, size_t length, uint8_t out[16]) {
    uint8_t y[16]{};
    uint8_t block[16]{};
    for (size_t offset = 0; offset < length; offset += 16) {
        std::memset(block, 0, 16);
        const size_t n = (length - offset < 16) ? length - offset : 16;
        std::memcpy(block, ciphertext + offset, n);
        xor16(y, block);
        ghashMultiply(y, h, y);
    }

    std::memset(block, 0, 16);
    const uint64_t bits = static_cast<uint64_t>(length) * 8ULL;
    for (int i = 0; i < 8; ++i) block[8 + i] = static_cast<uint8_t>(bits >> (56 - 8 * i));
    xor16(y, block);
    ghashMultiply(y, h, y);
    std::memcpy(out, y, 16);
}

static void increment32(uint8_t counter[16]) {
    uint32_t n = (static_cast<uint32_t>(counter[12]) << 24) |
                 (static_cast<uint32_t>(counter[13]) << 16) |
                 (static_cast<uint32_t>(counter[14]) << 8) |
                 static_cast<uint32_t>(counter[15]);
    ++n;
    counter[12] = static_cast<uint8_t>(n >> 24);
    counter[13] = static_cast<uint8_t>(n >> 16);
    counter[14] = static_cast<uint8_t>(n >> 8);
    counter[15] = static_cast<uint8_t>(n);
}

static void gctr(const AES256 &aes, const uint8_t j0[16], const uint8_t *input, size_t length, uint8_t *output) {
    uint8_t counter[16];
    uint8_t stream[16];
    std::memcpy(counter, j0, 16);
    for (size_t offset = 0; offset < length; offset += 16) {
        increment32(counter);
        aes.encryptBlock(counter, stream);
        const size_t n = (length - offset < 16) ? length - offset : 16;
        for (size_t i = 0; i < n; ++i) output[offset + i] = input[offset + i] ^ stream[i];
    }
}

static bool constantTimeEqual16(const uint8_t a[16], const uint8_t b[16]) {
    uint8_t diff = 0;
    for (int i = 0; i < 16; ++i) diff |= static_cast<uint8_t>(a[i] ^ b[i]);
    return diff == 0;
}

static bool aesGcmDecrypt(const uint8_t key[32], const uint8_t iv[12],
                          const uint8_t *ciphertext, size_t ciphertextLength,
                          const uint8_t tag[16], std::string &plaintext) {
    AES256 aes(key);
    uint8_t zero[16]{};
    uint8_t h[16]{};
    aes.encryptBlock(zero, h);

    uint8_t j0[16]{};
    std::memcpy(j0, iv, 12);
    j0[15] = 1;

    uint8_t s[16]{};
    ghash(h, ciphertext, ciphertextLength, s);
    uint8_t encryptedJ0[16]{};
    aes.encryptBlock(j0, encryptedJ0);
    xor16(encryptedJ0, s);
    if (!constantTimeEqual16(encryptedJ0, tag)) return false;

    std::vector<uint8_t> output(ciphertextLength);
    if (ciphertextLength != 0) gctr(aes, j0, ciphertext, ciphertextLength, output.data());
    plaintext.assign(reinterpret_cast<const char *>(output.data()), output.size());
    return true;
}

static jstring decryptToJString(JNIEnv *env, const uint8_t *iv, const uint8_t *ciphertext,
                                size_t ciphertextLength, const uint8_t tag[16]) {
    std::string plaintext;
    if (!aesGcmDecrypt(kAesKey, iv, ciphertext, ciphertextLength, tag, plaintext)) {
        __android_log_print(ANDROID_LOG_ERROR, "StreamMasterJNI", "native configuration authentication failed");
        return nullptr;
    }
    return env->NewStringUTF(plaintext.c_str());
}

} // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_stream_master_SecureNetworkClient_getBaseUrl(JNIEnv *env, jobject) {
    return decryptToJString(env,
                            streammaster_native::kBaseIv,
                            streammaster_native::kBaseCiphertext,
                            sizeof(streammaster_native::kBaseCiphertext),
                            streammaster_native::kBaseTag);
}

// getMasterSecret() was removed: the Android app no longer holds, computes, or sends any
// shared secret to authenticate a device or obtain a token (see SecureNetworkClient.kt —
// device auth is Keystore/ECDSA challenge-response only, with no fallback). MASTER_SECRET
// itself stays server-side only; generate_native_config.mjs no longer embeds it here at all.
