package org.allaymc.encryptmypack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomStringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.nio.file.Files.*;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;
import static org.apache.commons.io.FilenameUtils.getExtension;

/**
 * EncryptMyPack Project 2024/2/4
 *
 * @author daoge_cmd
 */
public class EncryptMyPack {

    public static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()// 禁止将部分特殊字符转义为unicode编码
            .create();

    public static final String DEFAULT_KEY = "liulihaocai123456789123456789123";

    public static final List<String> EXCLUDE = List.of(
            "manifest.json",
            "pack_icon.png",
            "bug_pack_icon.png"
    );

    public static final int KEY_LENGTH = 32;

    public static final ByteBuffer VERSION = ByteBuffer.wrap(new byte[]{(byte) 0x00, (byte) 0x00, (byte) 0x00, (byte) 0x00});
    public static final ByteBuffer MAGIC = ByteBuffer.wrap(new byte[]{(byte) 0xFC, (byte) 0xB9, (byte) 0xCF, (byte) 0x9B});

    public static void main(String[] args) {
        encrypt(Path.of("test_pack"), Path.of("test_pack_encrypted"), DEFAULT_KEY);
    }

    @SneakyThrows
    public static void encrypt(Path inputFolder, Path outputFolder, String key) {
        // Check argument
        if (key.length() != KEY_LENGTH) throw new IllegalArgumentException("key length must be 32");
        if (!exists(inputFolder)) throw new IllegalArgumentException("input folder not exists");
        if (!exists(outputFolder)) Files.createDirectories(outputFolder);

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
            contentEntries.add(new ContentEntry(packRelativePath.toString(), entryKey));
        });

        // Generate contents.json
        var contentJsonPath = outputFolder.resolve("contents.json");
        // Remove the old one
        Files.deleteIfExists(contentJsonPath);
        try (var channel = Files.newByteChannel(Files.createFile(contentJsonPath), WRITE)) {
            channel.write(VERSION);
            channel.write(MAGIC);
            channel.position(0x10);
            var contentIdBytes = contentId.getBytes();
            // Write content id length
            channel.write(ByteBuffer.wrap(new byte[]{(byte) contentIdBytes.length}));
            // Write content id
            channel.write(ByteBuffer.wrap(contentIdBytes));
            // Init contents.json encryptor
            var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            // Write contents.json
            channel.position(0x100);
            var contentJson = GSON.toJson(new Content(contentEntries));
            channel.write(ByteBuffer.wrap(cipher.doFinal(contentJson.getBytes(StandardCharsets.UTF_8))));
            log("Successfully create contents.json");
        }
    }

    @SneakyThrows
    public static void encryptExcludedFile(Path file, Path outputPath) {
        log("Excluded file: " + file + ", copy directly");
        if (isJson(file)) {
            deleteIfExists(outputPath);
            // Json file should be shrunk
            writeString(outputPath, shrinkJson(file));
        } else {
            // Copy file to output path directly
            copy(file, outputPath, REPLACE_EXISTING);
        }
    }

    @SneakyThrows
    public static String encryptFile(Path file, Path outputPath) {
        byte[] bytes;
        if (isJson(file)) {
            bytes = shrinkJson(file).getBytes();
        } else {
            bytes = readAllBytes(file);
        }
        // Init encryptor
        var key = RandomStringUtils.randomAlphabetic(KEY_LENGTH);
        var secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        var cipher = Cipher.getInstance("AES/CFB8/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        // Encrypt the file
        var encryptedBytes = cipher.doFinal(bytes);
        // Check directories
        var dir = outputPath.getParent();
        if (!exists(dir)) Files.createDirectories(dir);
        // Delete old file
        deleteIfExists(outputPath);
        // Write bytes
        createFile(outputPath);
        write(outputPath, encryptedBytes);
        return key;
    }

    public static boolean isJson(Path file) {
        return getExtension(file.toString()).equals("json");
    }

    @SneakyThrows
    public static String shrinkJson(Path file) {
        return GSON.toJson(GSON.fromJson(Files.newBufferedReader(file), Object.class));
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

    public record Content(List<ContentEntry> entries) {}

    public record ContentEntry(String packRelativePath, String key) {}

    public static class Manifest {

        public Header header;

        public static class Header {
            private String uuid;
        }
    }
}