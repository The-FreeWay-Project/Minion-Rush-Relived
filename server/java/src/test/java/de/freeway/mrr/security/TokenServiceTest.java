package de.freeway.mrr.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService tokens = new TokenService();

    @Test
    void generateTokenProducesUniqueUrlSafeTokens() {
        String first = tokens.generateToken();
        String second = tokens.generateToken();

        assertThat(first).hasSize(43);
        assertThat(first).matches("[A-Za-z0-9_-]+");
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void hashTokenIsSha256HexAndDeterministic() {
        String token = tokens.generateToken();

        String hash = tokens.hashToken(token);

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(tokens.hashToken(token)).isEqualTo(hash);
        assertThat(tokens.hashToken(tokens.generateToken())).isNotEqualTo(hash);
        assertThat(hash).isNotEqualTo(token);
    }
}
