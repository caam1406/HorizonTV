package br.com.suporte.horizontv;

import jakarta.mail.Address;
import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.FetchProfile;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.UIDFolder;
import jakarta.mail.internet.InternetAddress;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

final class ImapMailboxClient {
    static final int MAX_MESSAGES = 25;
    private static final String PROTOCOL = "imaps";
    private static final String INBOX = "INBOX";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 15_000;
    private static final int WRITE_TIMEOUT_MS = 15_000;

    private ImapMailboxClient() { }

    static List<ImapMessageSummary> fetchLatest(ImapAccountSettings settings, char[] password)
            throws ImapClientException {
        try (MailboxSession mailbox = open(settings, password)) {
            Folder inbox = mailbox.folder;
            int count = inbox.getMessageCount();
            if (count <= 0) return new ArrayList<>();
            int first = Math.max(1, count - MAX_MESSAGES + 1);
            Message[] messages = inbox.getMessages(first, count);
            FetchProfile profile = new FetchProfile();
            profile.add(FetchProfile.Item.ENVELOPE);
            inbox.fetch(messages, profile);
            ArrayList<ImapMessageSummary> result = new ArrayList<>(messages.length);
            UIDFolder uidFolder = (UIDFolder) inbox;
            for (Message message : messages) {
                long uid = uidFolder.getUID(message);
                if (uid <= 0) continue;
                result.add(new ImapMessageSummary(uid, message.getSubject(), senderFor(message), message.getSentDate()));
            }
            return result;
        } catch (AuthenticationFailedException error) {
            throw ImapClientException.from(error, ImapClientException.Reason.CONNECTION);
        } catch (MessagingException error) {
            throw ImapClientException.from(error, ImapClientException.Reason.CONNECTION);
        }
    }

    static String fetchPlainText(ImapAccountSettings settings, char[] password, long uid)
            throws ImapClientException {
        try (MailboxSession mailbox = open(settings, password)) {
            UIDFolder uidFolder = (UIDFolder) mailbox.folder;
            Message message = uidFolder.getMessageByUID(uid);
            if (message == null) throw new MessagingException("Message not found");
            return ImapMailParser.plainTextBody(message);
        } catch (AuthenticationFailedException error) {
            throw ImapClientException.from(error, ImapClientException.Reason.MESSAGE);
        } catch (MessagingException | IOException error) {
            throw ImapClientException.from(error, ImapClientException.Reason.MESSAGE);
        }
    }

    private static MailboxSession open(ImapAccountSettings settings, char[] password) throws MessagingException {
        Properties properties = new Properties();
        properties.setProperty("mail.store.protocol", PROTOCOL);
        properties.setProperty("mail.imaps.ssl.enable", "true");
        properties.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        properties.setProperty("mail.imaps.connectiontimeout", Integer.toString(CONNECT_TIMEOUT_MS));
        properties.setProperty("mail.imaps.timeout", Integer.toString(READ_TIMEOUT_MS));
        properties.setProperty("mail.imaps.writetimeout", Integer.toString(WRITE_TIMEOUT_MS));
        properties.setProperty("mail.imaps.fetchsize", "65536");
        Session session = Session.getInstance(properties);
        Store store = session.getStore(PROTOCOL);
        try {
            store.connect(settings.host, settings.port, settings.username, new String(password));
            Folder folder = store.getFolder(INBOX);
            folder.open(Folder.READ_ONLY);
            return new MailboxSession(store, folder);
        } catch (MessagingException error) {
            closeStore(store, error);
            throw error;
        }
    }

    private static void closeStore(Store store, MessagingException failure) {
        if (store == null || !store.isConnected()) return;
        try {
            store.close();
        } catch (MessagingException closeError) {
            failure.addSuppressed(closeError);
        }
    }

    private static String senderFor(Message message) throws MessagingException {
        Address[] addresses = message.getFrom();
        if (addresses == null || addresses.length == 0) return "Remetente desconhecido";
        Address address = addresses[0];
        if (address instanceof InternetAddress) {
            InternetAddress internetAddress = (InternetAddress) address;
            String personal = internetAddress.getPersonal();
            return personal == null || personal.trim().isEmpty() ? internetAddress.getAddress() : personal;
        }
        return address.toString();
    }

    private static final class MailboxSession implements AutoCloseable {
        private final Store store;
        private final Folder folder;

        private MailboxSession(Store store, Folder folder) {
            this.store = store;
            this.folder = folder;
        }

        @Override
        public void close() throws MessagingException {
            MessagingException failure = null;
            if (folder.isOpen()) {
                try {
                    folder.close(false);
                } catch (MessagingException error) {
                    failure = error;
                }
            }
            if (store.isConnected()) {
                try {
                    store.close();
                } catch (MessagingException error) {
                    if (failure == null) failure = error;
                    else failure.addSuppressed(error);
                }
            }
            if (failure != null) throw failure;
        }
    }
}
