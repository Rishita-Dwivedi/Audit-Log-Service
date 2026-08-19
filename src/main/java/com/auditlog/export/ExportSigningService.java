package com.auditlog.export;

import org.springframework.stereotype.Service;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

/**
 * docs/EVALUATION_CLOSURE_MATRIX.md item 15 (ARC-03), docs/DECISIONS.md ADR-013: asymmetric
 * (EC/SHA256withECDSA) signing of export manifests, replacing the earlier unsigned
 * concatenated-hash design -- gives a recipient a real trust boundary (verify with only the
 * published public key, no shared secret, no access to this server) rather than just internal
 * consistency.
 *
 * Key is generated fresh on every application startup and lives only in memory. This is a
 * deliberate trade-off, not an oversight: a persistent key would need to come from a secret
 * manager/HSM in production (never committed to source control -- docs/SECURITY.md), and this
 * environment has neither. Documented consequence: a signature cannot be re-verified against
 * the same public key after an application restart, since the key pair is regenerated. This is
 * a genuine limitation for a real deployment, not just a demo inconvenience -- recorded plainly
 * in ADR-013 rather than glossed over.
 */
@Service
public class ExportSigningService {

    public static final String ALGORITHM = "SHA256withECDSA";

    private final KeyPair keyPair;

    public ExportSigningService() {
        this.keyPair = generateKeyPair();
    }

    private static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException | java.security.InvalidAlgorithmParameterException e) {
            throw new IllegalStateException("Failed to generate export-signing key pair", e);
        }
    }

    public String sign(String canonicalManifest) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initSign(privateKey());
            signature.update(canonicalManifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign export manifest", e);
        }
    }

    /** Exposed so a recipient (or a test playing recipient) can verify independently of this server. */
    public boolean verify(String canonicalManifest, String signatureBase64, PublicKey publicKey) {
        try {
            Signature signature = Signature.getInstance(ALGORITHM);
            signature.initVerify(publicKey);
            signature.update(canonicalManifest.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    public String publicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }

    public PublicKey publicKey() {
        return keyPair.getPublic();
    }

    /** Reconstructs a PublicKey from the base64-encoded X.509 form published alongside every export. */
    public static PublicKey decodePublicKey(String base64) throws InvalidKeySpecException {
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            java.security.spec.X509EncodedKeySpec spec = new java.security.spec.X509EncodedKeySpec(bytes);
            return java.security.KeyFactory.getInstance("EC").generatePublic(spec);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("EC key factory not available", e);
        }
    }

    private PrivateKey privateKey() {
        return keyPair.getPrivate();
    }
}
