package util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class EncryptionUtil {
    public static void main(String[] args) throws Exception {
        String encrypted = encrypt("1");
        System.out.println("클라이언트에서 암호화한 '1': " + encrypted);
    }


    private static final String ALGORITHM = "AES";
    private static final byte[] KEY = "MySecretKey12345".getBytes(); // 16바이트
    private static final SecretKeySpec KEY_SPEC = new SecretKeySpec(KEY, ALGORITHM);

    public static String encrypt(String plainText) throws Exception {
        if (plainText == null) throw new IllegalArgumentException("암호화할 문자열이 null입니다.");
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, KEY_SPEC);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    public static String decrypt(String encryptedText) throws Exception {
        if (encryptedText == null || encryptedText.trim().isEmpty()) {
            throw new IllegalArgumentException("복호화할 문자열이 null 또는 비어 있습니다.");
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);
            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding"); // ✅ 암호화와 동일하게 맞춰줌
            cipher.init(Cipher.DECRYPT_MODE, KEY_SPEC);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | javax.crypto.BadPaddingException e) {
            System.err.println("❌ 복호화 실패: 잘못된 암호문 형식 또는 키 불일치");
            System.err.println("입력값: " + encryptedText);
            throw e;
        }
    }
}
