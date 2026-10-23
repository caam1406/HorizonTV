package br.com.suporte.horizontv;

import java.util.Date;

final class ImapMessageSummary {
    final long uid;
    final String subject;
    final String sender;
    final Date sentDate;

    ImapMessageSummary(long uid, String subject, String sender, Date sentDate) {
        this.uid = uid;
        this.subject = clean(subject, "(sem assunto)");
        this.sender = clean(sender, "Remetente desconhecido");
        this.sentDate = sentDate == null ? null : new Date(sentDate.getTime());
    }

    private static String clean(String value, String fallback) {
        if (value == null) return fallback;
        String cleaned = value.replaceAll("\\p{Cntrl}", " ").trim();
        if (cleaned.isEmpty()) return fallback;
        return cleaned.length() <= 240 ? cleaned : cleaned.substring(0, 240);
    }
}
