package br.com.suporte.horizontv;

import jakarta.mail.AuthenticationFailedException;

final class ImapClientException extends Exception {
    enum Reason { AUTHENTICATION, CONNECTION, MESSAGE }

    final Reason reason;

    private ImapClientException(Reason reason) {
        super(userMessage(reason));
        this.reason = reason;
    }

    static ImapClientException from(Exception error, Reason fallback) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof AuthenticationFailedException) {
                return new ImapClientException(Reason.AUTHENTICATION);
            }
            current = current.getCause();
        }
        return new ImapClientException(fallback);
    }

    static String userMessage(Reason reason) {
        if (reason == Reason.AUTHENTICATION) return "A conta recusou a autenticação. Confira usuário, senha e IMAP habilitado.";
        if (reason == Reason.MESSAGE) return "Não foi possível abrir esta mensagem.";
        return "Não foi possível conectar ao servidor IMAP. Confira servidor, porta e conexão segura.";
    }
}
