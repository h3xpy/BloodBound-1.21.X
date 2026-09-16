package net.h3xpy.bloodbound.client;

import net.h3xpy.bloodbound.data.PlayerPerkData;
import net.h3xpy.bloodbound.event.BankShotHandler;
import net.h3xpy.bloodbound.perk.ModAddons;
import net.h3xpy.bloodbound.perk.ModPerks;
import net.h3xpy.bloodbound.perk.impl.FragNade;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Vector3f;

/**
 * Star Medal: the grenade's path and both blasts, drawn while the throw is being wound up.
 * <p>
 * Particles spawned on this client alone, so nobody else sees where it is going. The flight is
 * worked out with the same numbers the server throws it with, walls and all, which is what makes
 * the arc worth trusting.
 */
public final class ClientFragPreview {

    private static final DustParticleOptions PATH = new DustParticleOptions(new Vector3f(1.0F, 1.0F, 1.0F), 0.6F);
    private static final DustParticleOptions FIRST_RING = new DustParticleOptions(FragNade.CYAN, 1.0F);
    private static final DustParticleOptions SECOND_RING = new DustParticleOptions(FragNade.YELLOW, 1.0F);

    private static final int MAX_TICKS = 160;
    private static final int SUB_STEPS = 4;
    private static final int RING_POINTS = 28;

    private ClientFragPreview() {}

    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.tickCount % 2 != 0) {
            return;
        }
        PlayerPerkData data = ClientPerkData.get();
        int tier = data.getActiveTier(ModPerks.FRAGNADE);
        if (tier <= 0 || !data.isAddonActive(ModAddons.STAR_MEDAL)
                || data.getCooldownRemaining(ModPerks.FRAGNADE.id(), ClientPerkData.gameTime()) > 0) {
            return;
        }

        int held = heldTicks(data);
        if (held <= 0) {
            return;
        }

        double share = Math.min(1.0D, held / (double) ModPerks.FRAG_HOLD_MAX_TICKS);
        Vec3 position = player.getEyePosition();
        Vec3 velocity = player.getLookAngle().scale(ModPerks.fragSpeed(share));

        for (int tick = 0; tick < MAX_TICKS; tick++) {
            velocity = velocity.subtract(0.0D, ModPerks.FRAG_GRAVITY, 0.0D);
            Vec3 step = velocity.scale(1.0D / SUB_STEPS);
            boolean turned = false;
            for (int i = 0; i < SUB_STEPS && !turned; i++) {
                Vec3 to = position.add(step);
                BlockHitResult hit = minecraft.level.clip(new ClipContext(position, to, ClipContext.Block.COLLIDER,
                        ClipContext.Fluid.NONE, CollisionContext.empty()));
                if (hit.getType() == HitResult.Type.MISS) {
                    position = to;
                    continue;
                }
                Direction face = hit.getDirection();
                position = hit.getLocation().add(Vec3.atLowerCornerOf(face.getNormal()).scale(ModPerks.FRAG_RADIUS + 0.02D));
                if (face == Direction.UP) {
                    rings(minecraft, position, tier, data);
                    return;
                }
                velocity = BankShotHandler.reflect(velocity, face).scale(ModPerks.FRAG_BOUNCE_SPEED);
                turned = true;
            }
            if (tick % 2 == 0) {
                minecraft.level.addParticle(PATH, position.x, position.y, position.z, 0.0D, 0.0D, 0.0D);
            }
        }
    }

    /** How long the key of whichever slot holds Frag' Nade has been down. */
    private static int heldTicks(PlayerPerkData data) {
        for (int slot = 0; slot < PlayerPerkData.LOADOUT_SIZE; slot++) {
            if (ModPerks.FRAGNADE.id().equals(data.getLoadoutSlot(slot))) {
                return ClientEventHandler.slotHeldTicks(slot);
            }
        }
        return 0;
    }

    private static void rings(Minecraft minecraft, Vec3 centre, int tier, PlayerPerkData data) {
        boolean fragstone = data.isAddonActive(ModAddons.FRAGSTONE_SHARD);
        boolean powdered = data.isAddonActive(ModAddons.POWDERED_CRYSTAL);
        boolean quartz = data.isAddonActive(ModAddons.QUARTZ_PENDANT);
        ring(minecraft, centre, FragNade.radius(tier, true, fragstone, powdered, quartz), FIRST_RING);
        ring(minecraft, centre, FragNade.radius(tier, false, fragstone, powdered, quartz), SECOND_RING);
    }

    private static void ring(Minecraft minecraft, Vec3 centre, double radius, DustParticleOptions color) {
        for (int i = 0; i < RING_POINTS; i++) {
            double angle = Math.PI * 2.0D * i / RING_POINTS;
            minecraft.level.addParticle(color, centre.x + Math.cos(angle) * radius, centre.y + 0.1D,
                    centre.z + Math.sin(angle) * radius, 0.0D, 0.0D, 0.0D);
        }
    }
}
