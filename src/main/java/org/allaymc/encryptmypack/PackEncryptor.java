package org.allaymc.encryptmypack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

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


/**
 * @author daoge_cmd
 */
@Slf4j
public final class PackEncryptor {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setLenient()
            .create();
    private static final int KEY_LENGTH = 32;
    private static final byte[] VERSION = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    private static final byte[] MAGIC = new byte[]{(byte) 0xFC, (byte) 0xB9, (byte) 0xCF, (byte) 0x9B};
    private static final List<String> EXCLUDED_FILES = List.of("manifest.json", "pack_icon.png", "bug_pack_icon.png");

    public static String generateRandomKey() {
        return RandomStringUtils.randomAlphanumeric(KEY_LENGTH);
    }

    public static void encrypt(Path inputPath, Path outputPath, String key) {
        if (!checkArgs(inputPath, outputPath, key)) {
            return;
        }

        try (var inputZip = new ZipFile(inputPath.toString())) {
            encrypt0(inputZip, outputPath, key);
        } catch (Exception e) {
            log.error("Failed to encrypt pack", e);
        }
    }

    public static void decrypt(Path inputPath, Path outputPath, String key) {
        if (!checkArgs(inputPath, outputPath, key)) {
            return;
        }

        try (var inputZip = new ZipFile(inputPath.toString())) {
            decrypt0(inputZip, outputPath, key);
        } catch (Exception e) {
            log.error("Failed to decrypt pack", e);
        }
    }

    @SneakyThrows
    private static void encrypt0(ZipFile inputZip, Path outputPath, String key) {
        // Find content id
        var uuid = findPackUUID(inputZip);
        log.info("ContentId: {}", uuid);

        var contentEntries = new ArrayList<ContentEntry>();

        // Delete old output
        Files.deleteIfExists(outputPath);
        var outputStream = new ZipOutputStream(new FileOutputStream(outputPath.toFile()), StandardCharsets.UTF_8);
        // Encrypt files
        inputZip.stream().forEach(zipEntry -> {
            if (zipEntry.isDirectory()) {
                createDirectoryRoot(zipEntry, outputStream);
                if (isSubPackRoot(zipEntry)) {
                    // Handle sub pack
                    encryptSubPack(inputZip, outputStream, zipEntry.getName(), key, uuid);
                }

                return;
            }
            // Sub pack files will be handled in encryptSubPack()
            if (isSubPackFile(zipEntry)) {
                return;
            }

            String entryKey = null;
            // Check if file is excluded
            if (EXCLUDED_FILES.contains(zipEntry.getName())) {
                encryptExcludedFile(inputZip, outputStream, zipEntry);
                // Excluded file does not have entry key
            } else {
                // Encrypt file
                entryKey = encryptFile(inputZip, outputStream, zipEntry);
            }
            log.info("File: {}, entryKey: {}", zipEntry.getName(), entryKey);
            contentEntries.add(new ContentEntry(zipEntry.getName(), entryKey));
        });

        generateContentsJson("contents.json", outputStream, uuid, key, contentEntries);
        outputStream.close();
        log.info("Encryption finish. Key: {}. Output file: {}", key, outputPath);
    }

    @SneakyThrows
    private static void createDirectoryRoot(ZipEntry zipEntry, ZipOutputStream outputStream) {
        outputStream.putNextEntry(copyZipEntry(zipEntry));
        outputStream.closeEntry();
    }

    @SneakyThrows
    private static void encryptSubPack(ZipFile inputZip, ZipOutputStream zos, String subPackPath, String key, String contentId) {
        log.info("Encrypting sub pack: {}", subPackPath);
        var subPackContentEntries = new ArrayList<ContentEntry>();

        // Encrypt files
        inputZip.stream().forEach(zipEntry -> {
            if (zipEntry.isDirectory() || !zipEntry.getName().startsWith(subPackPath)) {
                return;
            }

            String entryKey = encryptFile(inputZip, zos, zipEntry);
            log.info("Sub pack file: {}, entryKey: {}", zipEntry.getName(), entryKey);
            subPackContentEntries.add(new ContentEntry(zipEntry.getName().substring(subPackPath.length()), entryKey));
        });

        generateContentsJson(subPackPath + "contents.json", zos, contentId, key, subPackContentEntries);
    }

    private static void generateContentsJson(String name, ZipOutputStream outputStream, String contentId, String key, ArrayList<ContentEntry> contentEntries) throws IOException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
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
            var contentJson = GSON.toJson(new Content(contentEntries));
            paddingTo(stream, 0x100);
            stream.write(cipher.doFinal(contentJson.getBytes(StandardCharsets.UTF_8)));
            outputStream.write(stream.toByteArray());
        }
        outputStream.closeEntry();
        log.info("Successfully create contents.json");
    }

    @SneakyThrows
    private static void encryptExcludedFile(ZipFile inputZip, ZipOutputStream outputStream, ZipEntry zipEntry) {
        log.info("Excluded file: {}, copy directly", zipEntry.getName());
        outputStream.putNextEntry(copyZipEntry(zipEntry));
        outputStream.write(inputZip.getInputStream(zipEntry).readAllBytes());
        outputStream.closeEntry();
    }

    @SneakyThrows
    private static String encryptFile(ZipFile inputZip, ZipOutputStream outputStream, ZipEntry zipEntry) {
        byte[] bytes;
        bytes = inputZip.getInputStream(zipEntry).readAllBytes();
        // Init encryptor
        var key = RandomStringUtils.randomAlphanumeric(KEY_LENGTH);
        var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
        // Encrypt the file
        var encryptedBytes = cipher.doFinal(bytes);
        // Write bytes
        outputStream.putNextEntry(copyZipEntry(zipEntry));
        outputStream.write(encryptedBytes);
        outputStream.closeEntry();
        return key;
    }

    @SneakyThrows
    private static void decrypt0(ZipFile inputZip, Path outputPath, String key) {
        Content content = decryptContentsJson(inputZip, "contents.json", key);

        // Delete old output
        Files.deleteIfExists(outputPath);
        var outputStream = new ZipOutputStream(new FileOutputStream(outputPath.toFile()));
        // Decrypt files
        for (var contentEntry : content.content) {
            if (contentEntry.key == null) {
                continue;
            }

            var entryPath = contentEntry.path;
            var zipEntry = inputZip.getEntry(entryPath);
            if (zipEntry == null) {
                log.error("Zip entry not exists: {}", entryPath);
                continue;
            }

            log.info("Decrypting file: {}", entryPath);
            outputStream.putNextEntry(copyZipEntry(zipEntry));
            decryptFile(outputStream, inputZip.getInputStream(zipEntry).readAllBytes(), contentEntry.key);
            outputStream.closeEntry();
        }
        // Copy excluded files
        for (var excluded : EXCLUDED_FILES) {
            // manifest.json, pack_icon.png, bug_pack_icon.png etc...
            // Just copy it to output folder as they are not encrypted
            var zipEntry = inputZip.getEntry(excluded);
            if (zipEntry == null) continue;

            log.info("Copying file: {}", excluded);
            outputStream.putNextEntry(copyZipEntry(zipEntry));
            outputStream.write(inputZip.getInputStream(zipEntry).readAllBytes());
            outputStream.closeEntry();
        }

        // Handle sub packs (if exist)
        inputZip.stream().filter(PackEncryptor::isSubPackRoot).forEach(zipEntry -> decryptSubPack(inputZip, outputStream, zipEntry.getName(), key));

        outputStream.close();
        log.info("Decrypted file {} with key {} successfully. Output file: {}", inputZip.getName(), key, outputPath);
    }

    @SneakyThrows
    private static void decryptSubPack(ZipFile inputZip, ZipOutputStream zos, String subPackPath, String key) {
        log.info("Decrypting sub pack: {}", subPackPath);
        Content content = decryptContentsJson(inputZip, subPackPath + "contents.json", key);

        for (var contentEntry : content.content) {
            var entryPath = subPackPath + contentEntry.path;
            var zipEntry = inputZip.getEntry(entryPath);
            if (zipEntry == null) {
                log.error("Zip entry not exists: {}", entryPath);
                continue;
            }
            zos.putNextEntry(copyZipEntry(zipEntry));
            var bytes = inputZip.getInputStream(zipEntry).readAllBytes();
            log.info("Decrypting sub pack file: {}", entryPath);
            decryptFile(zos, bytes, contentEntry.key);
            zos.closeEntry();
        }
    }

    @SneakyThrows
    private static void decryptFile(ZipOutputStream zos, byte[] bytes, String entryKey) {
        var entryKeyBytes = entryKey.getBytes(StandardCharsets.UTF_8);
        if (entryKeyBytes.length != KEY_LENGTH) {
            log.error("Invalid key length (length should be {}): {}", KEY_LENGTH, entryKey);
            return;
        }
        var secretKey = new SecretKeySpec(entryKeyBytes, "AES");
        var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(entryKey.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
        var decryptedBytes = cipher.doFinal(bytes);
        zos.write(decryptedBytes);
    }

    @SneakyThrows
    private static Content decryptContentsJson(ZipFile inputZip, String subPackPath, String key) {
        var entry = inputZip.getEntry(subPackPath);
        if (entry == null) {
            log.error("Cannot find {}, it seems that this file is not encrypted", subPackPath);
            throw new IllegalArgumentException();
        }

        try (var stream = inputZip.getInputStream(entry)) {
            stream.skip(0x100);
            var bytes = stream.readAllBytes();
            var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
            var decryptedBytes = cipher.doFinal(bytes);
            Content content = GSON.fromJson(new String(decryptedBytes), Content.class);
            log.info("Decrypted content json: {}", content);
            return content;
        }
    }

    private static boolean isSubPackFile(ZipEntry zipEntry) {
        return zipEntry.getName().startsWith("subpacks/");
    }

    private static boolean isSubPackRoot(ZipEntry zipEntry) {
        return zipEntry.isDirectory() &&
               zipEntry.getName().startsWith("subpacks/") &&
               calculateCharCount(zipEntry.getName(), '/') == 2;
    }

    private static boolean checkArgs(Path inputPath, Path outputPath, String key) {
        if (key.length() != KEY_LENGTH) {
            log.error("key length must be 32");
            return false;
        }

        if (!Files.isRegularFile(inputPath)) {
            log.error("Input file is not exists");
            return false;
        }

        if (inputPath.equals(outputPath)) {
            log.error("input and output file cannot be the same");
            return false;
        }

        return true;
    }

    private static void paddingTo(ByteArrayOutputStream stream, int pos) {
        if (pos <= stream.size()) {
            throw new IllegalArgumentException("pos must be bigger than stream size");
        }

        var need = pos - stream.size();
        for (int i = 0; i < need; i++) {
            stream.write(0);
        }
    }

    @SneakyThrows
    private static String findPackUUID(ZipFile zip) {
        var manifestEntry = zip.getEntry("manifest.json");
        if (manifestEntry == null) {
            throw new IllegalArgumentException("manifest file not exists");
        }

        Manifest manifest = GSON.fromJson(new JsonReader(new InputStreamReader(zip.getInputStream(manifestEntry), StandardCharsets.UTF_8)), Manifest.class);
        return manifest.header.uuid;
    }

    private static int calculateCharCount(String str, char target) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == target) {
                count++;
            }
        }

        return count;
    }

    private static ZipEntry copyZipEntry(ZipEntry entry) {
        var newEntry = new ZipEntry(entry);
        // Explicitly set method to DEFLATED to avoid invalid crc-32 error
        newEntry.setMethod(ZipEntry.DEFLATED);
        return newEntry;
    }

    protected record Content(List<ContentEntry> content) {}

    protected record ContentEntry(String path, String key) {}

    protected static class Manifest {

        protected Header header;

        protected static class Header {
            private String uuid;
        }
    }
}