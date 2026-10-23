package br.com.suporte.horizontv;

import android.graphics.RectF;

import java.util.List;

final class DashboardHitTester {
    private DashboardHitTester() { }

    static int find(List<RectF> areas, float x, float y, ContinueCardRenderer continueCard) {
        for (int id = 0; id < areas.size(); id++) {
            RectF area = areas.get(id);
            if (area != null && area.contains(x, y)
                    && (id != 21 || continueCard.contains(x, y))) return id;
        }
        return -1;
    }
}
