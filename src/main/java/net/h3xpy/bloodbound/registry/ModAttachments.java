package net.h3xpy.bloodbound.registry;

import java.util.function.Supplier;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data attached to players and persisted with them.
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BloodBound.MODID);

    /**
     * Learned perks, loadout, cooldowns and soulweb. Copied on death so perk progress is permanent.
     */
    public static final Supplier<AttachmentType<PlayerPerkData>> PERK_DATA =
            ATTACHMENT_TYPES.register("perk_data", () -> AttachmentType
                    .<CompoundTag, PlayerPerkData>serializable(PlayerPerkData::new)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {}

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
