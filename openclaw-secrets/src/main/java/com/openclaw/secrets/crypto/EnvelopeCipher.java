package com.openclaw.secrets.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Envelope encryption utility for storing provider credentials at rest.
 *
 * <p><b>Scheme</b> (AES-256-GCM at both layers):
 * <ol>
 *   <li>Every {@link Envelope} carries its own random <b>DEK</b> (Data Encryption Key,
 *       256 bit) — the plaintext is encrypted by the DEK with a random 12-byte IV.</li>
 *   <li>The DEK is itself encrypted by the long-lived <b>KEK</b> (Key Encryption Key,
 *       AES-256, supplied via {@link SecretsCryptoProperties#getKekBase64()}), with its
 *       own random IV.</li>
 *   <li>Only the ciphertexts and IVs land in MySQL — DEK/plaintext never touch disk.</li>
 * </ol>
 *
 * <p>This gives us:
 * <ul>
 *   <li>Rotating the KEK re-encrypts only each row's DEK field (not the bulky payload);</li>
 *   <li>A leaked ciphertext / DEK-ciphertext is useless without the KEK held in env;</li>
 *   <li>The GCM auth tag is part of the ciphertext, so tampering is detected on decrypt.</li>
 * </ul>
 *
 * <p>Thread-safe: holds no per-operation state. {@link Cipher} instances are created
 * per call (JCA cost is negligible on the hot-path since credentials are read at most
 * once per provider call).
 */
public final class EnvelopeCipher {

    /** AES block size in bytes; also the minimum GCM IV length we generate. */
    public static final int IV_BYTES = 12;

    /** GCM authentication tag length in bits (128 is the JCA default maximum). */
    public static final int TAG_BITS = 128;

    /** AES-GCM transformation — 256-bit keys throughout. */
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    /** Symmetric algorithm for the SecretKeySpec. */
    private static final String ALGO = "AES";

    /** Required KEK size in bits (AES-256). */
    private static final int KEK_BITS = 256;

    private final SecretKey kek;
    private final SecureRandom random = new SecureRandom();

    public EnvelopeCipher(final SecretKey kek) {
        Objects.requireNonNull(kek, "kek");
        if (kek.getEncoded() == null || kek.getEncoded().length * 8 != KEK_BITS) {
            throw new IllegalArgumentException(
                "KEK must be AES-" + KEK_BITS + " (" + (KEK_BITS / 8) + " raw bytes)");
        }
        this.kek = kek;
    }

    /**
     * @param kekBase64 Base64-encoded 32-byte (AES-256) master key.
     */
    public static EnvelopeCipher fromBase64Kek(final String kekBase64) {
        if (kekBase64 == null || kekBase64.isBlank()) {
            throw new IllegalArgumentException("kekBase64 must not be blank");
        }
        final byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(kekBase64);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("kekBase64 is not valid Base64", iae);
        }
        return new EnvelopeCipher(new SecretKeySpec(bytes, ALGO));
    }

    /** Seal {@code plaintext} into an envelope. The returned record holds four
     * byte arrays safe to persist; the original plaintext can be zeroed after. */
    public Envelope encrypt(final byte[] plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        try {
            final SecretKey dek = newDek();
            final byte[] dataIv = newIv();
            final byte[] dataCt = doCipher(Cipher.ENCRYPT_MODE, dek, dataIv, plaintext);
            final byte[] dekIv = newIv();
            final byte[] dekCt = doCipher(Cipher.ENCRYPT_MODE, kek, dekIv, dek.getEncoded());
            return new Envelope(dataCt, dataIv, dekCt, dekIv);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("envelope.encrypt.failed", e);
        }
    }

    /** Reverse of {@link #encrypt(byte[])}. Any tampering on {@code envelope}
     * surfaces as a {@link javax.crypto.AEADBadTagException}-backed exception. */
    public byte[] decrypt(final Envelope envelope) {
        Objects.requireNonNull(envelope, "envelope");
        try {
            final byte[] dekRaw = doCipher(Cipher.DECRYPT_MODE, kek, envelope.dekIv(), envelope.dekCiphertext());
            final SecretKey dek = new SecretKeySpec(dekRaw, ALGO);
            return doCipher(Cipher.DECRYPT_MODE, dek, envelope.dataIv(), envelope.dataCiphertext());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("envelope.decrypt.failed", e);
        }
    }

    /** Convenience for UTF-8 string payloads. */
    public Envelope encryptString(final String plaintext) {
        Objects.requireNonNull(plaintext, "plaintext");
        return encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String decryptString(final Envelope envelope) {
        return new String(decrypt(envelope), java.nio.charset.StandardCharsets.UTF_8);
    }

    private SecretKey newDek() throws GeneralSecurityException {
        final KeyGenerator kg = KeyGenerator.getInstance(ALGO);
        kg.init(KEK_BITS, random);
        return kg.generateKey();
    }

    private byte[] newIv() {
        final byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        return iv;
    }

    private static byte[] doCipher(final int mode,
                                    final SecretKey key,
                                    final byte[] iv,
                                    final byte[] input) throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
        return cipher.doFinal(input);
    }

    /**
     * Four byte arrays that together constitute one sealed credential. Owner MUST
     * store all four — losing any one field renders the envelope unrecoverable.
     */
    public record Envelope(byte[] dataCiphertext, byte[] dataIv, byte[] dekCiphertext, byte[] dekIv) {

        public Envelope {
            Objects.requireNonNull(dataCiphertext, "dataCiphertext");
            Objects.requireNonNull(dataIv, "dataIv");
            Objects.requireNonNull(dekCiphertext, "dekCiphertext");
            Objects.requireNonNull(dekIv, "dekIv");
            if (dataIv.length != IV_BYTES || dekIv.length != IV_BYTES) {
                throw new IllegalArgumentException("GCM IV must be " + IV_BYTES + " bytes");
            }
        }
    }
}
