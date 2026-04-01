package com.codes.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Encodes/decodes shared files as a single message string (no pipe characters).
 * Wire/storage format: {@code FILE:}<base64(DataOutput: utf filename, utf mime, int len, bytes)>
 */
public final class FileMessageCodec {
    public static final String PREFIX = "FILE:";
    /** Maximum file payload size (2 MiB) — keeps single-line TCP messages practical. */
    public static final int MAX_DECODED_BYTES = 2 * 1024 * 1024;

    private FileMessageCodec() {}

    public static String encode(String filename, String mimeType, byte[] data) throws IOException {
        if (filename == null) filename = "download";
        if (mimeType == null || mimeType.isBlank()) mimeType = "application/octet-stream";
        if (data == null) data = new byte[0];
        if (data.length > MAX_DECODED_BYTES) {
            throw new IOException("File too large (max " + MAX_DECODED_BYTES + " bytes)");
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        dos.writeUTF(filename);
        dos.writeUTF(mimeType);
        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
        return PREFIX + Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    public static Parsed decode(String content) throws IOException {
        if (content == null || !content.startsWith(PREFIX)) {
            throw new IllegalArgumentException("Not a file message");
        }
        String b64 = content.substring(PREFIX.length());
        byte[] raw = Base64.getDecoder().decode(b64);
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(raw));
        String filename = dis.readUTF();
        String mime = dis.readUTF();
        int len = dis.readInt();
        if (len < 0 || len > MAX_DECODED_BYTES) {
            throw new IOException("Invalid file length");
        }
        byte[] fileData = dis.readNBytes(len);
        if (fileData.length != len) throw new IOException("Truncated file payload");
        return new Parsed(filename, mime, fileData);
    }

    public static boolean isFileMessage(String content) {
        return content != null && content.startsWith(PREFIX);
    }

    public record Parsed(String filename, String mimeType, byte[] data) {}
}
