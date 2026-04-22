package com.openclaw.secrets.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeCipherTest {

    private static final String KEK_BASE64 =
        Base64.getEncoder().encodeToString(new byte[32]); // 32 zero bytes – fine for unit tests

    @Test
    void shouldRoundTripPlaintext() {
        final EnvelopeCipher cipher = EnvelopeCipher.fromBase64Kek(KEK_BASE64);
        final String plaintext = "sk-api-key-123";

        final EnvelopeCipher.Envelope sealed = cipher.encryptString(plaintext);

        assertThat(cipher.decryptString(sealed)).isEqualTo(plaintext);
        // Ciphertext MUST be different from plaintext bytes.
        assertThat(sealed.dataCiphertext()).isNotEqualTo(plaintext.getBytes());
    }

    @Test
    void everyEncryptionUsesAFreshDekAndIv() {
        final EnvelopeCipher cipher = EnvelopeCipher.fromBase64Kek(KEK_BASE64);

        final EnvelopeCipher.Envelope a = cipher.encryptString("hello");
        final EnvelopeCipher.Envelope b = cipher.encryptString("hello");

        assertThat(a.dataIv()).isNotEqualTo(b.dataIv());
        assertThat(a.dekIv()).isNotEqualTo(b.dekIv());
        assertThat(a.dekCiphertext()).isNotEqualTo(b.dekCiphertext());
        assertThat(a.dataCiphertext()).isNotEqualTo(b.dataCiphertext());
    }

    @Test
    void decryptWithTamperedCiphertextFails() {
        final EnvelopeCipher cipher = EnvelopeCipher.fromBase64Kek(KEK_BASE64);
        final EnvelopeCipher.Envelope sealed = cipher.encryptString("secret");
        final byte[] ct = sealed.dataCiphertext().clone();
        ct[0] ^= 0x01;
        final EnvelopeCipher.Envelope tampered = new EnvelopeCipher.Envelope(
            ct, sealed.dataIv(), sealed.dekCiphertext(), sealed.dekIv());

        assertThatThrownBy(() -> cipher.decrypt(tampered))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("envelope.decrypt.failed");
    }

    @Test
    void decryptWithWrongKekFails() {
        final EnvelopeCipher enc = EnvelopeCipher.fromBase64Kek(KEK_BASE64);
        final byte[] otherRaw = new byte[32];
        otherRaw[0] = 1;
        final EnvelopeCipher other = EnvelopeCipher.fromBase64Kek(Base64.getEncoder().encodeToString(otherRaw));

        final EnvelopeCipher.Envelope sealed = enc.encryptString("secret");

        assertThatThrownBy(() -> other.decrypt(sealed))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectShortKek() {
        final String shortKek = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> EnvelopeCipher.fromBase64Kek(shortKek))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("AES-256");
    }

    @Test
    void rejectBlankKek() {
        assertThatThrownBy(() -> EnvelopeCipher.fromBase64Kek(null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnvelopeCipher.fromBase64Kek("   "))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
