package br.com.suporte.horizontv;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;

import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class ImapMailParserTest {
    @Test
    public void readsPlainTextAndSuppressesHtmlOnlyContent() throws Exception {
        MimeMessage plainMessage = new MimeMessage(Session.getInstance(new Properties()));
        plainMessage.setText("Olá, caixa de entrada", "UTF-8");
        assertEquals("Olá, caixa de entrada", ImapMailParser.plainTextBody(plainMessage));

        MimeMessage htmlMessage = new MimeMessage(Session.getInstance(new Properties()));
        htmlMessage.setContent("<script>alert(1)</script><b>HTML</b>", "text/html; charset=UTF-8");
        htmlMessage.saveChanges();
        assertTrue(ImapMailParser.plainTextBody(htmlMessage).startsWith("Esta mensagem não contém texto simples."));
    }

    @Test
    public void selectsPlainAlternativeAndLimitsMessageBody() throws Exception {
        MimeMessage multipartMessage = new MimeMessage(Session.getInstance(new Properties()));
        Multipart alternatives = new MimeMultipart("alternative");
        MimeBodyPart htmlPart = new MimeBodyPart();
        htmlPart.setContent("<b>não renderizar</b>", "text/html; charset=UTF-8");
        MimeBodyPart plainPart = new MimeBodyPart();
        plainPart.setText("Conteúdo simples", "UTF-8");
        alternatives.addBodyPart(htmlPart);
        alternatives.addBodyPart(plainPart);
        multipartMessage.setContent(alternatives);
        multipartMessage.saveChanges();
        assertEquals("Conteúdo simples", ImapMailParser.plainTextBody(multipartMessage));

        MimeMessage longMessage = new MimeMessage(Session.getInstance(new Properties()));
        longMessage.setText("x".repeat(ImapMailParser.MAX_BODY_CHARACTERS + 1), "UTF-8");
        longMessage.saveChanges();
        assertEquals(ImapMailParser.MAX_BODY_CHARACTERS, ImapMailParser.plainTextBody(longMessage).length());
    }
}
