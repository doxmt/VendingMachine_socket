package util;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;


public class EnvUtil {
    private static final Properties props = new Properties();


    static {
        try {
            props.load(new FileInputStream(".env"));
        } catch (IOException e) {
            System.err.println("[EnvUtil] .env 파일 로드 실패: " + e.getMessage());
        }
    }

    public static String get(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            value = System.getProperty(key);  // 시스템 프로퍼티 fallback
        }
        return value;
    }
}
