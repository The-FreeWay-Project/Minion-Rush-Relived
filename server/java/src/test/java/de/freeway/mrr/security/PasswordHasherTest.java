package de.freeway.mrr.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher(10_000);

    @Test
    void hashProducesPythonCompatibleFormat() {
        String stored = hasher.hash("s3cret-password");

        String[] parts = stored.split("\\$");
        assertThat(parts).hasSize(4);
        assertThat(parts[0]).isEqualTo("pbkdf2_sha256");
        assertThat(parts[1]).isEqualTo("10000");
        assertThat(parts[2]).hasSize(32);
        assertThat(parts[3]).hasSize(64);
        assertThat(stored).doesNotContain("s3cret-password");
    }

    @Test
    void verifyAcceptsCorrectPassword() {
        String stored = hasher.hash("correct horse battery staple");
        assertThat(hasher.verify(stored, "correct horse battery staple")).isTrue();
    }

    @Test
    void verifyRejectsWrongPassword() {
        String stored = hasher.hash("correct");
        assertThat(hasher.verify(stored, "incorrect")).isFalse();
    }

    @Test
    void verifyRejectsMalformedStoredHashes() {
        assertThat(hasher.verify(null, "x")).isFalse();
        assertThat(hasher.verify("", "x")).isFalse();
        assertThat(hasher.verify("plaintext$x$y$z", "x")).isFalse();
        assertThat(hasher.verify("pbkdf2_sha256$abc$zzzz$yyyy", "x")).isFalse();
        assertThat(hasher.verify("pbkdf2_sha256$-1$00$00", "x")).isFalse();
        assertThat(hasher.verify("pbkdf2_sha256$10000$nothex$nothex", "x")).isFalse();
    }

    @Test
    void verifyRejectsNullRawPassword() {
        String stored = hasher.hash("pw");
        assertThat(hasher.verify(stored, null)).isFalse();
    }
}
