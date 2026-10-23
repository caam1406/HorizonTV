package br.com.suporte.horizontv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class EmailClientActivity extends Activity {
    private final Handler mainThread = new Handler(Looper.getMainLooper());
    private final ExecutorService imapWorker = Executors.newSingleThreadExecutor();
    private ImapCredentialStore credentials;
    private LinearLayout content;
    private TextView status;
    private EditText hostField;
    private EditText portField;
    private EditText usernameField;
    private EditText passwordField;
    private boolean requestInProgress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        credentials = new ImapCredentialStore(this);
        createPage();
        ImapAccountSettings savedSettings = credentials.loadSettings();
        if (savedSettings == null) showSetup(null);
        else showSavedAccount(savedSettings);
    }

    @Override
    protected void onDestroy() {
        imapWorker.shutdownNow();
        super.onDestroy();
    }

    private void createPage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setBackgroundColor(Color.rgb(6, 10, 23));
        int horizontalPadding = dp(48);
        page.setPadding(horizontalPadding, dp(28), horizontalPadding, dp(24));

        TextView title = text("E-mail · IMAP", 30, Color.WHITE, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView subtitle = text("Caixa de entrada protegida por TLS", 15, 0xFFB6C5DA, false);
        page.addView(subtitle, margins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(6), 0, dp(20)));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(26), dp(22), dp(26), dp(24));
        content.setBackgroundColor(0xCC172235);
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = text("", 16, 0xFFD9E5F5, false);
        page.addView(status, margins(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(8), dp(10), dp(8), 0));
        setContentView(page);
    }

    private void showSavedAccount(ImapAccountSettings settings) {
        content.removeAllViews();
        content.addView(text("Conta configurada", 22, Color.WHITE, true), margins(-1, -2, 0, 0, 0, dp(12)));
        content.addView(text(settings.username, 18, 0xFFE5EDF8, false), margins(-1, -2, 0, 0, 0, dp(8)));
        content.addView(text(settings.host + ":" + settings.port + " · IMAP com TLS", 15, 0xFFB6C5DA, false),
                margins(-1, -2, 0, 0, 0, dp(22)));
        addButton(content, "Conectar à caixa de entrada", () -> connectSaved(settings));
        addButton(content, "Alterar configuração", () -> showSetup(settings));
        addButton(content, "Remover conta deste aparelho", this::confirmRemoveAccount);
        status.setText("A caixa será aberta em modo somente leitura.");
    }

    private void showSetup(ImapAccountSettings existing) {
        content.removeAllViews();
        hostField = addField("Servidor IMAP", "ex.: imap.gmail.com", InputType.TYPE_CLASS_TEXT,
                existing == null ? "" : existing.host);
        portField = addField("Porta com TLS", "993", InputType.TYPE_CLASS_NUMBER,
                existing == null ? "993" : Integer.toString(existing.port));
        usernameField = addField("Usuário ou endereço da conta", "nome@exemplo.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                existing == null ? "" : existing.username);
        passwordField = addField("Senha IMAP ou senha de app", "", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD, "");
        content.addView(text("A conexão sempre usa TLS com certificado validado. O Horizon não envia e-mail, não abre anexos e não altera o estado das mensagens.",
                14, 0xFFB6C5DA, false), margins(-1, -2, 0, dp(8), 0, dp(18)));
        addButton(content, "Conectar e salvar", this::connectFromForm);
        if (existing != null) addButton(content, "Voltar", () -> showSavedAccount(existing));
        status.setText(existing == null
                ? "Informe os dados fornecidos pelo seu provedor de e-mail."
                : "A senha não é exibida. Digite-a novamente para alterar e conectar.");
    }

    private EditText addField(String label, String hint, int inputType, String value) {
        content.addView(text(label, 15, 0xFFE3EBF7, true), margins(-1, -2, 0, dp(9), 0, dp(5)));
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setInputType(inputType);
        field.setTextSize(18);
        field.setTextColor(Color.WHITE);
        field.setHintTextColor(0xFF8998AD);
        field.setHint(hint);
        field.setText(value);
        field.setSelectAllOnFocus(false);
        content.addView(field, margins(-1, dp(58), 0, 0, 0, dp(16)));
        return field;
    }

    private void connectFromForm() {
        if (requestInProgress) return;
        ImapAccountSettings settings;
        char[] password;
        try {
            settings = new ImapAccountSettings(
                    hostField.getText().toString(),
                    ImapAccountSettings.parsePort(portField.getText().toString()),
                    usernameField.getText().toString());
            password = characters(passwordField);
            if (password.length == 0) {
                status.setText("Informe a senha IMAP ou a senha de app.");
                return;
            }
            if (password.length > 1024) {
                clear(password);
                status.setText("A senha deve ter no máximo 1024 caracteres.");
                return;
            }
        } catch (IllegalArgumentException error) {
            status.setText(error.getMessage());
            return;
        }
        passwordField.setText("");
        connect(settings, password);
    }

    private void connectSaved(ImapAccountSettings settings) {
        if (requestInProgress) return;
        setLoading("Conectando ao servidor IMAP…");
        imapWorker.execute(() -> {
            char[] password = null;
            try {
                password = credentials.loadPassword();
                if (password == null || password.length == 0) {
                    throw new GeneralSecurityException("Senha indisponível");
                }
                List<ImapMessageSummary> messages = ImapMailboxClient.fetchLatest(settings, password);
                mainThread.post(() -> showInbox(settings, messages));
            } catch (ImapClientException error) {
                mainThread.post(() -> showError(error.getMessage()));
            } catch (GeneralSecurityException | IOException error) {
                mainThread.post(() -> showError("A senha protegida não pôde ser lida. Altere a configuração da conta."));
            } finally {
                clear(password);
                mainThread.post(() -> requestInProgress = false);
            }
        });
    }

    private void connect(ImapAccountSettings settings, char[] password) {
        setLoading("Conectando ao servidor IMAP…");
        imapWorker.execute(() -> {
            try {
                List<ImapMessageSummary> messages = ImapMailboxClient.fetchLatest(settings, password);
                credentials.save(settings, password);
                mainThread.post(() -> showInbox(settings, messages));
            } catch (ImapClientException error) {
                mainThread.post(() -> showError(error.getMessage()));
            } catch (GeneralSecurityException | IOException error) {
                mainThread.post(() -> showError("Não foi possível proteger a senha neste aparelho."));
            } finally {
                clear(password);
                mainThread.post(() -> requestInProgress = false);
            }
        });
    }

    private void showInbox(ImapAccountSettings settings, List<ImapMessageSummary> messages) {
        requestInProgress = false;
        content.removeAllViews();
        content.addView(text("Caixa de entrada", 23, Color.WHITE, true), margins(-1, -2, 0, 0, 0, dp(7)));
        content.addView(text(settings.username + " · " + messages.size() + " mensagens recentes", 15, 0xFFB6C5DA, false),
                margins(-1, -2, 0, 0, 0, dp(14)));
        if (messages.isEmpty()) {
            content.addView(text("A caixa de entrada está vazia.", 18, Color.WHITE, false), margins(-1, -2, 0, 0, 0, dp(16)));
        } else {
            for (ImapMessageSummary message : messages) addMessageButton(message);
        }
        addButton(content, "Atualizar", () -> connectSaved(settings));
        addButton(content, "Configurar outra conta", () -> showSetup(null));
        addButton(content, "Remover conta deste aparelho", this::confirmRemoveAccount);
        status.setText("Mensagens abertas em modo somente leitura; conteúdo limitado a texto simples.");
    }

    private void addMessageButton(ImapMessageSummary message) {
        String date = message.sentDate == null ? "Data indisponível" : DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT, new Locale("pt", "BR")).format(message.sentDate);
        Button button = createButton(message.subject + "\n" + message.sender + " · " + date);
        button.setTextSize(16);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(74));
        button.setOnClickListener(view -> openMessage(message));
        content.addView(button, margins(-1, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0, 0, dp(6)));
    }

    private void openMessage(ImapMessageSummary message) {
        if (requestInProgress) return;
        ImapAccountSettings settings = credentials.loadSettings();
        if (settings == null) {
            showSetup(null);
            return;
        }
        setLoading("Abrindo mensagem…");
        requestInProgress = true;
        imapWorker.execute(() -> {
            char[] password = null;
            try {
                password = credentials.loadPassword();
                if (password == null || password.length == 0) {
                    throw new GeneralSecurityException("Senha indisponível");
                }
                String body = ImapMailboxClient.fetchPlainText(settings, password, message.uid);
                mainThread.post(() -> showMessage(message, body));
            } catch (ImapClientException error) {
                mainThread.post(() -> showError(error.getMessage()));
            } catch (GeneralSecurityException | IOException error) {
                mainThread.post(() -> showError("A senha protegida não pôde ser lida."));
            } finally {
                clear(password);
                mainThread.post(() -> requestInProgress = false);
            }
        });
    }

    private void showMessage(ImapMessageSummary message, String body) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(message.subject)
                .setMessage("De: " + message.sender + "\n\n" + body)
                .setPositiveButton("Fechar", null)
                .create();
        dialog.show();
        status.setText("Mensagem em modo somente leitura.");
    }

    private void confirmRemoveAccount() {
        new AlertDialog.Builder(this)
                .setTitle("Remover conta IMAP")
                .setMessage("Remover servidor, usuário e senha protegida deste aparelho?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Remover", (dialog, which) -> removeAccount())
                .show();
    }

    private void removeAccount() {
        try {
            credentials.clear();
            showSetup(null);
        } catch (GeneralSecurityException | IOException error) {
            showError("Não foi possível remover todas as credenciais locais.");
        }
    }

    private void setLoading(String message) {
        requestInProgress = true;
        status.setText(message);
    }

    private void showError(String message) {
        requestInProgress = false;
        status.setText(message);
    }

    private void addButton(LinearLayout target, String label, Runnable action) {
        Button button = createButton(label);
        button.setOnClickListener(view -> action.run());
        target.addView(button, margins(-1, dp(62), 0, dp(6), 0, dp(6)));
    }

    private Button createButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(18);
        button.setAllCaps(false);
        button.setFocusable(true);
        return button;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams margins(int width, int height, int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static char[] characters(EditText field) {
        int length = field.length();
        char[] value = new char[length];
        field.getText().getChars(0, length, value, 0);
        return value;
    }

    private static void clear(char[] value) {
        if (value != null) Arrays.fill(value, '\0');
    }
}
