package com.hizeric.shenaniganscore.core.ability;

import java.util.Objects;
import java.util.logging.Logger;

public record RegistrationContext(
        AbilityRegistry abilities,
        ListenerSink listeners,
        Logger logger
) {
    public RegistrationContext {
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(listeners, "listeners");
        Objects.requireNonNull(logger, "logger");
    }
}
