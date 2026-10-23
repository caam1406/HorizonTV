package br.com.suporte.horizontv;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public final class ImapAccountSettingsTest {
    @Test
    public void normalizesInternationalHostAndKeepsConfiguredLogin() {
        ImapAccountSettings settings = new ImapAccountSettings("IMAP.Example.COM", 993, "person@example.com");

        assertEquals("imap.example.com", settings.host);
        assertEquals(993, settings.port);
        assertEquals("person@example.com", settings.username);
    }

    @Test
    public void rejectsUrlsAndOutOfRangePorts() {
        assertInvalid(() -> new ImapAccountSettings("imaps://mail.example.com", 993, "person@example.com"));
        assertInvalid(() -> new ImapAccountSettings("mail.example.com/path", 993, "person@example.com"));
        assertInvalid(() -> new ImapAccountSettings("mail.example.com", 993, "person@example.com\r\nNOOP"));
        assertInvalid(() -> ImapAccountSettings.parsePort("0"));
        assertInvalid(() -> ImapAccountSettings.parsePort("65536"));
        assertInvalid(() -> ImapAccountSettings.parsePort("993;NOOP"));
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            fail("Expected invalid IMAP settings to be rejected.");
        } catch (IllegalArgumentException expected) {
            // The user-facing validation intentionally avoids echoing arbitrary input.
        }
    }
}
