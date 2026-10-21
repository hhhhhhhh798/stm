package com.stream.master

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * One hardware-backed EC key pair per install, generated and held inside Android
 * Keystore (StrongBox — a separate secure-element chip — when the device has one,
 * the Trusted Execution Environment otherwise). The private key material never
 * leaves the keystore: not to this process' memory as bytes, not to disk, not over
 * the network. The app can only ever ask the keystore to sign a challenge with it.
 *
 * This is what actually changes the security model, not just hides it better:
 * a value extracted from the app (like the previous shared MASTER_SECRET baked into
 * every install) is, by definition, the same for everyone and can be copied and
 * replayed from anywhere. A Keystore-held private key has no "value" to extract in
 * the first place — the most a full teardown of the app can reveal is the *method*
 * used to ask for a signature, which is useless without physically running it on
 * that exact device's secure hardware.
 *
 * See SecureNetworkClient: this is the only token bootstrap path. There is no
 * remaining fallback to a shared secret — a device that cannot complete the
 * Keystore/ECDSA challenge-response simply does not get a token.
 */
object DeviceIdentity {

    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "sm_device_identity_v1"

    /** Creates the key pair on first call; every call after returns the same one. */
    @JvmStatic
    fun getOrCreatePublicKey(): PublicKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        ks.getCertificate(ALIAS)?.let { return it.publicKey }
        return generateKeyPair().public
    }

    /** True once a key pair exists locally. Does not mean the server has accepted it yet — see SecureStorageManager.isDeviceKeyRegistered(). */
    @JvmStatic
    fun hasKeyPair(): Boolean {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        return ks.containsAlias(ALIAS)
    }

    /** Base64 (NO_WRAP) X.509 SubjectPublicKeyInfo of the public key — safe to send to the server as-is. */
    @JvmStatic
    fun publicKeyBase64(): String =
        Base64.encodeToString(getOrCreatePublicKey().encoded, Base64.NO_WRAP)

    /**
     * Signs [data] (SHA-256withECDSA) using the private key inside the keystore.
     * The key is never read out to do this — the OS performs the signing operation
     * inside the secure hardware and only the resulting signature bytes come back.
     */
    @JvmStatic
    fun sign(data: ByteArray): String {
        getOrCreatePublicKey() // ensures the pair exists
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val privateKey = ks.getKey(ALIAS, null) as? PrivateKey
            ?: throw IllegalStateException("Device identity key is missing from the keystore")
        val signature = Signature.getInstance("SHA256withECDSA").apply {
            initSign(privateKey)
            update(data)
        }
        return Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
    }

    private fun generateKeyPair(): KeyPair {
        val purpose = KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY

        fun spec(strongBox: Boolean) = KeyGenParameterSpec.Builder(ALIAS, purpose)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .apply {
                // setIsStrongBoxBacked exists only on API 28+ (minSdk is 24: many TVs run older Android).
                if (strongBox && android.os.Build.VERSION.SDK_INT >= 28) setIsStrongBoxBacked(true)
            }
            .build()

        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            try {
                // Prefer the dedicated secure-element chip when this device has one.
                generator.initialize(spec(true))
                return generator.generateKeyPair()
            } catch (_: StrongBoxUnavailableException) {
                // fall through to TEE/software-backed keystore
            } catch (_: Exception) {
                // some set-top boxes throw ProviderException instead
            }
        }
        generator.initialize(spec(false))
        return generator.generateKeyPair()
    }
}
