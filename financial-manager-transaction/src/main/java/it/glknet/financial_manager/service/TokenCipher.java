package it.glknet.financial_manager.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Cifra i segreti salvati nel database (es. il refresh token di Home Assistant) con AES-256-GCM. La chiave arriva da
 * APP_SECRET_KEY (32 byte in base64): senza, le funzioni che salvano segreti restano disattivate.
 */
@Component
public class TokenCipher {

	private static final int IV_BYTES = 12;
	private static final int TAG_BITS = 128;
	private static final SecureRandom RANDOM = new SecureRandom();

	private final SecretKeySpec key;

	public TokenCipher(@Value("${app.secret-key:}") String base64Key) {
		byte[] raw = base64Key.isBlank() ? new byte[0] : Base64.getDecoder().decode(base64Key.trim());
		if (raw.length > 0 && raw.length != 32) {
			throw new IllegalStateException("APP_SECRET_KEY deve essere di 32 byte in base64 (es. openssl rand -base64 32)");
		}
		this.key = raw.length == 32 ? new SecretKeySpec(raw, "AES") : null;
	}

	public boolean enabled() {
		return key != null;
	}

	public String encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			RANDOM.nextBytes(iv);
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.ENCRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, iv));
			byte[] enc = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + enc.length).put(iv).put(enc).array());
		} catch (GeneralSecurityException e) {
			throw new IllegalStateException("Cifratura non riuscita", e);
		}
	}

	public String decrypt(String stored) {
		try {
			byte[] all = Base64.getDecoder().decode(stored);
			Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
			c.init(Cipher.DECRYPT_MODE, requireKey(), new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
			return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
		} catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new IllegalStateException("Decifratura non riuscita (chiave APP_SECRET_KEY cambiata?)", e);
		}
	}

	private SecretKeySpec requireKey() {
		if (key == null) {
			throw new IllegalStateException("APP_SECRET_KEY non impostata");
		}
		return key;
	}
}
