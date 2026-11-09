package com.smallbiz.billing;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 激活码原文加密（AES-256-GCM）。密钥来自环境变量 CODE_ENCRYPTION_KEY（Base64 编码的 32 字节）。
 * 没有配置密钥时不可用：发码台关闭，生成激活码时原文只显示一次（和以前一样）。
 */
@Component
public class CodeCrypto {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CodeCrypto(@Value("${smallbiz.codes.encryption-key:}") String base64Key) {
        SecretKey k = null;
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] raw = Base64.getDecoder().decode(base64Key.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("CODE_ENCRYPTION_KEY 必须是 Base64 编码的 32 字节（openssl rand -base64 32）");
            }
            k = new SecretKeySpec(raw, "AES");
        }
        this.key = k;
    }

    public boolean available() {
        return key != null;
    }

    public byte[] encrypt(String plain) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 解密；密钥不对或数据被改动时抛出异常（不会返回错误的激活码）。 */
    public String decrypt(byte[] data) {
        requireKey();
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES));
            return new String(c.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法解密激活码（密钥不正确？）", e);
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("未配置 CODE_ENCRYPTION_KEY");
        }
    }
}
