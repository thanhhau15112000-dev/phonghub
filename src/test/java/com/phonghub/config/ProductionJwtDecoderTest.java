package com.phonghub.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.gen.ECKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.springframework.security.oauth2.jwt.JwtException;

class ProductionJwtDecoderTest {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"ES256", "RS256"})
    void verifiesSignatureAndKeepsIssuerAudienceAndExpiryValidation(String algorithm) throws Exception {
        com.nimbusds.jose.jwk.JWK key = algorithm.equals("ES256")
            ? new ECKeyGenerator(Curve.P_256).keyID("qa-key").generate()
            : new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("qa-key").generate();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/jwks", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, jwks.length);
            try (var out = exchange.getResponseBody()) { out.write(jwks); }
        });
        server.start();
        try {
            var properties = new SupabaseProperties();
            properties.setJwksUri("http://127.0.0.1:" + server.getAddress().getPort() + "/jwks");
            properties.setJwtIssuer("https://qa.supabase.co/auth/v1");
            properties.setJwtAudience("authenticated");
            var decoder = new ProductionSecurityConfig(null, properties).jwtDecoder();
            for (String variant : new String[] {"valid", "issuer", "audience", "expired", "signature"}) {
                var token = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.parse(algorithm)).keyID(key.getKeyID()).build(),
                    new JWTClaimsSet.Builder().subject("qa-user")
                        .issuer(variant.equals("issuer") ? "https://other.invalid" : properties.getJwtIssuer())
                        .audience(variant.equals("audience") ? "other" : "authenticated")
                        .issueTime(Date.from(Instant.now().minusSeconds(900)))
                        .expirationTime(Date.from(Instant.now().plusSeconds(variant.equals("expired") ? -300 : 300)))
                        .build());
                var signingKey = variant.equals("signature")
                    ? (algorithm.equals("ES256") ? new ECKeyGenerator(Curve.P_256).generate()
                        : new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).generate()) : key;
                token.sign(signingKey instanceof com.nimbusds.jose.jwk.ECKey ec
                    ? new ECDSASigner(ec)
                    : new com.nimbusds.jose.crypto.RSASSASigner((com.nimbusds.jose.jwk.RSAKey) signingKey));
                if (variant.equals("valid")) assertEquals("qa-user", decoder.decode(token.serialize()).getSubject());
                else assertThrows(JwtException.class, () -> decoder.decode(token.serialize()), variant);
            }
        } finally { server.stop(0); }
    }
}
