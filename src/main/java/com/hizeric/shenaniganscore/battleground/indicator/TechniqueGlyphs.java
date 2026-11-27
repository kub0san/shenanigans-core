package com.hizeric.shenaniganscore.battleground.indicator;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.format.Style;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class TechniqueGlyphs {

    public static final Key FONT_KEY = Key.key("shenanigans", "hud");
    public static final Style FONT_STYLE = Style.style().font(FONT_KEY).build();
    public static final char EMPTY = '\uE0FD';

    public enum State { READY, LOCKED, COOLDOWN }

    public enum Technique {
        BLUE("jjk:blue",
                '\uE000', '\uE001',
                '\uE002', '\uE003', '\uE004', '\uE005', '\uE006', '\uE007', '\uE008',
                '\uE009', '\uE00A', '\uE00B', '\uE00C', '\uE00D', '\uE00E', '\uE00F',
                '\uE010', '\uE011', '\uE012', '\uE013', '\uE014', '\uE015', '\uE016'),
        RED("jjk:red",
                '\uE017', '\uE018',
                '\uE019', '\uE01A', '\uE01B', '\uE01C', '\uE01D', '\uE01E', '\uE01F',
                '\uE020', '\uE021', '\uE022', '\uE023', '\uE024', '\uE025', '\uE026',
                '\uE027', '\uE028', '\uE029', '\uE02A', '\uE02B', '\uE02C', '\uE02D'),
        PURPLE("jjk:purple",
                '\uE02E', '\uE02F',
                '\uE030', '\uE031', '\uE032', '\uE033', '\uE034', '\uE035', '\uE036',
                '\uE037', '\uE038', '\uE039', '\uE03A', '\uE03B', '\uE03C', '\uE03D',
                '\uE03E', '\uE03F', '\uE040', '\uE041', '\uE042', '\uE043', '\uE044'),
        FUGA("jjk:fuga",
                '\uE045', '\uE046',
                '\uE047', '\uE048', '\uE049', '\uE04A', '\uE04B', '\uE04C', '\uE04D',
                '\uE04E', '\uE04F', '\uE050', '\uE051', '\uE052', '\uE053', '\uE054',
                '\uE055', '\uE056', '\uE057', '\uE058', '\uE059', '\uE05A', '\uE05B'),
        PIERCING_BLOOD("jjk:piercing_blood",
                '\uE05C', '\uE05D',
                '\uE05E', '\uE05F', '\uE060', '\uE061', '\uE062', '\uE063', '\uE064',
                '\uE065', '\uE066', '\uE067', '\uE068', '\uE069', '\uE06A', '\uE06B',
                '\uE06C', '\uE06D', '\uE06E', '\uE06F', '\uE070', '\uE071', '\uE072'),
        WORLD_SLASH("jjk:world_slash",
                '\uE073', '\uE074',
                '\uE075', '\uE076', '\uE077', '\uE078', '\uE079', '\uE07A', '\uE07B',
                '\uE07C', '\uE07D', '\uE07E', '\uE07F', '\uE080', '\uE081', '\uE082',
                '\uE083', '\uE084', '\uE085', '\uE086', '\uE087', '\uE088', '\uE089'),
        MALEVOLENT_SHRINE("jjk:malevolent_shrine",
                '\uE08A', '\uE08B',
                '\uE08C', '\uE08D', '\uE08E', '\uE08F', '\uE090', '\uE091', '\uE092',
                '\uE093', '\uE094', '\uE095', '\uE096', '\uE097', '\uE098', '\uE099',
                '\uE09A', '\uE09B', '\uE09C', '\uE09D', '\uE09E', '\uE09F', '\uE0A0'),
        UNLIMITED_VOID("jjk:unlimited_void",
                '\uE0A1', '\uE0A2',
                '\uE0A3', '\uE0A4', '\uE0A5', '\uE0A6', '\uE0A7', '\uE0A8', '\uE0A9',
                '\uE0AA', '\uE0AB', '\uE0AC', '\uE0AD', '\uE0AE', '\uE0AF', '\uE0B0',
                '\uE0B1', '\uE0B2', '\uE0B3', '\uE0B4', '\uE0B5', '\uE0B6', '\uE0B7'),
        TIME_CELL_MOON_PALACE("jjk:time_cell_moon_palace",
                '\uE0B8', '\uE0B9',
                '\uE0BA', '\uE0BB', '\uE0BC', '\uE0BD', '\uE0BE', '\uE0BF', '\uE0C0',
                '\uE0C1', '\uE0C2', '\uE0C3', '\uE0C4', '\uE0C5', '\uE0C6', '\uE0C7',
                '\uE0C8', '\uE0C9', '\uE0CA', '\uE0CB', '\uE0CC', '\uE0CD', '\uE0CE'),
        WOMB_PROFUSION("jjk:womb_profusion",
                '\uE0CF', '\uE0D0',
                '\uE0D1', '\uE0D2', '\uE0D3', '\uE0D4', '\uE0D5', '\uE0D6', '\uE0D7',
                '\uE0D8', '\uE0D9', '\uE0DA', '\uE0DB', '\uE0DC', '\uE0DD', '\uE0DE',
                '\uE0DF', '\uE0E0', '\uE0E1', '\uE0E2', '\uE0E3', '\uE0E4', '\uE0E5'),
        DASH_SWORD("minecraft:dash_sword",
                '\uE0E6', '\uE0E7',
                '\uE0E8', '\uE0E9', '\uE0EA', '\uE0EB', '\uE0EC', '\uE0ED', '\uE0EE',
                '\uE0EF', '\uE0F0', '\uE0F1', '\uE0F2', '\uE0F3', '\uE0F4', '\uE0F5',
                '\uE0F6', '\uE0F7', '\uE0F8', '\uE0F9', '\uE0FA', '\uE0FB', '\uE0FC');

        private final String canonicalId;
        private final char ready;
        private final char locked;
        private final char[] cooldown;

        Technique(String canonicalId, char ready, char locked, char... cooldown) {
            if (cooldown.length != 21) {
                throw new IllegalArgumentException("Expected 21 cooldown tiles, got " + cooldown.length);
            }
            this.canonicalId = canonicalId;
            this.ready = ready;
            this.locked = locked;
            this.cooldown = cooldown;
        }

        public char glyph(State state, int level) {
            return switch (state) {
                case READY -> ready;
                case LOCKED -> locked;
                case COOLDOWN -> cooldown[Math.max(0, Math.min(20, level))];
            };
        }

        public String canonicalId() { return canonicalId; }
    }

    private static final Map<String, Technique> BY_ID = new HashMap<>();
    static {
        for (Technique t : Technique.values()) BY_ID.put(t.canonicalId(), t);
    }

    public static Technique byId(String canonicalId) {
        if (canonicalId == null) return null;
        return BY_ID.get(canonicalId.toLowerCase(Locale.ROOT));
    }

    private TechniqueGlyphs() { }
}