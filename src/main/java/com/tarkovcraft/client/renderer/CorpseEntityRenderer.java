package com.tarkovcraft.client.renderer;

import com.mojang.authlib.GameProfile;
import com.tarkovcraft.client.model.GearModel;
import com.tarkovcraft.entity.CorpseEntity;
import com.tarkovcraft.loadout.EquipmentSlotType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Renders CorpseEntity as a proper lying humanoid character model on the ground
 * (using Minecraft's PlayerEntityModel) with a single clean nameplate (no overlapping text!).
 */
public class CorpseEntityRenderer extends EntityRenderer<CorpseEntity> {
	private static final Identifier SCAV_SKIN = new Identifier("tarkovcraft", "textures/entity/corpse_scav.png");
	private static final Identifier BOSS_SKIN = new Identifier("tarkovcraft", "textures/entity/corpse_boss.png");
	private static final Identifier PLAYER_FALLBACK_SKIN = new Identifier("tarkovcraft", "textures/entity/corpse_player.png");
	private final PlayerEntityModel<LivingEntity> wideModel;
	private final PlayerEntityModel<LivingEntity> slimModel;
	private final Map<CorpseEntity, PlayerListEntry> offlineSkins = new WeakHashMap<>();

	public CorpseEntityRenderer(EntityRendererFactory.Context ctx) {
		super(ctx);
		this.shadowRadius = 0.55f;
		wideModel = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER), false);
		slimModel = new PlayerEntityModel<>(ctx.getPart(EntityModelLayers.PLAYER_SLIM), true);
		wideModel.child = false;
		slimModel.child = false;
	}

	@Override
	public void render(CorpseEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
		PlayerListEntry skin = playerSkin(entity);
		Identifier texture = skin == null ? fallbackTexture(entity) : skin.getSkinTexture();
		PlayerEntityModel<LivingEntity> model = skin != null && "slim".equals(skin.getModel()) ? slimModel : wideModel;
		matrices.push();

		// Lay the biped on its side so both the chest gear and backpack clear the floor.
		matrices.translate(0.0, 0.42, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw() + 180.0f));
		matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0f));
		matrices.scale(-0.9375f, -0.9375f, 0.9375f);
		matrices.translate(0.0, -0.75, 0.0);
		matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90.0f));

		// Slight natural fallen pose for arms and legs
		model.head.setAngles(0.05f, 0.18f, 0.0f);
		model.hat.copyTransform(model.head);
		model.body.setAngles(0.0f, 0.0f, 0.0f);
		model.jacket.copyTransform(model.body);
		model.leftArm.setAngles(-0.15f, 0.0f, -0.35f);
		model.leftSleeve.copyTransform(model.leftArm);
		model.rightArm.setAngles(0.10f, 0.0f, 0.42f);
		model.rightSleeve.copyTransform(model.rightArm);
		model.leftLeg.setAngles(0.0f, 0.0f, -0.12f);
		model.leftPants.copyTransform(model.leftLeg);
		model.rightLeg.setAngles(0.0f, 0.0f, 0.14f);
		model.rightPants.copyTransform(model.rightLeg);

		VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityTranslucent(texture));
		model.render(matrices, vc, light, OverlayTexture.DEFAULT_UV, 0.85f, 0.85f, 0.85f, 1.0f);

		for (EquipmentSlotType slot : CorpseEntity.VISIBLE_SLOTS) {
			ItemStack stack = entity.getVisibleEquipment(slot);
			GearModel gear = GearAppearance.worn(stack.getItem());
			if (gear != null) gear.renderWorn(matrices, vertexConsumers, light, model, stack.getItem());
		}
		matrices.pop();

		// Render ONLY ONE clean nameplate (do NOT call super.render which rendered a duplicate!)
		if (this.hasLabel(entity)) {
			this.renderLabelIfPresent(entity, Text.literal(entity.getOwnerName()), matrices, vertexConsumers, light);
		}
	}

	@Override
	protected boolean hasLabel(CorpseEntity entity) {
		return this.dispatcher.getSquaredDistanceToCamera(entity) < 36.0;
	}

	@Override
	public Identifier getTexture(CorpseEntity entity) {
		PlayerListEntry skin = playerSkin(entity);
		return skin == null ? fallbackTexture(entity) : skin.getSkinTexture();
	}

	private static Identifier fallbackTexture(CorpseEntity entity) {
		return switch (entity.getKind()) {
			case PLAYER -> PLAYER_FALLBACK_SKIN;
			case SCAV -> SCAV_SKIN;
			case BOSS -> BOSS_SKIN;
		};
	}

	@Nullable
	private PlayerListEntry playerSkin(CorpseEntity entity) {
		UUID owner = entity.getOwnerUuid();
		if (entity.getKind() != CorpseEntity.Kind.PLAYER || owner == null) return null;

		Identifier vanillaFallback = DefaultSkinHelper.getTexture(owner);
		ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();
		PlayerListEntry online = network == null ? null : network.getPlayerListEntry(owner);
		if (online != null && !vanillaFallback.equals(online.getSkinTexture())) return online;

		GameProfile profile = entity.getOwnerProfile();
		if (profile == null) return null;
		PlayerListEntry offline = offlineSkins.get(entity);
		if (offline == null || offline.getProfile() != profile) {
			offline = new PlayerListEntry(profile, false);
			offlineSkins.put(entity, offline);
		}
		return vanillaFallback.equals(offline.getSkinTexture()) ? null : offline;
	}
}
