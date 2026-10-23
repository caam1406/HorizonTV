package br.com.suporte.horizontv;

import java.net.IDN;
import java.util.Locale;

final class ImapAccountSettings {
    final String host;
    final int port;
    final String username;

    ImapAccountSettings(String host, int port, String username) {
        this.host = normalizeHost(host);
        this.port = validatePort(port);
        this.username = validateUsername(username);
    }

    static int parsePort(String value) {
        try {
            return validatePort(Integer.parseInt(value.trim()));
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Informe uma porta IMAP entre 1 e 65535.");
        }
    }

    private static String normalizeHost(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Informe o servidor IMAP.");
        }
        String host = value.trim();
        if (host.contains("://") || host.contains("/") || host.contains("@") || host.contains(" ")) {
            throw new IllegalArgumentException("Informe somente o nome do servidor, sem protocolo ou caminho.");
        }
        try {
            host = IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("O servidor IMAP informado é inválido.");
        }
        if (host.length() > 253 || host.startsWith(".") || host.endsWith(".") || host.contains("..")) {
            throw new IllegalArgumentException("O servidor IMAP informado é inválido.");
        }
        return host;
    }

    private static int validatePort(int value) {
        if (value < 1 || value > 65535) {
            throw new IllegalArgumentException("Informe uma porta IMAP entre 1 e 65535.");
        }
        return value;
    }

    private static String validateUsername(String value) {
        if (value == null || value.trim().isEmpty() || value.length() > 254
                || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Informe o usuário da conta IMAP.");
        }
        return value.trim();
    }
}
