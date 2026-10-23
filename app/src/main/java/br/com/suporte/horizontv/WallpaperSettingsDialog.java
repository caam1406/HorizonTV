package br.com.suporte.horizontv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.widget.EditText;
import android.widget.Toast;

final class WallpaperSettingsDialog {
    private static final String[] THEMES = {
            "Espaço e nebulosas", "Auroras", "Natureza", "Montanhas",
            "Oceanos", "Cidades à noite", "Arte abstrata", "Personalizar busca…"
    };
    private static final String[] QUERIES = {
            "space nebula stars landscape", "aurora borealis landscape", "forest nature landscape",
            "mountain landscape", "ocean sea landscape", "city skyline night",
            "abstract colorful background"
    };

    private WallpaperSettingsDialog() { }

    static void show(Activity activity, DynamicWallpaperManager manager) {
        OpenverseImage image = manager.currentImage();
        String toggle = manager.isEnabled() ? "Pausar e usar o fundo original" : "Ativar fundo dinâmico";
        String[] actions = {toggle, "Escolher tema", "Personalizar busca", "Trocar imagem agora", "Créditos da imagem"};
        new AlertDialog.Builder(activity)
                .setTitle("Personalizar fundo · "
                        + (manager.isEnabled() ? manager.query() : "fundo original"))
                .setItems(actions, (dialog, index) -> {
                    if (index == 0) manager.setEnabled(!manager.isEnabled());
                    else if (index == 1) chooseTheme(activity, manager);
                    else if (index == 2) editQuery(activity, manager);
                    else if (index == 3) manager.refresh();
                    else showCredits(activity, image);
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    private static void chooseTheme(Activity activity, DynamicWallpaperManager manager) {
        new AlertDialog.Builder(activity)
                .setTitle("Estilo do fundo")
                .setItems(THEMES, (dialog, index) -> {
                    if (index == THEMES.length - 1) editQuery(activity, manager);
                    else manager.setQuery(QUERIES[index]);
                })
                .setNegativeButton("Voltar", (dialog, which) -> show(activity, manager))
                .show();
    }

    private static void editQuery(Activity activity, DynamicWallpaperManager manager) {
        EditText query = new EditText(activity);
        query.setSingleLine(true);
        query.setHint("Ex.: montanhas com céu estrelado");
        query.setText(manager.query());
        query.setSelection(query.length());
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        new AlertDialog.Builder(activity)
                .setTitle("Buscar imagens")
                .setMessage("Digite um tema para pesquisar no Openverse.")
                .setView(query)
                .setPositiveButton("Buscar", (dialog, which) -> {
                    String value = query.getText().toString().replaceAll("[\\p{Cntrl}]", " ")
                            .trim().replaceAll("\\s+", " ");
                    if (value.isEmpty()) {
                        Toast.makeText(activity, "Digite um tema para a busca.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    manager.setQuery(value);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private static void showCredits(Activity activity, OpenverseImage image) {
        if (image == null) {
            new AlertDialog.Builder(activity).setTitle("Créditos da imagem")
                    .setMessage("Ainda não há uma imagem dinâmica salva.")
                    .setPositiveButton("Fechar", null).show();
            return;
        }
        String creator = image.creator.isEmpty() ? "Autor não informado" : image.creator;
        String version = image.licenseVersion.isEmpty() ? "" : " " + image.licenseVersion;
        String message = "\"" + image.title + "\"\n" + creator + "\nLicença: "
                + image.license.toUpperCase() + version + "\n\n" + image.attribution;
        new AlertDialog.Builder(activity)
                .setTitle("Créditos da imagem")
                .setMessage(message)
                .setPositiveButton("Abrir origem", (dialog, which) -> openUrl(activity, image.sourceUrl))
                .setNeutralButton("Ver licença", (dialog, which) -> openUrl(activity, image.licenseUrl))
                .setNegativeButton("Fechar", null)
                .show();
    }

    private static void openUrl(Activity activity, String address) {
        if (!OpenverseImageClient.isHttpsUrl(address)) return;
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(address)));
        } catch (ActivityNotFoundException error) {
            Toast.makeText(activity, "Instale um navegador para abrir este link.", Toast.LENGTH_SHORT).show();
        }
    }
}
