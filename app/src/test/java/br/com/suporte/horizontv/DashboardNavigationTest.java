package br.com.suporte.horizontv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DashboardNavigationTest {
    @Test
    public void topNavigationContainsEightItemsAndEndsWithPersonalization() {
        assertEquals(8, DashboardNavigation.ITEM_COUNT);
        assertEquals("Personalizar", DashboardNavigation.labelFor(DashboardNavigation.ITEM_COUNT - 1));
    }
}
