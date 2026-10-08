package net.h3xpy.bloodbound.client;

import javax.annotation.Nullable;

import net.h3xpy.bloodbound.entity.SanctumBubbleEntity;
import net.h3xpy.bloodbound.network.SanctumHitPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Hitting a Holy Sanctum's shell with your hand, from outside or from inside.
 * <p>
 * The bubble is not picked like an ordinary entity: a hitbox the size of the bubble would contain
 * the eyes of anyone inside, and every click they made — on a block, a mob, anything — would land
 * on the shell instead. So the shell is found by casting the crosshair against the sphere itself,
 * and it only takes the blow when it is nearer than whatever the crosshair was going to hit.
 * Only a fresh press counts; holding the button down does not keep hitting it.
 */
public final class ClientSanctumHits {

    private static boolean attackWasDown;

    private ClientSanctumHits() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        attackWasDown = Minecraft.getInstance().options.keyAttack.isDown();
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        if (!event.isAttack()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.isSpectator()) {
            return;
        }
        SanctumBubbleEntity bubble = shellUnderCrosshair(minecraft, player);
        if (bubble == null) {
            return;
        }
        // The shell is in the way, so whatever the crosshair was on is not reached this click.
        event.setCanceled(true);
        if (!attackWasDown) {
            PacketDistributor.sendToServer(new SanctumHitPayload(bubble.getId()));
        }
        attackWasDown = true;
    }

    /** The nearest shell the crosshair meets within reach, if it comes before anything else. */
    @Nullable
    private static SanctumBubbleEntity shellUnderCrosshair(Minecraft minecraft, LocalPlayer player) {
        Vec3 eyes = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = player.entityInteractionRange();
        HitResult target = minecraft.hitResult;
        double blocked = target == null || target.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : eyes.distanceTo(target.getLocation());

        SanctumBubbleEntity best = null;
        double bestDistance = Math.min(reach, blocked);
        AABB around = player.getBoundingBox().inflate(reach + 8.0D);
        for (SanctumBubbleEntity bubble : minecraft.level.getEntitiesOfClass(SanctumBubbleEntity.class, around)) {
            double distance = shellDistance(eyes, look, bubble.position(), bubble.radius());
            if (distance >= 0.0D && distance < bestDistance) {
                best = bubble;
                bestDistance = distance;
            }
        }
        return best;
    }

    /**
     * How far along the line of sight it meets the sphere's surface: going in from outside, or out
     * from inside. Negative when it never does.
     */
    private static double shellDistance(Vec3 eyes, Vec3 look, Vec3 center, double radius) {
        Vec3 offset = eyes.subtract(center);
        double b = offset.dot(look);
        double c = offset.lengthSqr() - radius * radius;
        double discriminant = b * b - c;
        if (discriminant < 0.0D) {
            return -1.0D;
        }
        double root = Math.sqrt(discriminant);
        return c > 0.0D ? -b - root : -b + root;
    }
}
