package com.tarkovcraft.client.renderer;

import com.tarkovcraft.entity.TarkovScavEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;

import java.util.List;

public final class TarkovScavRenderer extends MobEntityRenderer<TarkovScavEntity, PlayerEntityModel<TarkovScavEntity>> {
	private static final Identifier SCAV = new Identifier("tarkovcraft", "textures/entity/corpse_scav.png");
	private static final Identifier BOSS = new Identifier("tarkovcraft", "textures/entity/corpse_boss.png");

	public TarkovScavRenderer(EntityRendererFactory.Context context) {
		super(context, new PlayerEntityModel<>(context.getPart(EntityModelLayers.PLAYER), false) {
			@Override
			public void setAngles(TarkovScavEntity bot, float limbAngle, float limbDistance, float age,
					float headYaw, float headPitch) {
				ArmPose pose = bot.getMainHandStack().isEmpty() ? ArmPose.EMPTY : ArmPose.CROSSBOW_HOLD;
				rightArmPose = bot.getMainArm() == Arm.RIGHT ? pose : BipedEntityModel.ArmPose.EMPTY;
				leftArmPose = bot.getMainArm() == Arm.LEFT ? pose : BipedEntityModel.ArmPose.EMPTY;
				super.setAngles(bot, limbAngle, limbDistance, age, headYaw, headPitch);
			}
		}, 0.5f);
		addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
		addFeature(new TarkovEquipmentFeatureRenderer<>(this, TarkovScavRenderer::gear));
	}

	private static List<Item> gear(TarkovScavEntity bot) {
		return List.of(bot.getEquippedStack(EquipmentSlot.HEAD).getItem(),
			bot.getEquippedStack(EquipmentSlot.CHEST).getItem(), bot.rig().getItem());
	}

	@Override
	public Identifier getTexture(TarkovScavEntity bot) {
		return bot.isBoss() ? BOSS : SCAV;
	}
}
