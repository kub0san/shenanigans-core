package com.hizeric.shenaniganscore.battleground.menu.base;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The exact strings that were being parsed on the hanging server thread must
 * parse promptly. MiniMessage is exercised directly — ItemStack/Material need a
 * live Paper registry, which this unit test environment does not have.
 */
class GuiThemeParseTest {
    private static final MiniMessage MM = MiniMessage.miniMessage();

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void hubStringsParseWithoutPathologicalBacktracking() {
        String name = "<!italic>" + GuiTheme.TEXT + "<bold>Market";
        String about = "<!italic>" + GuiTheme.dim("Spend credits on items and abilities");
        String data = "<!italic>" + GuiTheme.value("balance", "120");

        assertNotNull(MM.deserialize(name));
        assertNotNull(MM.deserialize(about));
        assertNotNull(MM.deserialize(data));
        assertEquals("Spend credits on items and abilities", plain(MM.deserialize(about)));
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    void pageStringsParse() {
        assertNotNull(MM.deserialize("<!italic>" + GuiTheme.GOLD + "PAGE 1 OF 3"));
        assertNotNull(MM.deserialize("<!italic>" + GuiTheme.status(GuiTheme.GREEN, true, "Usable")));
    }

    @Test
    void abilityModelUsesExactNormalizedIds() {
        assertEquals("ability_blue", GuiTheme.abilityModel("JJK:BLUE"));
        assertEquals("ability_piercing_blood", GuiTheme.abilityModel("jjk:piercing_blood"));
        assertEquals("ultimate_timecell", GuiTheme.abilityModel("jjk:time_cell_moon_palace"));
        assertNull(GuiTheme.abilityModel("jjk:blood_red")); // no substring collision
        assertNull(GuiTheme.abilityModel(null));
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(component);
    }
}
