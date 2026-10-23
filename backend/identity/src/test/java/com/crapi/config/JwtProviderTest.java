
package com.crapi.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class JwtProviderTest {

    private JwtProvider jwtProvider;
    private RSAKey rsaKey;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);

        KeyPair keyPair = generator.generateKeyPair();

        rsaKey = new RSAKey.Builder(
                (java.security.interfaces.RSAPublicKey) keyPair.getPublic()
        )
                .privateKey(
                        (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate()
                )
                .algorithm(JWSAlgorithm.RS256)
                .keyID("test-key")
                .build();

        String jwksJson = Base64.getEncoder().encodeToString(
                new JWKSet(rsaKey).toString().getBytes(
                        java.nio.charset.StandardCharsets.UTF_8
                )
        );

        jwtProvider = new JwtProvider(jwksJson);
    }

    private String createRs256Token(Date expiration) throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test@example.com")
                .issueTime(new Date())
                .expirationTime(expiration)
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims
        );

        jwt.sign(new RSASSASigner(rsaKey));
        return jwt.serialize();
    }

    @Test
    void validRs256TokenShouldBeAccepted() throws Exception {
        String token = createRs256Token(
                new Date(System.currentTimeMillis() + 60000)
        );

        assertTrue(jwtProvider.validateJwtToken(token));
    }

    @Test
    void expiredTokenShouldBeRejected() throws Exception {
        String token = createRs256Token(
                new Date(System.currentTimeMillis() - 60000)
        );

        assertFalse(jwtProvider.validateJwtToken(token));
    }

    @Test
    void tamperedSignatureShouldBeRejected() throws Exception {
        String token = createRs256Token(
                new Date(System.currentTimeMillis() + 60000)
        );

        String[] parts = token.split("\\.");

        // Thay doi signature nhung van giu dinh dang JWT hop le
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A')
                + parts[2].substring(1);

        String tamperedToken = String.join(".", parts);

        assertFalse(jwtProvider.validateJwtToken(tamperedToken));
    }

    @Test
    void hs256TokenShouldBeRejected() throws Exception {
        byte[] secret = new byte[32];
        java.util.Arrays.fill(secret, (byte) 65);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test@example.com")
                .expirationTime(
                        new Date(System.currentTimeMillis() + 60000)
                )
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.HS256),
                claims
        );

        jwt.sign(new MACSigner(secret));

        assertFalse(jwtProvider.validateJwtToken(jwt.serialize()));
    }

    @Test
    void malformedTokenShouldBeRejected() {
        assertFalse(jwtProvider.validateJwtToken("invalid.jwt.token"));
    }
    
    @Test
    void apiKeyWithoutExpirationShouldBeAccepted() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test@example.com")
                .issueTime(new Date())
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims
        );

        jwt.sign(new RSASSASigner(rsaKey));

        String apiKey = jwt.serialize();

        // Confirm the API Key has no expiration claim.
        assertNull(SignedJWT.parse(apiKey)
                .getJWTClaimsSet().getExpirationTime());

        // Valid RSA signature must be accepted.
        assertTrue(jwtProvider.validateJwtToken(apiKey));
    }

    @Test
    void apiKeyWithInvalidSignatureShouldBeRejected() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject("test@example.com")
                .issueTime(new Date())
                .build();

        SignedJWT jwt = new SignedJWT(
                new JWSHeader(JWSAlgorithm.RS256),
                claims
        );

        jwt.sign(new RSASSASigner(rsaKey));

        String[] parts = jwt.serialize().split("\\.");

        // Change the signature while keeping valid Base64URL format.
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);

        String forgedApiKey = String.join(".", parts);

        assertFalse(jwtProvider.validateJwtToken(forgedApiKey));
    }

}
