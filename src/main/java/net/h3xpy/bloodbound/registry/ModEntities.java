package net.h3xpy.bloodbound.registry;

import java.util.function.Supplier;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.entity.BarbedWireEntity;
import net.h3xpy.bloodbound.entity.TargetFoundEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BloodBound.MODID);

    /** Barbed Wire's coil: flat, still, and only as wide as its own footprint. */
    public static final Supplier<EntityType<BarbedWireEntity>> BARBED_WIRE =
            ENTITY_TYPES.register("barbed_wire", () -> EntityType.Builder
                    .<BarbedWireEntity>of(BarbedWireEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .build("barbed_wire"));

    /** Target Found's tripwire: the same footprint, and just as still. */
    public static final Supplier<EntityType<TargetFoundEntity>> TARGET_FOUND =
            ENTITY_TYPES.register("target_found", () -> EntityType.Builder
                    .<TargetFoundEntity>of(TargetFoundEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .build("target_found"));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
