package net.h3xpy.bloodbound.client;

import java.util.HashSet;
import java.util.Set;

import net.h3xpy.bloodbound.network.AngelWingsPayload;

/** Which players nearby have Beware The Power Of An Angel's wings out, by entity id. */
public final class ClientAngelWings {

    private static final Set<Integer> WINGED = new HashSet<>();

    private ClientAngelWings() {}

    static void set(AngelWingsPayload payload) {
        if (payload.spread()) {
            WINGED.add(payload.entityId());
        } else {
            WINGED.remove(payload.entityId());
        }
    }

    public static boolean hasWings(int entityId) {
        return WINGED.contains(entityId);
    }

    public static void reset() {
        WINGED.clear();
    }
}
