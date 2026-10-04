package cn.ybdata.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2-HMAC-SHA256 secret hashing ({@code pbkdf2_sha256$iterations$salt$hash}) and small digests. */
public final class Passwords {

    private static final int ITERATIONS = 120_000;

    private Passwords() {}

    public static String hash(String secret) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return "pbkdf2_sha256$" + ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(pbkdf2(secret, salt, ITERATIONS));
    }

    public static boolean matches(String secret, String stored) {
        if (secret == null || stored == null) return false;
        String[] p = stored.split("\\$");
        if (p.length != 4 || !"pbkdf2_sha256".equals(p[0])) return false;
        try {
            int iter = Integer.parseInt(p[1]);
            byte[] salt = Base64.getDecoder().decode(p[2]);
            byte[] want = Base64.getDecoder().decode(p[3]);
            return MessageDigest.isEqual(want, pbkdf2(secret, salt, iter));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static byte[] pbkdf2(String secret, byte[] salt, int iterations) {
        try {
            var spec = new PBEKeySpec(secret.toCharArray(), salt, iterations, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String sha256Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
