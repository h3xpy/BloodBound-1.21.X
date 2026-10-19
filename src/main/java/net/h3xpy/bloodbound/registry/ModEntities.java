package net.h3xpy.bloodbound.registry;

import java.util.function.Supplier;

import net.h3xpy.bloodbound.BloodBound;
import net.h3xpy.bloodbound.entity.BarbedWireEntity;
import net.h3xpy.bloodbound.entity.ChainAnchorEntity;
import net.h3xpy.bloodbound.entity.DecoyEntity;
import net.h3xpy.bloodbound.entity.IceShellEntity;
import net.h3xpy.bloodbound.entity.RitualEntity;
import net.h3xpy.bloodbound.entity.SanctumBubbleEntity;
import net.h3xpy.bloodbound.entity.SpringPadEntity;
import net.h3xpy.bloodbound.entity.TargetFoundEntity;
import net.h3xpy.bloodbound.entity.KnifeEntity;
import net.h3xpy.bloodbound.entity.PentagramEntity;
import net.h3xpy.bloodbound.entity.RiteEntity;
import net.h3xpy.bloodbound.entity.TripwireEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, BloodBound.MODID);

    /** Diagnostic Tool X's stand-in: a player's size and shape, for a few seconds. */
    public static final Supplier<EntityType<DecoyEntity>> DECOY =
            ENTITY_TYPES.register("decoy", () -> EntityType.Builder
                    .<DecoyEntity>of(DecoyEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.8F)
                    .eyeHeight(1.62F)
                    .clientTrackingRange(10)
                    .noSummon()
                    .build("decoy"));

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

    /** The consecration of a ritual as it is laid: a few seconds of light or fire. */
    public static final Supplier<EntityType<RiteEntity>> RITE =
            ENTITY_TYPES.register("rite", () -> EntityType.Builder
                    .<RiteEntity>of(RiteEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSummon()
                    .noSave()
                    .build("rite"));

    /** Salvation's pentagram, lying on the ground while it is drawn and binds. */
    public static final Supplier<EntityType<PentagramEntity>> PENTAGRAM =
            ENTITY_TYPES.register("pentagram", () -> EntityType.Builder
                    .<PentagramEntity>of(PentagramEntity::new, MobCategory.MISC)
                    .sized(0.5F, 0.1F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSummon()
                    .noSave()
                    .build("pentagram"));

    /** Sacrificial Knife's knife: drawn in flight and where it sticks. */
    public static final Supplier<EntityType<KnifeEntity>> KNIFE =
            ENTITY_TYPES.register("knife", () -> EntityType.Builder
                    .<KnifeEntity>of(KnifeEntity::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(8)
                    .updateInterval(1)
                    .noSummon()
                    .noSave()
                    .build("knife"));

    /** A Tripwires wire, standing on its first post. Its own size is a post: the wire is drawn past it. */
    public static final Supplier<EntityType<TripwireEntity>> TRIPWIRE =
            ENTITY_TYPES.register("tripwire", () -> EntityType.Builder
                    .<TripwireEntity>of(TripwireEntity::new, MobCategory.MISC)
                    .sized(0.2F, 1.0F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .build("tripwire"));

    /**
     * A ritual, or an Eavesdrop trap, standing on the ground. Tracked far further than the traps:
     * Eavesdrop watches its own from up to a hundred blocks away, and an entity nobody is tracking
     * cannot be lit up for them.
     */
    public static final Supplier<EntityType<RitualEntity>> RITUAL =
            ENTITY_TYPES.register("ritual", () -> EntityType.Builder
                    .<RitualEntity>of(RitualEntity::new, MobCategory.MISC)
                    .sized(1.0F, 0.2F)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSummon()
                    .build("ritual"));

    /** The block of ice Ice Block seals its owner in. */
    public static final Supplier<EntityType<IceShellEntity>> ICE_SHELL =
            ENTITY_TYPES.register("ice_shell", () -> EntityType.Builder
                    .<IceShellEntity>of(IceShellEntity::new, MobCategory.MISC)
                    .sized(1.3F, 2.2F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .build("ice_shell"));

    /** Chained Up's anchor: small, still, and there to be hit. */
    public static final Supplier<EntityType<ChainAnchorEntity>> CHAIN_ANCHOR =
            ENTITY_TYPES.register("chain_anchor", () -> EntityType.Builder
                    .<ChainAnchorEntity>of(ChainAnchorEntity::new, MobCategory.MISC)
                    .sized(0.6F, 0.6F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .build("chain_anchor"));

    /**
     * What a Holy Sanctum looks like. Tracked from afar: a bubble is something you see coming. Sent
     * often, since one carried by Cracked Halo moves with its owner; a still one sends nothing.
     */
    public static final Supplier<EntityType<SanctumBubbleEntity>> SANCTUM_BUBBLE =
            ENTITY_TYPES.register("sanctum_bubble", () -> EntityType.Builder
                    .<SanctumBubbleEntity>of(SanctumBubbleEntity::new, MobCategory.MISC)
                    .sized(0.2F, 0.2F)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .noSummon()
                    .build("sanctum_bubble"));

    /** Spring Pad: flat and still, like the other traps. */
    public static final Supplier<EntityType<SpringPadEntity>> SPRING_PAD =
            ENTITY_TYPES.register("spring_pad", () -> EntityType.Builder
                    .<SpringPadEntity>of(SpringPadEntity::new, MobCategory.MISC)
                    .sized(0.9F, 0.2F)
                    .clientTrackingRange(8)
                    .updateInterval(20)
                    .noSummon()
                    .build("spring_pad"));

    private ModEntities() {}

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntities::registerAttributes);
    }

    /** Living entities need their attributes registered: the decoy has a living thing's plain set. */
    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DECOY.get(), LivingEntity.createLivingAttributes().build());
    }
}
