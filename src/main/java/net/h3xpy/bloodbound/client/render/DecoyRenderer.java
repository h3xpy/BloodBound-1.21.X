package net.h3xpy.bloodbound.client.render;

import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.h3xpy.bloodbound.entity.DecoyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Draws Diagnostic Tool X's stand-in the way the game draws a player: the owner's own skin on the
 * wide or slim body it was made for, at a player's scale, with their armour, held items, head item,
 * elytra and cape. The name over its head goes through the usual living-entity rules, so the owner's
 * team decides whether it shows and how it is coloured, as it does for the owner.
 */
public class DecoyRenderer extends LivingEntityRenderer<DecoyEntity, PlayerModel<DecoyEntity>> {

    /** Vanilla draws players at fifteen sixteenths of their model's size. */
    private static final float PLAYER_SCALE = 0.9375F;

    private final PlayerModel<DecoyEntity> wide;
    private final PlayerModel<DecoyEntity> slim;

    public DecoyRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
        this.wide = this.model;
        this.slim = new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER_SLIM), true);
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new CapeLayer(this));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
    }

    /** The owner's skin, as the server sent it to this client; a default one if they are unknown here. */
    private static PlayerSkin skin(DecoyEntity decoy) {
        UUID owner = decoy.ownerId();
        if (owner == null) {
            return DefaultPlayerSkin.get(decoy.getUUID());
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(owner);
        return info != null ? info.getSkin() : DefaultPlayerSkin.get(owner);
    }

    @Override
    public void render(DecoyEntity decoy, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        // The body follows the skin: the layers draw on whichever model is current.
        this.model = skin(decoy).model() == PlayerSkin.Model.SLIM ? slim : wide;
        this.model.crouching = decoy.isCrouching();
        HumanoidModel.ArmPose main = decoy.getMainHandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        HumanoidModel.ArmPose off = decoy.getOffhandItem().isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        boolean rightHanded = decoy.getMainArm() == HumanoidArm.RIGHT;
        this.model.rightArmPose = rightHanded ? main : off;
        this.model.leftArmPose = rightHanded ? off : main;
        super.render(decoy, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public Vec3 getRenderOffset(DecoyEntity decoy, float partialTicks) {
        return decoy.isCrouching() ? new Vec3(0.0D, decoy.getScale() * -2.0F / 16.0D, 0.0D)
                : super.getRenderOffset(decoy, partialTicks);
    }

    @Override
    protected void scale(DecoyEntity decoy, PoseStack poseStack, float partialTicks) {
        poseStack.scale(PLAYER_SCALE, PLAYER_SCALE, PLAYER_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(DecoyEntity decoy) {
        return skin(decoy).texture();
    }

    /** The owner's cape, hanging still: a stand-in does not move, so neither does the cloth. */
    private static final class CapeLayer extends RenderLayer<DecoyEntity, PlayerModel<DecoyEntity>> {

        private CapeLayer(DecoyRenderer renderer) {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, DecoyEntity decoy,
                float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw,
                float headPitch) {
            ResourceLocation cape = skin(decoy).capeTexture();
            if (cape == null || decoy.isInvisible() || decoy.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                return;
            }
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.125F);
            poseStack.mulPose(Axis.XP.rotationDegrees(decoy.isCrouching() ? 25.0F : 6.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            getParentModel().renderCloak(poseStack, buffer.getBuffer(RenderType.entitySolid(cape)), packedLight,
                    OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }
}
