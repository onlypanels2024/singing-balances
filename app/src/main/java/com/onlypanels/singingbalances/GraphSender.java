package com.onlypanels.singingbalances;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Sends one email (with an optional PDF) through Microsoft Graph, as the signed-in Outlook user. */
final class GraphSender {
    private GraphSender() {}

    static String json(String to, String subject, String body, String fileName, byte[] pdf) throws Exception {
        JSONObject msg = new JSONObject();
        msg.put("subject", subject);
        msg.put("body", new JSONObject().put("contentType", "Text").put("content", body));
        msg.put("toRecipients", new JSONArray().put(new JSONObject().put("emailAddress", new JSONObject().put("address", to))));
        if (pdf != null) {
            msg.put("attachments", new JSONArray().put(new JSONObject()
                    .put("@odata.type", "#microsoft.graph.fileAttachment")
                    .put("name", fileName)
                    .put("contentType", "application/pdf")
                    .put("contentBytes", Base64.getEncoder().encodeToString(pdf))));
        }
        return new JSONObject().put("message", msg).put("saveToSentItems", true).toString();
    }

    static void send(String accessToken, String to, String subject, String body, File pdf) throws IOException {
        String payload;
        try {
            payload = json(to, subject, body, pdf == null ? null : pdf.getName(), pdf == null ? null : GmailSender.read(pdf));
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Couldn't prepare the email");
        }
        HttpURLConnection c = (HttpURLConnection) new URL("https://graph.microsoft.com/v1.0/me/sendMail").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(30000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + accessToken);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try (OutputStream out = c.getOutputStream()) {
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        if (code == 401) throw new GmailSender.AuthExpired();
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
            throw new IOException("Outlook said no (" + code + ")" + (msg.isEmpty() ? "" : ": " + msg));
        }
    }
}
