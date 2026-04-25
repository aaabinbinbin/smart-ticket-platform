package com.smartticket.rag.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * RAG 敏感信息检测与脱敏测试。
 *
 * <p>这些用例保护知识入库前的安全边界，确保常见个人信息和访问凭证不会以明文进入 embedding、
 * MySQL fallback 表或 PGvector。</p>
 */
class SensitiveInfoMaskerTest {

    @Test
    void detectorShouldCoverCommonSensitiveTypes() {
        SensitiveInfoDetector detector = new SensitiveInfoDetector();
        String text = "phone=13812345678 email=user@example.com id=11010119900307123X ip=192.168.1.10 "
                + "Bearer abc.def.ghi password=abc secret=xyz access_key=AKIA123456789";

        assertTrue(detector.containsSensitiveInfo(text));
        assertTrue(detector.detectTypes(text).contains("PHONE"));
        assertTrue(detector.detectTypes(text).contains("EMAIL"));
        assertTrue(detector.detectTypes(text).contains("ID_CARD"));
        assertTrue(detector.detectTypes(text).contains("IP"));
        assertTrue(detector.detectTypes(text).contains("BEARER_TOKEN"));
        assertTrue(detector.detectTypes(text).contains("PASSWORD"));
        assertTrue(detector.detectTypes(text).contains("SECRET"));
        assertTrue(detector.detectTypes(text).contains("ACCESS_KEY"));
    }

    @Test
    void maskerShouldRemoveRawValuesAndKeepTypedPlaceholders() {
        SensitiveInfoMasker masker = new SensitiveInfoMasker(new SensitiveInfoDetector());
        String masked = masker.mask("jwt eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.sig "
                + "phone 13812345678 email user@example.com secret=abc123");

        assertTrue(masked.contains("[JWT_MASKED]"));
        assertTrue(masked.contains("[PHONE_MASKED]"));
        assertTrue(masked.contains("[EMAIL_MASKED]"));
        assertTrue(masked.contains("[SECRET_MASKED]"));
        assertFalse(masked.contains("13812345678"));
        assertFalse(masked.contains("user@example.com"));
        assertFalse(masked.contains("abc123"));
    }
}
