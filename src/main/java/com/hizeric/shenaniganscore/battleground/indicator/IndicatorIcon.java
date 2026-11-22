package com.hizeric.shenaniganscore.battleground.indicator;

import net.kyori.adventure.text.Component;

@FunctionalInterface
public interface IndicatorIcon {

    IndicatorIcon NONE = () -> Component.empty();

    Component render();

    static IndicatorIcon glyph(char codepoint) {
        return () -> Component.text(String.valueOf(codepoint))
                .style(TechniqueGlyphs.FONT_STYLE);
    }

    static IndicatorIcon plain(String fallback) {
        return () -> Component.text(fallback);
    }

    static IndicatorIcon of(String miniMessage) {
        return () -> net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(miniMessage);
    }
}