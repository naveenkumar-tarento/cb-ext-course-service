package com.igot.cb.util;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.MessageDigest;
import java.security.spec.KeySpec;
import java.util.Locale;

@Component
public class SecretKeySpec implements KeySpec, SecretKey {
    private static final long serialVersionUID = 6577238317307289933L;
    private static final String TRIPLE_DES = "TripleDES";
    private byte[] key;
    private String algorithm;

    @PostConstruct
    public void postConstruct() {
        this.key = Constants.CIPHER_KEY.clone();
        this.algorithm = Constants.CIPHER_ALGORITHM;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getFormat() {
        return "RAW";
    }

    public byte[] getEncoded() {
        return this.key.clone();
    }

    public int hashCode() {
        int retval = 0;

        for (int i = 1; i < this.key.length; ++i) {
            retval += this.key[i] * i;
        }

        return this.algorithm.equalsIgnoreCase(TRIPLE_DES) ? retval ^ "desede".hashCode()
                : retval ^ this.algorithm.toLowerCase(Locale.ENGLISH).hashCode();
    }

    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof SecretKey secretKey)) {
            return false;
        } else {
            String thatAlg = secretKey.getAlgorithm();
            if (thatAlg.equalsIgnoreCase(this.algorithm)
                    || thatAlg.equalsIgnoreCase("DESede") && this.algorithm.equalsIgnoreCase(TRIPLE_DES)
                    || thatAlg.equalsIgnoreCase(TRIPLE_DES) && this.algorithm.equalsIgnoreCase("DESede")) {
                byte[] thatKey = secretKey.getEncoded();
                return MessageDigest.isEqual(this.key, thatKey);
            } else {
                return false;
            }
        }
    }
}
