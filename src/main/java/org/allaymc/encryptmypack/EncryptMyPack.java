package org.allaymc.encryptmypack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric;

/**
 * EncryptMyPack Project 2024/2/4
 *
 * @author daoge_cmd
 */
public class EncryptMyPack {

    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .setLenient()
            .create();

    public static final String USAGE = "Usage: java -jar EncryptMyPack.jar <encrypt|decrypt> <inputFolder> <outputFolder> [key]";

    public static final String DEFAULT_KEY = "liulihaocai123456789123456789123";

    public static final List<String> EXCLUDE = List.of(
            "manifest.json",
            "pack_icon.png",
            "bug_pack_icon.png"
    );

    public static final int KEY_LENGTH = 32;

    public static final byte[] VERSION = new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00};
    public static final byte[] MAGIC = new byte[]{(byte) 0xFC, (byte) 0xB9, (byte) 0xCF, (byte) 0x9B};

    public static void main(String[] args) {
        if (args.length < 3 || args.length > 4) {
            log(USAGE);
            return;
        }
        switch (args[0]) {
            case "encrypt" -> encrypt(Path.of(args[1]), Path.of(args[2]), args.length > 3 ? args[3] : DEFAULT_KEY);
            case "decrypt" -> {
                // To decrypt, the user must provide the pack's key
                if (args.length != 4) throw new IllegalArgumentException("key must be provided to decrypt");
                decrypt(Path.of(args[1]), Path.of(args[2]), args[3]);
            }
            default -> log(USAGE);
        }
    }

    @SneakyThrows
    public static void encrypt(Path inputFolder, Path outputFolder, String key) {
        checkArgs(inputFolder, outputFolder, key);

        // Find content id
        var contentId = findContentId(inputFolder.resolve("manifest.json"));
        log("ContentId: " + contentId);

        var contentEntries = new ArrayList<ContentEntry>();

        // Encrypt files
        findAllFiles(inputFolder).forEach(file -> {
            var outputPath = outputFolder.resolve(inputFolder.relativize(file));
            String entryKey = null;
            // Check if file is excluded
            if (EXCLUDE.contains(file.getFileName().toString())) {
                encryptExcludedFile(file, outputPath);
                // Excluded file does not have entry key
            } else {
                // Encrypt file
                entryKey = encryptFile(file, outputPath);
            }
            var packRelativePath = inputFolder.relativize(file);
            log("File: " + file + ", entryKey: " + entryKey + ", packRelativePath: " + packRelativePath + ", outputPath: " + outputPath);
            contentEntries.add(new ContentEntry(packRelativePath.toString().replaceAll("\\\\", "/"), entryKey));
        });

        // Generate contents.json
        var contentJsonPath = outputFolder.resolve("contents.json");
        // Remove the old one
        Files.deleteIfExists(contentJsonPath);
        try (var file = new RandomAccessFile(Files.createFile(contentJsonPath).toFile(), "rw")) {
            file.write(VERSION);
            file.write(MAGIC);
            file.seek(0x10);
            var contentIdBytes = contentId.getBytes(StandardCharsets.UTF_8);
            // Write content id length
            file.write(contentIdBytes.length);
            // Write content id
            file.write(contentIdBytes);
            // Init contents.json encryptor
            var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
            // Write contents.json
            var contentJson = GSON.toJson(new Content(contentEntries));
            file.seek(0x100);
            file.write(cipher.doFinal(contentJson.getBytes(StandardCharsets.UTF_8)));
            log("Successfully create contents.json");
        }
        log("Key: " + key);
    }

    @SneakyThrows
    public static void checkArgs(Path inputFolder, Path outputFolder, String key) {
        // Check argument
        if (key.length() != KEY_LENGTH) throw new IllegalArgumentException("key length must be 32");
        if (inputFolder.equals(outputFolder)) throw new IllegalArgumentException("input folder and output folder cannot be the same");
        if (!exists(inputFolder)) throw new IllegalArgumentException("input folder not exists");
        if (!exists(outputFolder)) Files.createDirectories(outputFolder);
    }

    @SneakyThrows
    public static void encryptExcludedFile(Path file, Path outputPath) {
        log("Excluded file: " + file + ", copy directly");
        copy(file, outputPath, REPLACE_EXISTING);
    }

    @SneakyThrows
    public static String encryptFile(Path file, Path outputPath) {
        byte[] bytes;
        bytes = readAllBytes(file);
        // Init encryptor
        var key = randomAlphanumeric(KEY_LENGTH);
        var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
        // Encrypt the file
        var encryptedBytes = cipher.doFinal(bytes);
        checkDirectories(outputPath);
        deleteIfExists(outputPath);
        // Write bytes
        write(outputPath, encryptedBytes);
        return key;
    }

    @SneakyThrows
    public static void checkDirectories(Path outputPath) {
        var dir = outputPath.getParent();
        if (!exists(dir)) Files.createDirectories(dir);
    }

    @SneakyThrows
    public static void decrypt(Path inputFolder, Path outputFolder, String key) {
        checkArgs(inputFolder, outputFolder, key);

        Content content;
        try (var file = new RandomAccessFile(inputFolder.resolve("contents.json").toFile(), "rw")) {
            file.seek(0x100);
            var buffer = new byte[(int) (file.length() - 0x100)];
            file.readFully(buffer);
            var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(key.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
            var decryptedBytes = cipher.doFinal(buffer);
            content = GSON.fromJson(new String(decryptedBytes), Content.class);
            log("Content: " + content);
        }

        // Decrypt files
        for (var contentEntry : content.content) {
            var entryInputPath = inputFolder.resolve(contentEntry.path);
            if (!Files.exists(entryInputPath)) {
                err("File not exists: " + entryInputPath);
                continue;
            }
            var entryOutputPath = outputFolder.resolve(contentEntry.path);
            checkDirectories(entryOutputPath);
            var entryKey = contentEntry.key;
            if (entryKey == null) {
                // manifest.json, pack_icon.png, bug_pack_icon.png etc...
                // Just copy it to output folder
                log("Copying file: " + entryInputPath);
                copy(entryInputPath, entryOutputPath, REPLACE_EXISTING);
            } else {
                log("Decrypting file: " + entryInputPath);
                var entryKeyBytes = entryKey.getBytes(StandardCharsets.UTF_8);
                if (entryKeyBytes.length != KEY_LENGTH) {
                    err("Invalid key length (length should be " + KEY_LENGTH + "): " + entryKey);
                    continue;
                }
                var secretKey = new SecretKeySpec(entryKeyBytes, "AES");
                var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, secretKey, new IvParameterSpec(entryKey.substring(0, 16).getBytes(StandardCharsets.UTF_8)));
                var decryptedBytes = cipher.doFinal(readAllBytes(entryInputPath));
                deleteIfExists(entryOutputPath);
                write(entryOutputPath, decryptedBytes);
            }
        }

        log("Decrypted " + inputFolder + " with key " + key + " successfully");
    }

    @SneakyThrows
    public static List<Path> findAllFiles(Path folder) {
        if (!isDirectory(folder)) return Collections.emptyList();
        var files = new ArrayList<Path>();
        try (var stream = Files.list(folder)) {
            stream.forEach(file -> {
                if (isDirectory(file)) {
                    files.addAll(findAllFiles(file));
                } else {
                    files.add(file);
                }
            });
        }
        return files;
    }

    @SneakyThrows
    public static String findContentId(Path manifestFilePath) {
        if (!exists(manifestFilePath)) throw new IllegalArgumentException("manifest file not exists");
        Manifest manifest = GSON.fromJson(new JsonReader(Files.newBufferedReader(manifestFilePath)), Manifest.class);
        return manifest.header.uuid;
    }

    public static void log(String msg) {
        System.out.println(msg);
    }

    public static void err(String msg) {
        System.err.println(msg);
    }

    public record Content(List<ContentEntry> content) {}

    public record ContentEntry(String path, String key) {}

    public static class Manifest {

        public Header header;

        public static class Header {
            private String uuid;
        }
    }
}