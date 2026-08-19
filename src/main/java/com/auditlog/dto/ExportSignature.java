package com.auditlog.dto;

/** publicKeyBase64 is the X.509-encoded EC public key -- see ExportSigningService.decodePublicKey(). */
public record ExportSignature(String algorithm, String publicKeyBase64, String signatureBase64) {
}
