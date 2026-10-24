package br.com.suporte.horizontv;

import android.view.KeyEvent;

import java.util.List;

final class DashboardFocusNavigation {
    static final int HERO_WATCH_SELECTION = 30;

    int moveFocus(int selected, int keyCode, AppShortcutDock appDock) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            if (appDock.handlesSelection(selected)) return appDock.moveFocus(selected, -1);
            if (selected == HERO_WATCH_SELECTION || selected == 9) return selected == HERO_WATCH_SELECTION ? 9 : HERO_WATCH_SELECTION;
            if (selected == 10 || selected == 11) return selected == 10 ? 11 : 10;
            if (selected == 21) return 27;
            if (selected >= 23 && selected <= 27) return selected == 23 ? 21 : selected - 1;
            if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) {
                return selected == 0 ? DashboardNavigation.ITEM_COUNT - 1 : selected - 1;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (appDock.handlesSelection(selected)) return appDock.moveFocus(selected, 1);
            if (selected == HERO_WATCH_SELECTION || selected == 9) return selected == HERO_WATCH_SELECTION ? 9 : HERO_WATCH_SELECTION;
            if (selected == 10 || selected == 11) return selected == 10 ? 11 : 10;
            if (selected == 21) return 23;
            if (selected >= 23 && selected <= 27) return selected == 27 ? 21 : selected + 1;
            if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) {
                return selected == DashboardNavigation.ITEM_COUNT - 1 ? 0 : selected + 1;
            }
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (selected < DashboardNavigation.ITEM_COUNT) return selected == 3 ? 10 : HERO_WATCH_SELECTION;
            if (selected == HERO_WATCH_SELECTION || selected == 9) return AppShortcutDock.SELECTION_BASE;
            if (appDock.handlesSelection(selected)) return 21;
            if (selected >= 21 && selected <= 27) return AppShortcutDock.SELECTION_BASE;
            if (selected == 10 || selected == 11) return 23;
        } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (appDock.handlesSelection(selected)) return 0;
            if (selected == 21) return AppShortcutDock.SELECTION_BASE;
            if (selected >= 23 && selected <= 27) return 18;
            if (selected == HERO_WATCH_SELECTION || selected == 9) return 0;
            if (selected == 10 || selected == 11) return 3;
        }
        return selected;
    }

    String descriptionForSelection(int selected, AppShortcutDock appDock, String lastAppLabel,
                                   FilmSuggestion featuredFilm, List<Headline> headlines) {
        if (selected >= 0 && selected < DashboardNavigation.ITEM_COUNT) {
            return DashboardNavigation.labelFor(selected);
        }
        if (appDock.handlesSelection(selected)) return appDock.labelForSelection(selected);
        if (selected == 21 && lastAppLabel != null) return "Continuar " + lastAppLabel;
        if (selected >= 23 && selected <= 27) {
            int headlineIndex = selected - 23 + DashboardView.TRENDING_STORY_COUNT;
            Headline item = getHeadline(headlines, headlineIndex);
            return item == null ? "Notícias" : item.title;
        }
        if (selected == 10) return "Clima da sua região";
        if (selected == 11) return "Versículo bíblico do dia";
        if (selected == HERO_WATCH_SELECTION) return featuredFilm == null
                ? "Filme em destaque" : "Abrir " + featuredFilm.title + " no TMDB";
        if (selected == 9) return "Buscar outra sugestão de filme";
        return "Horizon TV";
    }

    private Headline getHeadline(List<Headline> headlines, int index) {
        return index >= 0 && index < headlines.size() ? headlines.get(index) : null;
    }
}
