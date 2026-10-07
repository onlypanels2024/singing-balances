package com.onlypanels.singingbalances;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Sends one email (with an optional PDF) through the Gmail API, as the signed-in user. */
final class GmailSender {
    static final class AuthExpired extends IOException {
        AuthExpired() {
            super("Google sign-in expired");
        }
    }

    private GmailSender() {}

    /** Encodes a header like a subject so accents, € and dashes survive. */
    static String header(String s) {
        for (char ch : s.toCharArray()) {
            if (ch > 126 || ch < 32) {
                return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8)) + "?=";
            }
        }
        return s;
    }

    private static String wrap76(String b64) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b64.length(); i += 76) sb.append(b64, i, Math.min(b64.length(), i + 76)).append("\r\n");
        return sb.toString();
    }

    /** Builds the raw email (MIME). Public for testing. */
    static String mime(String to, String subject, String body, String html, String fileName, byte[] pdf) {
        String boundary = "outro_" + Long.toHexString(System.nanoTime());
        String alt = boundary + "_alt";
        StringBuilder m = new StringBuilder();
        m.append("To: ").append(to).append("\r\n");
        m.append("Subject: ").append(header(subject)).append("\r\n");
        m.append("MIME-Version: 1.0\r\n");
        m.append("Content-Type: multipart/mixed; boundary=\"").append(boundary).append("\"\r\n\r\n");
        m.append("--").append(boundary).append("\r\n");
        if (html != null) {
            // Plain text and the same message as a web page (with the "Pay here" button); mail apps show the best one.
            m.append("Content-Type: multipart/alternative; boundary=\"").append(alt).append("\"\r\n\r\n");
            m.append("--").append(alt).append("\r\n");
        }
        m.append("Content-Type: text/plain; charset=UTF-8\r\n");
        m.append("Content-Transfer-Encoding: base64\r\n\r\n");
        m.append(wrap76(Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8))));
        if (html != null) {
            m.append("--").append(alt).append("\r\n");
            m.append("Content-Type: text/html; charset=UTF-8\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n\r\n");
            m.append(wrap76(Base64.getEncoder().encodeToString(html.getBytes(StandardCharsets.UTF_8))));
            m.append("--").append(alt).append("--\r\n");
        }
        if (pdf != null) {
            m.append("--").append(boundary).append("\r\n");
            m.append("Content-Type: application/pdf; name=\"").append(fileName).append("\"\r\n");
            m.append("Content-Disposition: attachment; filename=\"").append(fileName).append("\"\r\n");
            m.append("Content-Transfer-Encoding: base64\r\n\r\n");
            m.append(wrap76(Base64.getEncoder().encodeToString(pdf)));
        }
        m.append("--").append(boundary).append("--\r\n");
        return m.toString();
    }

    static byte[] read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) > 0) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    /** Sends the email. Runs on a background thread. */
    static void send(String accessToken, String to, String subject, String body, String html, File pdf) throws IOException {
        String raw = mime(to, subject, body, html, pdf == null ? null : pdf.getName(), pdf == null ? null : read(pdf));
        String json = "{\"raw\":\"" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8)) + "\"}";
        HttpURLConnection c = (HttpURLConnection) new URL("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
                .openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream out = c.getOutputStream()) {
            out.write(json.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        if (code == 401) throw new AuthExpired();
        if (code / 100 != 2) {
            String err = "";
            try (InputStream es = c.getErrorStream()) {
                if (es != null) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] b = new byte[4096];
                    int n;
                    while ((n = es.read(b)) > 0) bo.write(b, 0, n);
                    err = bo.toString("UTF-8");
                }
            } catch (Exception ignored) {
            }
            String msg = err.contains("\"message\"") ? err.replaceAll("(?s).*\"message\"\\s*:\\s*\"([^\"]*)\".*", "$1") : "";
            throw new IOException("Gmail said no (" + code + ")" + (msg.isEmpty() ? "" : ": " + msg));
        }
    }
}
