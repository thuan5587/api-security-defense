import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.*;

public class GenerateValidJWT {
    public static void main(String[] args) throws Exception {
        JWKSet jwks = JWKSet.parse(Files.readString(Path.of("jwks.json")));
        RSAKey key = jwks.getKeys().get(0).toRSAKey();

        long now = System.currentTimeMillis();

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
            .subject("test@example.com")
            .issueTime(new Date(now))
            .expirationTime(new Date(now + 3600000))
            .build();

        SignedJWT jwt = new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(key.getKeyID())
                .build(),
            claims
        );

        jwt.sign(new RSASSASigner(key));

        Files.writeString(
            Path.of("jwt-test-tools", "valid-token.txt"),
            jwt.serialize()
        );

        System.out.println("DA TAO valid-token.txt");
    }
}
