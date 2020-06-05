package fr.pchab.androidrtc;

import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtil {

    public static byte[] getEncryptionKey(String key) {
        byte[] hashKey = null;
        byte[] encryptionKey = null;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            hashKey = digest.digest(key.getBytes("UTF-8"));
            encryptionKey = Arrays.copyOf(hashKey, 16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return encryptionKey;
    }

    public static byte[] encryptAes128(byte[] inputContent, String key) {

        byte[] encryptionKey = getEncryptionKey(key);
        SecretKeySpec secretKeySpec = new SecretKeySpec(encryptionKey, "AES");
        byte[] encryptedContent = null;
        IvParameterSpec ivParameterSpec = new IvParameterSpec(encryptionKey);
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec);
            encryptedContent = cipher.doFinal(inputContent);

        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return encryptedContent;
    }

    public static byte[] decryptAes128(byte[] encryptedInputContent, String key) {

        byte[] decryptionKey = getEncryptionKey(key);
        SecretKeySpec secretKeySpec = new SecretKeySpec(decryptionKey, "AES");
        IvParameterSpec ivParameterSpec = new IvParameterSpec(decryptionKey);
        byte[] decryptedContent = null;
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec);
            decryptedContent = cipher.doFinal(encryptedInputContent);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (InvalidAlgorithmParameterException e) {
            e.printStackTrace();
        } catch (InvalidKeyException e) {
            e.printStackTrace();
        } catch (BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        }
        return decryptedContent;
    }
}
