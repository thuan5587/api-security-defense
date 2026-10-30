import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.JWKSet;

public class GenerateTestJWKS {
    public static void main(String[] args) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();

        RSAKey key = new RSAKey.Builder(
            (java.security.interfaces.RSAPublicKey) pair.getPublic()
        )
        .privateKey((java.security.interfaces.RSAPrivateKey) pair.getPrivate())
        .algorithm(JWSAlgorithm.RS256)
        .keyID("otp-test-key")
        .build();

        String json = new JWKSet(key).toString(false);
        String encoded = Base64.getEncoder().encodeToString(
            json.getBytes(StandardCharsets.UTF_8)
        );

        System.out.println(encoded);
    }
}
