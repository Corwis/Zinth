package net.zanoria.zinth.combat;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;

/**
 * Public read-only API for combat state.
 * Lightlay uses only this interface.
 */
public interface CombatService {

    @Nullable CombatContext getContext(UUID playerId);

    boolean isInCombat(UUID playerId);

    @Nullable UUID lastOpponent(UUID playerId);
}
