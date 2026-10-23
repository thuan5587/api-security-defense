/*
 * Licensed under the Apache License, Version 2.0 (the “License”);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an “AS IS” BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.crapi.config;

import com.crapi.entity.User;
import com.crapi.repository.UserRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import io.jsonwebtoken.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.text.ParseException;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class JwtProvider {

  @Autowired private UserRepository userRepository;

  @Value("${app.jwtExpiration}")
  private String jwtExpiration;

  private KeyPair keyPair;

  private RSAKey publicRSAKey;

  private Map<String, Object> publicJwkSet;

  public JwtProvider(@Value("${app.jwksJson}") String jwksJson) {
    try {
      Base64.Decoder decoder = Base64.getDecoder();
      InputStream jwksStream = new ByteArrayInputStream(decoder.decode(jwksJson));
      JWKSet jwkSet = JWKSet.load(jwksStream);
      jwksStream.close();
      List<JWK> keys = jwkSet.getKeys();
      if (keys.size() != 1 || !Objects.equals(keys.get(0).getAlgorithm().getName(), "RS256")) {
        throw new RuntimeException("Invalid JWKS key passed!!!");
      }

      RSAKey rsaKey = keys.get(0).toRSAKey();
      this.publicRSAKey = rsaKey.toPublicJWK();
      this.keyPair = rsaKey.toKeyPair();
      this.publicJwkSet = jwkSet.toJSONObject();
    } catch (IOException | ParseException | JOSEException e) {
      throw new RuntimeException(e);
    }
  }

  public String getPublicJwkSet() {
    Gson gson = new GsonBuilder().setPrettyPrinting().create();
    return gson.toJson(this.publicJwkSet);
  }

  /**
   * @param user
   * @return generated token with expire date
   */
  public String generateJwtToken(User user) {
    int jwtExpirationInt;
    if (jwtExpiration.contains("e+")) jwtExpirationInt = new BigDecimal(jwtExpiration).intValue();
    else jwtExpirationInt = Integer.parseInt(jwtExpiration);
    JwtBuilder builder =
        Jwts.builder()
            .subject(user.getEmail())
            .issuedAt(new Date())
            .expiration(new Date((new Date()).getTime() + jwtExpirationInt))
            .claim("role", user.getRole().getName())
            .signWith(this.keyPair.getPrivate());
    String jwt = builder.compact();
    return jwt;
  }

  /**
   * @param user
   * @return generated apikey token without expiry date
   */
  public String generateApiKey(User user) {
    JwtBuilder builder =
        Jwts.builder()
            .subject(user.getEmail())
            .issuedAt(new Date())
            .claim("role", user.getRole().getName())
            .signWith(this.keyPair.getPrivate());
    String jwt = builder.compact();
    return jwt;
  }

  /**
   * @param token
   * @return username from JWT Token
   */
  public String getUserNameFromJwtToken(String token) throws ParseException {
    // Parse without verifying token signature
    return JWTParser.parse(token).getJWTClaimsSet().getSubject();
  }

  /**
   * @param authToken
   * @return validate token expire and true boolean
   */

public boolean validateJwtToken(String authToken) {
    try {
        // 1. Parse JWT
        SignedJWT signedJWT = SignedJWT.parse(authToken);
        JWSHeader header = signedJWT.getHeader();

        // 2. Chi chap nhan thuat toan RS256
        if (!JWSAlgorithm.RS256.equals(header.getAlgorithm())) {
            log.warn("Rejected JWT: unsupported signing algorithm");
            return false;
        }

        // 3. Xac thuc chu ky bang public key tin cay
        JWSVerifier verifier = new RSASSAVerifier(this.publicRSAKey);

        if (!signedJWT.verify(verifier)) {
            log.warn("Rejected JWT: invalid signature");
            return false;
        }

        // 4. Kiem tra thoi gian het han
        Date now = new Date();
        Date expiration = signedJWT.getJWTClaimsSet().getExpirationTime();

        if (expiration != null && !now.before(expiration)) {
            log.warn("Rejected JWT: token expired");
            return false;
        }

        // 5. Kiem tra thoi diem token co hieu luc (neu co)
        Date notBefore = signedJWT.getJWTClaimsSet().getNotBeforeTime();

        if (notBefore != null && now.before(notBefore)) {
            log.warn("Rejected JWT: token not yet valid");
            return false;
        }

        log.debug("JWT validated successfully");
        return true;

    } catch (ParseException e) {
        log.warn("Rejected JWT: invalid token format or claims");
        return false;

    } catch (JOSEException e) {
        log.error("JWT signature verification failed", e);
        return false;

    } catch (IllegalArgumentException e) {
        log.warn("Rejected JWT: invalid token");
        return false;
    }
}
}
