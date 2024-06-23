package org.allaymc.encryptmypack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import lombok.SneakyThrows;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static java.nio.file.Files.exists;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

/**
 * EncryptMyPack Project 2024/2/4
 *
 * @author daoge_cmd
 */
public class EncryptMyPack {

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().setLenient().create();
    public static final String USAGE = "Usage: EncryptMyPack.exe <encrypt|decrypt> <inputZip> <outputZip> [key]";
    public static final String DEFAULT_KEY = "liulihaocai123456789123456789123";
    public static final List<String> EXCLUDE = List.of("manifest.json", "pack_icon.png", "bug_pack_icon.png");
    public static final int KEY_LENGTH = 32;
    public static final byte[] VERSION = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    public static final byte[] MAGIC = new byte[]{(byte) 0xFC, (byte) 0xB9, (byte) 0xCF, (byte) 0x9B};

    @SneakyThrows
    public static void main(String[] args) {
        if (args.length < 3 || args.length > 4) {
            log(USAGE);
            return;
        }
        var inputPath = Path.of(args[1]);
        var outputName = args[2];
        switch (args[0]) {
            case "encrypt" -> {
                var key = args.length > 3 ? args[3] : DEFAULT_KEY;
                checkArgs(inputPath, outputName, key);
                try (var inputZip = new ZipFile(inputPath.toString())) {
                    encrypt(inputZip, outputName, key);
                }
            }
            case "decrypt" -> {
                // To decrypt, the user must provide the pack's key
                if (args.length != 4) throw new IllegalArgumentException("key must be provided to decrypt");
                var key = args[3];
                checkArgs(inputPath, outputName, key);
                try (var inputZip = new ZipFile(inputPath.toString())) {
                    decrypt(inputZip, outputName, key);
                }
            }
            default -> log(USAGE);
        }
    }

    @SneakyThrows
    public static void encrypt(ZipFile inputZip, String outputName, String key) {
        // Find content id
        var contentId = findContentId(inputZip);
        log("ContentId: " + contentId);

        var contentEntries = new ArrayList<org.allaymc.encryptmypack.EncryptMyPack.ContentEntry>();

        // Delete old output
        Files.deleteIfExists(Path.of(outputName));
        var outputStream = new ZipOutputStream(new FileOutputStream(outputName), StandardCharsets.UTF_8);
        // Encrypt files
        inputZip.stream().forEach(zipEntry -> {
            if (zipEntry.isDirectory()) {
                createDirectoryRoot(zipEntry, outputStream);
                if (isSubPackRoot(zipEntry)) {
                    // Handle sub pack
                    encryptSubPack(inputZip, outputStream, zipEntry.getName(), key, contentId);
                }
                return;
            }
            // Sub pack files will be handled in encryptSubPack()
            if (isSubPackFile(zipEntry)) return;
            String entryKey = null;
            // Check if file is excluded
            if (EXCLUDE.contains(zipEntry.getName())) {
                encryptExcludedFile(inputZip, outputStream, zipEntry);
                // Excluded file does not have entry key
            } else {
                // Encrypt file
                entryKey = encryptFile(inputZip, outputStream, zipEntry);
            }
            log("File: " + zipEntry.getName() + ", entryKey: " + entryKey);
            contentEntries.add(new org.allaymc.encryptmypack.EncryptMyPack.ContentEntry(zipEntry.getName(), entryKey));
        });

        generateContentsJson("contents.json", outputStream, contentId, key, contentEntries);
        outputStream.close();
        log("Encryption finish. Key: " + key);
    }

    @SneakyThrows
    public static void createDirectoryRoot(ZipEntry zipEntry, ZipOutputStream outputStream) {
        outputStream.putNextEntry((ZipEntry) zipEntry.clone());
        outputStream.closeEntry();
    }

    @SneakyThrows
    public static void encryptSubPack(ZipFile inputZip, ZipOutputStream zos, String subPackPath, String key, String contentId) {
        log("Encrypting sub pack: " + subPackPath);
        var subPackContentEntries = new ArrayList<org.allaymc.encryptmypack.EncryptMyPack.ContentEntry>();

        // Encrypt files
        inputZip.stream().forEach(zipEntry -> {
            if (zipEntry.isDirectory()) return;
            if (!zipEntry.getName().startsWith(subPackPath)) return;
            String entryKey = encryptFile(inputZip, zos, zipEntry);
            log("Sub pack file: " + zipEntry.getName() + ", entryKey: " + entryKey);
            subPackContentEntries.add(new org.allaymc.encryptmypack.EncryptMyPack.ContentEntry(zipEntry.getName().substring(subPackPath.length()), entryKey));
        });

        generateContentsJson(subPackPath + "contents.json", zos, contentId, key, subPackContentEntries);
    }

    public static void generateContentsJson(String name, ZipOutputStream outputStream, String contentId, String key, ArrayList<org.allaymc.encryptmypack.EncryptMyPack.ContentEntry> contentEntries) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        outputStream.putNextEntry(new ZipEntry(name));
        try (var stream = new ByteArrayOutputStream()) {
            stream.write(VERSION);
            stream.write(MAGIC);
            paddingTo(stream, 0x10);
            var contentIdBytes = contentId.getBytes(StandardCharsets.UTF_8);
            // Write content id length
            stream.write(contentIdBytes.length);
            // Write content id
            stream.write(contentIdBytes);
            // Init contents.json encryptor
            var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
            // Write contents.json
            var contentJson = GSON.toJson(new org.allaymc.encryptmypack.EncryptMyPack.Content(contentEntries));
            paddingTo(stream, 0x100);
            stream.write(cipher.doFinal(contentJson.getBytes(StandardCharsets.UTF_8)));
            outputStream.write(stream.toByteArray());
        }
        outputStream.closeEntry();
        log("Successfully create contents.json");
    }

    @SneakyThrows
    public static void encryptExcludedFile(ZipFile inputZip, ZipOutputStream outputStream, ZipEntry zipEntry) {
        log("Excluded file: " + zipEntry.getName() + ", copy directly");
        outputStream.putNextEntry((ZipEntry) zipEntry.clone());
        outputStream.write(inputZip.getInputStream(zipEntry).readAllBytes());
        outputStream.closeEntry();
    }

    @SneakyThrows
    public static String encryptFile(ZipFile inputZip, ZipOutputStream outputStream, ZipEntry zipEntry) {
        byte[] bytes = inputZip.getInputStream(zipEntry).readAllBytes();
        // Init encryptor
        var key = randomAlphanumeric(KEY_LENGTH);
        var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
        // Encrypt the file
        var encryptedBytes = cipher.doFinal(bytes);
        // Write bytes
        outputStream.putNextEntry(new ZipEntry(zipEntry.getName()));
        outputStream.write(encryptedBytes);
        outputStream.closeEntry();  // Закрываем entry после записи данных
        return key;
    }


    @SneakyThrows
    public static void decrypt(ZipFile inputZip, String outputName, String key) {
        org.allaymc.encryptmypack.EncryptMyPack.Content content = decryptContentsJson(inputZip, "contents.json", key);

        // Delete old output
        Files.deleteIfExists(Path.of(outputName));
        var outputStream = new ZipOutputStream(new FileOutputStream(outputName));
        // Decrypt files
        for (var contentEntry : content.content()) {
            var entryPath = contentEntry.path();
            var zipEntry = inputZip.getEntry(entryPath);
            if (zipEntry == null) {
                err("Zip entry not exists: " + entryPath);
                continue;
            }
            outputStream.putNextEntry((ZipEntry) zipEntry.clone());
            var bytes = inputZip.getInputStream(zipEntry).readAllBytes();
            if (contentEntry.key() == null) {
                // manifest.json, pack_icon.png, bug_pack_icon.png etc...
                // Just copy it to output folder
                log("Copying file: " + entryPath);
                outputStream.write(bytes);
            } else {
                log("Decrypting file: " + entryPath);
                decryptFile(outputStream, bytes, contentEntry.key());
            }
            outputStream.closeEntry();
        }

        // Handle sub packs (if exist)
        inputZip.stream().filter(org.allaymc.encryptmypack.EncryptMyPack::isSubPackRoot).forEach(zipEntry -> decryptSubPack(inputZip, outputStream, zipEntry.getName(), key));

        outputStream.close();
        log("Decrypted " + inputZip.getName() + " with key " + key + " successfully");
    }

    @SneakyThrows
    public static void decryptSubPack(ZipFile inputZip, ZipOutputStream zos, String subPackPath, String key) {
        log("Decrypting sub pack: " + subPackPath);
        org.allaymc.encryptmypack.EncryptMyPack.Content content = decryptContentsJson(inputZip, subPackPath + "contents.json", key);

        for (var contentEntry : content.content()) {
            var entryPath = subPackPath + contentEntry.path();
            var zipEntry = inputZip.getEntry(entryPath);
            if (zipEntry == null) {
                err("Zip entry not exists: " + entryPath);
                continue;
            }
            zos.putNextEntry((ZipEntry) zipEntry.clone());
            var bytes = inputZip.getInputStream(zipEntry).readAllBytes();
            log("Decrypting sub pack file: " + entryPath);
            decryptFile(zos, bytes, contentEntry.key());
            zos.closeEntry();
        }
    }

    @SneakyThrows
    public static void decryptFile(ZipOutputStream zos, byte[] bytes, String entryKey) {
        var entryKeyBytes = entryKey.getBytes(StandardCharsets.UTF_8);
        if (entryKeyBytes.length != KEY_LENGTH) {
            err("Invalid key length (length should be " + KEY_LENGTH + "): " + entryKey);
            return;
        }
        var secretKey = new SecretKeySpec(entryKeyBytes, "AES");
        var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(entryKey.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
        var decryptedBytes = cipher.doFinal(bytes);
        zos.write(decryptedBytes);
    }

    @SneakyThrows
    private static org.allaymc.encryptmypack.EncryptMyPack.Content decryptContentsJson(ZipFile inputZip, String subPackPath, String key) {
        try (var stream = inputZip.getInputStream(inputZip.getEntry(subPackPath))) {
            stream.skip(0x100);
            var bytes = stream.readAllBytes();
            var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
            var decryptedBytes = cipher.doFinal(bytes);
            org.allaymc.encryptmypack.EncryptMyPack.Content content = GSON.fromJson(new String(decryptedBytes), org.allaymc.encryptmypack.EncryptMyPack.Content.class);
            log("Decrypted content json: " + content);
            return content;
        }
    }

    public static boolean isSubPackFile(ZipEntry zipEntry) {
        return zipEntry.getName().startsWith("subpacks/");
    }

    public static boolean isSubPackRoot(ZipEntry zipEntry) {
        return zipEntry.isDirectory() && zipEntry.getName().startsWith("subpacks/") && calCharCount(zipEntry.getName(), '/') == 2;
    }

    @SneakyThrows
    public static void checkArgs(Path inputPath, String outputName, String key) {
        // Check argument
        if (key.length() != KEY_LENGTH) throw new IllegalArgumentException("key length must be 32");
        if (!exists(inputPath)) throw new IllegalArgumentException("input zip not exists");
        if (inputPath.getFileName().toString().equals(outputName))
            throw new IllegalArgumentException("input and output cannot be the same");
    }

    public static void paddingTo(ByteArrayOutputStream stream, int pos) {
        if (pos <= stream.size()) throw new IllegalArgumentException("pos must be bigger than stream size");
        var need = pos - stream.size();
        for (int i = 0; i < need; i++) {
            stream.write(0);
        }
    }

    @SneakyThrows
    public static String findContentId(ZipFile zip) {
        var manifestEntry = zip.getEntry("manifest.json");
        if (manifestEntry == null) throw new IllegalArgumentException("manifest file not exists");
        org.allaymc.encryptmypack.EncryptMyPack.Manifest manifest = GSON.fromJson(new JsonReader(new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8)), org.allaymc.encryptmypack.EncryptMyPack.Manifest.class);
        return manifest.header.uuid;
    }

    public static int calCharCount(String str, char target) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == target) count++;
        }
        return count;
    }

    public static void log(String msg) {
        System.out.println(msg);
    }

    public static void err(String msg) {
        System.err.println(msg);
    }

    public record Content(List<org.allaymc.encryptmypack.EncryptMyPack.ContentEntry> content) {
    }

    public record ContentEntry(String path, String key) {
    }

    public static class Manifest {

        public org.allaymc.encryptmypack.EncryptMyPack.Manifest.Header header;

        public static class Header {
            private String uuid;
        }
    }
}
