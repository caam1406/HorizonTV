package br.com.suporte.horizontv;

import jakarta.mail.BodyPart;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.ParseException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

final class ImapMailParser {
    static final int MAX_BODY_CHARACTERS = 60_000;
    private static final int MAX_MIME_DEPTH = 5;
    private static final int MAX_MULTIPART_ITEMS = 30;

    private ImapMailParser() { }

    static String plainTextBody(Part part) throws MessagingException, IOException {
        String body = findPlainText(part, 0);
        if (body == null || body.trim().isEmpty()) {
            return "Esta mensagem não contém texto simples. Conteúdo HTML e anexos não são abertos.";
        }
        return body;
    }

    private static String findPlainText(Part part, int depth) throws MessagingException, IOException {
        if (depth > MAX_MIME_DEPTH) return null;
        if (part.isMimeType("text/plain")) return readBoundedText(part);
        if (!part.isMimeType("multipart/*")) return null;

        Object content = part.getContent();
        if (!(content instanceof Multipart)) return null;
        Multipart multipart = (Multipart) content;
        int itemCount = Math.min(multipart.getCount(), MAX_MULTIPART_ITEMS);
        for (int index = 0; index < itemCount; index++) {
            BodyPart bodyPart = multipart.getBodyPart(index);
            String body = findPlainText(bodyPart, depth + 1);
            if (body != null && !body.trim().isEmpty()) return body;
        }
        return null;
    }

    private static String readBoundedText(Part part) throws MessagingException, IOException {
        Charset charset = charsetFor(part.getContentType());
        StringBuilder text = new StringBuilder();
        char[] buffer = new char[2048];
        try (Reader reader = new InputStreamReader(part.getInputStream(), charset)) {
            int count;
            while (text.length() < MAX_BODY_CHARACTERS
                    && (count = reader.read(buffer, 0,
                    Math.min(buffer.length, MAX_BODY_CHARACTERS - text.length()))) != -1) {
                text.append(buffer, 0, count);
            }
        }
        return text.toString();
    }

    private static Charset charsetFor(String contentType) {
        try {
            String name = new ContentType(contentType).getParameter("charset");
            return name == null ? StandardCharsets.UTF_8 : Charset.forName(name);
        } catch (ParseException | IllegalArgumentException error) {
            return StandardCharsets.UTF_8;
        }
    }
}
