package com.tarkovcraft.client.model;

import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.Locale;

/** Cuboids and box UVs supplied by the TarkovCraft model pack. */
public enum GearModel {
	ARMOR_HEAVY(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -4.50F, 0.50F, -2.50F, 9.0F, 11.0F, 5.0F, 0.25F),
			cube(21, 17, -4.00F, 1.50F, -3.60F, 8.0F, 9.0F, 1.0F, 0.10F),
			cube(40, 17, -4.00F, 1.50F, 2.60F, 8.0F, 9.0F, 1.0F, 0.10F),
			cube(0, 29, -5.20F, 4.50F, -2.00F, 1.0F, 6.0F, 4.0F),
			cube(11, 29, 4.20F, 4.50F, -2.00F, 1.0F, 6.0F, 4.0F),
			cube(28, 40, -4.00F, -1.50F, 1.80F, 8.0F, 2.0F, 1.0F, 0.05F),
			cube(47, 40, -3.50F, -1.20F, -3.00F, 7.0F, 2.0F, 1.0F),
			cube(44, 29, -4.50F, -1.50F, -2.50F, 1.0F, 2.0F, 5.0F),
			cube(0, 40, 3.50F, -1.50F, -2.50F, 1.0F, 2.0F, 5.0F),
			cube(13, 40, -3.00F, 11.00F, -3.30F, 6.0F, 3.0F, 1.0F),
			cube(0, 48, -2.50F, 2.00F, -3.80F, 5.0F, 2.0F, 1.0F)),
		part(Anchor.RIGHT_ARM,
			cube(29, 0, -3.50F, -2.00F, -2.50F, 5.0F, 6.0F, 5.0F, 0.15F),
			cube(22, 29, -4.00F, -1.00F, -2.00F, 1.0F, 4.0F, 4.0F)),
		part(Anchor.LEFT_ARM,
			cube(0, 17, -1.50F, -2.00F, -2.50F, 5.0F, 6.0F, 5.0F, 0.15F),
			cube(33, 29, 3.00F, -1.00F, -2.00F, 1.0F, 4.0F, 4.0F))),
	ARMOR_MEDIUM(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -4.50F, 1.00F, -2.50F, 9.0F, 10.0F, 5.0F, 0.15F),
			cube(29, 0, -4.00F, 2.20F, -3.40F, 8.0F, 8.0F, 1.0F, 0.05F),
			cube(0, 16, -4.00F, 2.20F, 2.40F, 8.0F, 8.0F, 1.0F, 0.05F),
			cube(0, 26, -3.50F, -1.20F, 1.80F, 7.0F, 2.0F, 1.0F),
			cube(41, 16, -4.20F, -1.20F, -1.00F, 1.0F, 2.0F, 3.0F),
			cube(50, 16, 3.20F, -1.20F, -1.00F, 1.0F, 2.0F, 3.0F),
			cube(19, 16, -5.00F, 5.20F, -2.00F, 1.0F, 5.0F, 4.0F),
			cube(30, 16, 4.00F, 5.20F, -2.00F, 1.0F, 5.0F, 4.0F),
			cube(17, 26, -2.50F, 2.20F, -3.60F, 5.0F, 2.0F, 1.0F))),
	ARMOR_SLICK(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -4.00F, 1.50F, -2.00F, 8.0F, 8.0F, 4.0F, 0.40F),
			cube(25, 0, -3.50F, 2.00F, -3.10F, 7.0F, 7.0F, 1.0F, 0.05F),
			cube(42, 0, -3.50F, 2.00F, 2.10F, 7.0F, 7.0F, 1.0F, 0.05F),
			cube(48, 13, -2.50F, 2.00F, -3.30F, 5.0F, 2.0F, 1.0F),
			cube(0, 13, -4.00F, 0.00F, -2.50F, 2.0F, 1.0F, 5.0F, 0.10F),
			cube(15, 13, 2.00F, 0.00F, -2.50F, 2.0F, 1.0F, 5.0F, 0.10F),
			cube(30, 13, -4.60F, 5.50F, -1.50F, 1.0F, 3.0F, 3.0F),
			cube(39, 13, 3.60F, 5.50F, -1.50F, 1.0F, 3.0F, 3.0F))),
	BACKPACK_LARGE(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -5.00F, -0.50F, 3.60F, 10.0F, 13.0F, 5.0F),
			cube(33, 38, -4.50F, -2.50F, 3.60F, 9.0F, 2.0F, 5.0F),
			cube(0, 46, -4.50F, -4.50F, 4.60F, 9.0F, 2.0F, 3.0F),
			cube(45, 0, -3.50F, 3.00F, 8.50F, 7.0F, 8.0F, 2.0F),
			cube(0, 19, -6.50F, 4.00F, 4.60F, 2.0F, 7.0F, 3.0F),
			cube(11, 19, 4.50F, 4.00F, 4.60F, 2.0F, 7.0F, 3.0F),
			cube(0, 30, -5.20F, 2.00F, 4.00F, 10.0F, 1.0F, 6.0F, 0.10F),
			cube(0, 38, -5.20F, 8.50F, 4.00F, 10.0F, 1.0F, 6.0F, 0.10F),
			cube(22, 19, -4.20F, -0.60F, -2.50F, 2.0F, 1.0F, 7.0F, 0.05F),
			cube(41, 19, 2.20F, -0.60F, -2.50F, 2.0F, 1.0F, 7.0F, 0.05F),
			cube(31, 0, -4.20F, 0.00F, -3.00F, 2.0F, 10.0F, 1.0F),
			cube(38, 0, 2.20F, 0.00F, -3.00F, 2.0F, 10.0F, 1.0F),
			cube(25, 46, -2.00F, 3.50F, -3.20F, 4.0F, 1.0F, 1.0F))),
	BACKPACK_SMALL(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -4.00F, 1.50F, 3.50F, 8.0F, 9.0F, 4.0F),
			cube(15, 14, -3.50F, 0.50F, 3.50F, 7.0F, 1.0F, 3.0F),
			cube(0, 14, -3.00F, 5.00F, 7.20F, 6.0F, 5.0F, 1.0F),
			cube(36, 14, -1.50F, -0.50F, 4.50F, 3.0F, 1.0F, 1.0F),
			cube(35, 0, -4.20F, -0.50F, -2.20F, 1.0F, 1.0F, 6.0F, 0.05F),
			cube(50, 0, 3.20F, -0.50F, -2.20F, 1.0F, 1.0F, 6.0F, 0.05F),
			cube(25, 0, -4.20F, 0.50F, -2.70F, 1.0F, 8.0F, 1.0F),
			cube(30, 0, 3.20F, 0.50F, -2.70F, 1.0F, 8.0F, 1.0F))),
	BALACLAVA(64, 64, true,
		part(Anchor.HEAD,
			cube(0, 0, -4.00F, -8.00F, -4.00F, 8.0F, 8.0F, 8.0F, 0.30F),
			cube(33, 0, -3.50F, 0.00F, -3.50F, 7.0F, 1.0F, 7.0F, 0.25F),
			cube(0, 17, -2.50F, -2.20F, -4.50F, 5.0F, 2.0F, 1.0F, 0.05F))),
	GAS_MASK(64, 64, true,
		part(Anchor.HEAD,
			cube(0, 0, -4.00F, -8.00F, -4.00F, 8.0F, 8.0F, 8.0F, 0.40F),
			cube(0, 17, -3.50F, -5.00F, -5.10F, 7.0F, 2.0F, 1.0F),
			cube(33, 0, -2.00F, -3.20F, -6.00F, 4.0F, 3.0F, 2.0F),
			cube(17, 17, -1.00F, -2.70F, -6.50F, 2.0F, 2.0F, 1.0F),
			cube(46, 0, 1.50F, -3.20F, -6.30F, 3.0F, 3.0F, 2.0F))),
	HEADSET_ACTIVE(64, 64, false,
		part(Anchor.HEAD,
			cube(41, 0, -4.50F, -9.10F, -0.50F, 9.0F, 1.0F, 1.0F, 0.05F),
			cube(22, 0, -4.80F, -9.00F, -0.50F, 1.0F, 4.0F, 1.0F),
			cube(27, 0, 3.80F, -9.00F, -0.50F, 1.0F, 4.0F, 1.0F),
			cube(0, 0, -5.60F, -5.50F, -1.50F, 2.0F, 4.0F, 3.0F),
			cube(11, 0, 3.60F, -5.50F, -1.50F, 2.0F, 4.0F, 3.0F),
			cube(32, 0, -4.80F, -2.20F, -4.50F, 1.0F, 1.0F, 3.0F),
			cube(0, 8, -4.50F, -2.00F, -5.00F, 2.0F, 1.0F, 1.0F))),
	HELMET_HIGHCUT(64, 64, false,
		part(Anchor.HEAD,
			cube(0, 0, -4.50F, -8.50F, -4.50F, 9.0F, 3.0F, 9.0F, 0.25F),
			cube(0, 13, -4.00F, -9.80F, -4.00F, 8.0F, 1.0F, 8.0F, 0.10F),
			cube(33, 13, -4.50F, -5.20F, 1.50F, 9.0F, 3.0F, 3.0F, 0.22F),
			cube(13, 30, -1.50F, -8.20F, -5.30F, 3.0F, 2.0F, 1.0F),
			cube(0, 23, -5.30F, -7.00F, -3.00F, 1.0F, 1.0F, 5.0F),
			cube(13, 23, 4.30F, -7.00F, -3.00F, 1.0F, 1.0F, 5.0F),
			cube(0, 30, -2.50F, -5.50F, 4.40F, 5.0F, 2.0F, 1.0F),
			cube(36, 23, -2.00F, -10.40F, -2.00F, 4.0F, 1.0F, 4.0F),
			cube(26, 23, -4.60F, -5.50F, -2.50F, 1.0F, 5.0F, 1.0F),
			cube(31, 23, 3.60F, -5.50F, -2.50F, 1.0F, 5.0F, 1.0F))),
	HELMET_LOWCUT(64, 64, false,
		part(Anchor.HEAD,
			cube(0, 0, -4.50F, -9.00F, -4.50F, 9.0F, 4.0F, 9.0F, 0.25F),
			cube(15, 14, -4.00F, -10.00F, -4.00F, 8.0F, 1.0F, 8.0F, 0.12F),
			cube(37, 0, -5.10F, -6.00F, -2.00F, 1.0F, 4.0F, 6.0F, 0.10F),
			cube(0, 14, 4.10F, -6.00F, -2.00F, 1.0F, 4.0F, 6.0F, 0.10F),
			cube(0, 25, -4.50F, -5.80F, 2.50F, 9.0F, 4.0F, 2.0F, 0.22F),
			cube(33, 25, -4.50F, -6.20F, -5.20F, 9.0F, 1.0F, 1.0F),
			cube(23, 25, -4.60F, -3.20F, -2.50F, 1.0F, 3.0F, 1.0F),
			cube(28, 25, 3.60F, -3.20F, -2.50F, 1.0F, 3.0F, 1.0F))),
	HELMET_RONIN(64, 64, true,
		part(Anchor.HEAD,
			cube(0, 0, -4.50F, -8.50F, -4.50F, 9.0F, 8.0F, 9.0F, 0.25F),
			cube(21, 18, -3.50F, -9.50F, -3.50F, 7.0F, 1.0F, 7.0F, 0.10F),
			cube(0, 18, -4.50F, -8.20F, -5.40F, 9.0F, 8.0F, 1.0F),
			cube(37, 0, -5.20F, -5.50F, -3.50F, 1.0F, 5.0F, 5.0F),
			cube(50, 0, 4.20F, -5.50F, -3.50F, 1.0F, 5.0F, 5.0F),
			cube(50, 18, -2.50F, -6.00F, 4.60F, 5.0F, 4.0F, 1.0F))),
	HELMET_VISOR(64, 64, true,
		part(Anchor.HEAD,
			cube(0, 0, -5.00F, -9.50F, -5.00F, 10.0F, 5.0F, 10.0F, 0.05F),
			cube(0, 27, -4.00F, -10.50F, -4.00F, 8.0F, 1.0F, 8.0F, 0.05F),
			cube(0, 16, -5.00F, -5.00F, -1.00F, 10.0F, 4.0F, 6.0F, 0.05F),
			cube(33, 27, -4.50F, -5.50F, -5.60F, 9.0F, 5.0F, 1.0F, 0.05F),
			cube(33, 37, -3.50F, -0.80F, -5.50F, 7.0F, 1.0F, 1.0F),
			cube(54, 27, -5.60F, -5.50F, -2.00F, 1.0F, 2.0F, 2.0F),
			cube(0, 37, 4.60F, -5.50F, -2.00F, 1.0F, 2.0F, 2.0F),
			cube(7, 37, -5.50F, -3.20F, 0.50F, 1.0F, 2.0F, 2.0F),
			cube(14, 37, -4.00F, -1.50F, 4.30F, 8.0F, 2.0F, 1.0F))),
	VISOR(64, 64, true, "helmet_visor",
		part(Anchor.HEAD,
			cube(33, 27, -4.50F, -5.50F, -5.60F, 9.0F, 5.0F, 1.0F, 0.05F),
			cube(54, 27, -5.60F, -5.50F, -2.00F, 1.0F, 2.0F, 2.0F),
			cube(0, 37, 4.60F, -5.50F, -2.00F, 1.0F, 2.0F, 2.0F))),
	MASK_TAGILLA(64, 64, false,
		part(Anchor.HEAD,
			cube(0, 13, -4.50F, -8.00F, -5.60F, 9.0F, 9.0F, 1.0F, 0.10F),
			cube(0, 0, -5.20F, -7.50F, -4.80F, 1.0F, 8.0F, 4.0F, 0.05F),
			cube(11, 0, 4.20F, -7.50F, -4.80F, 1.0F, 8.0F, 4.0F, 0.05F),
			cube(0, 24, -4.50F, -8.80F, -5.50F, 9.0F, 1.0F, 4.0F, 0.05F),
			cube(46, 24, -3.50F, -6.00F, -6.20F, 7.0F, 2.0F, 1.0F),
			cube(27, 24, -3.50F, 0.60F, -5.20F, 7.0F, 1.0F, 2.0F),
			cube(22, 0, -4.00F, -5.50F, -3.50F, 8.0F, 2.0F, 8.0F, 0.35F),
			cube(21, 13, -1.50F, -9.20F, -2.50F, 3.0F, 1.0F, 7.0F, 0.25F))),
	RIG_ARMORED(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -4.50F, 1.00F, -2.50F, 9.0F, 10.0F, 5.0F, 0.20F),
			cube(29, 0, -4.00F, 1.50F, -3.60F, 8.0F, 9.0F, 1.0F, 0.05F),
			cube(0, 16, -4.00F, 1.50F, 2.60F, 8.0F, 9.0F, 1.0F, 0.05F),
			cube(41, 16, -3.00F, 2.50F, 3.50F, 6.0F, 7.0F, 1.0F),
			cube(34, 27, -3.80F, 6.50F, -4.90F, 2.0F, 4.0F, 1.0F, 0.10F),
			cube(41, 27, -1.00F, 6.50F, -4.90F, 2.0F, 4.0F, 1.0F, 0.10F),
			cube(48, 27, 1.80F, 6.50F, -4.90F, 2.0F, 4.0F, 1.0F, 0.10F),
			cube(0, 35, -2.50F, 2.80F, -4.40F, 5.0F, 2.0F, 1.0F),
			cube(19, 16, -5.30F, 5.50F, -2.00F, 1.0F, 5.0F, 4.0F),
			cube(30, 16, 4.30F, 5.50F, -2.00F, 1.0F, 5.0F, 4.0F),
			cube(0, 27, -4.20F, -0.20F, -2.80F, 2.0F, 1.0F, 6.0F, 0.15F),
			cube(17, 27, 2.20F, -0.20F, -2.80F, 2.0F, 1.0F, 6.0F, 0.15F))),
	RIG_BELT_RPS(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -4.50F, 9.00F, -3.50F, 9.0F, 3.0F, 7.0F, 0.15F),
			cube(27, 21, -1.50F, 9.50F, -4.00F, 3.0F, 2.0F, 1.0F),
			cube(53, 11, -5.80F, 8.20F, -3.00F, 2.0F, 4.0F, 2.0F),
			cube(0, 21, -5.80F, 8.20F, -0.50F, 2.0F, 4.0F, 2.0F),
			cube(9, 21, 3.80F, 8.20F, -3.00F, 2.0F, 4.0F, 2.0F),
			cube(18, 21, 3.80F, 8.20F, -0.50F, 2.0F, 4.0F, 2.0F),
			cube(32, 11, -3.50F, 8.50F, 3.50F, 7.0F, 4.0F, 3.0F),
			cube(33, 0, -3.80F, 0.00F, -3.80F, 1.0F, 9.0F, 1.0F),
			cube(38, 0, 2.80F, 0.00F, -3.80F, 1.0F, 9.0F, 1.0F),
			cube(43, 0, -3.80F, -0.80F, -3.80F, 1.0F, 1.0F, 8.0F),
			cube(0, 11, 2.80F, -0.80F, -3.80F, 1.0F, 1.0F, 8.0F),
			cube(19, 11, -2.50F, 0.50F, 3.50F, 5.0F, 8.0F, 1.0F))),
	RIG_CHEST_HEAVY(64, 64, false,
		part(Anchor.BODY,
			cube(0, 0, -4.50F, 3.00F, -4.00F, 9.0F, 8.0F, 1.0F),
			cube(9, 10, -4.20F, 6.70F, -5.40F, 2.0F, 4.0F, 2.0F),
			cube(18, 10, -1.00F, 6.70F, -5.40F, 2.0F, 4.0F, 2.0F),
			cube(27, 10, 2.20F, 6.70F, -5.40F, 2.0F, 4.0F, 2.0F),
			cube(33, 18, -3.00F, 4.00F, -5.10F, 6.0F, 2.0F, 1.0F),
			cube(0, 10, 4.20F, 5.00F, -3.60F, 2.0F, 5.0F, 2.0F),
			cube(36, 10, -6.20F, 6.00F, -3.60F, 2.0F, 4.0F, 2.0F),
			cube(45, 10, 5.00F, 0.00F, -3.00F, 1.0F, 5.0F, 1.0F, -0.25F),
			cube(19, 18, -3.80F, 0.00F, -4.00F, 2.0F, 3.0F, 1.0F),
			cube(26, 18, 1.80F, 0.00F, -4.00F, 2.0F, 3.0F, 1.0F),
			cube(21, 0, -3.80F, -0.80F, -4.00F, 2.0F, 1.0F, 8.0F),
			cube(42, 0, 1.80F, -0.80F, -4.00F, 2.0F, 1.0F, 8.0F),
			cube(0, 18, -4.00F, 4.00F, 3.50F, 8.0F, 3.0F, 1.0F),
			cube(48, 18, -3.30F, 2.50F, -4.40F, 1.0F, 1.0F, 1.0F),
			cube(53, 18, 2.30F, 2.50F, -4.40F, 1.0F, 1.0F, 1.0F))),
	RIG_SCAV_VEST(64, 64, false,
		part(Anchor.BODY,
			cube(38, 0, -4.00F, 5.00F, -3.90F, 8.0F, 5.0F, 1.0F),
			cube(5, 10, -3.60F, 5.80F, -5.00F, 2.0F, 4.0F, 1.0F, 0.10F),
			cube(12, 10, 1.60F, 5.80F, -5.00F, 2.0F, 4.0F, 1.0F, 0.10F),
			cube(19, 10, -1.00F, 5.80F, -4.90F, 2.0F, 2.0F, 1.0F),
			cube(26, 10, -1.00F, 8.00F, -4.90F, 2.0F, 2.0F, 1.0F),
			cube(57, 0, -3.50F, 0.00F, -3.90F, 1.0F, 5.0F, 1.0F),
			cube(0, 10, 2.50F, 0.00F, -3.90F, 1.0F, 5.0F, 1.0F),
			cube(0, 0, -3.50F, -0.80F, -3.90F, 1.0F, 1.0F, 8.0F),
			cube(19, 0, 2.50F, -0.80F, -3.90F, 1.0F, 1.0F, 8.0F),
			cube(33, 10, -4.00F, 6.50F, 3.50F, 8.0F, 1.0F, 1.0F))),
	TACTICAL_CAP(64, 64, false,
		part(Anchor.HEAD,
			cube(0, 0, -4.00F, -8.00F, -4.00F, 8.0F, 3.0F, 8.0F, 0.45F),
			cube(33, 0, -3.50F, -9.20F, -3.50F, 7.0F, 1.0F, 7.0F, 0.15F),
			cube(0, 12, -4.00F, -6.00F, -7.20F, 8.0F, 1.0F, 3.0F),
			cube(34, 12, -3.00F, -5.80F, -8.00F, 6.0F, 1.0F, 1.0F),
			cube(23, 12, -2.00F, -8.20F, -4.70F, 4.0F, 2.0F, 1.0F))),
	TACTICAL_GOGGLES(64, 64, true,
		part(Anchor.HEAD,
			cube(0, 0, -4.00F, -5.00F, -4.00F, 8.0F, 2.0F, 8.0F, 0.45F),
			cube(33, 0, -4.50F, -5.80F, -5.00F, 9.0F, 3.0F, 1.0F),
			cube(7, 11, -4.00F, -5.20F, -5.30F, 8.0F, 2.0F, 1.0F, -0.05F),
			cube(54, 0, -4.90F, -5.20F, -3.50F, 1.0F, 2.0F, 2.0F),
			cube(0, 11, 3.90F, -5.20F, -3.50F, 1.0F, 2.0F, 2.0F))),
	USHANKA(64, 64, false,
		part(Anchor.HEAD,
			cube(0, 0, -4.50F, -9.00F, -4.50F, 9.0F, 4.0F, 9.0F, 0.15F),
			cube(0, 14, -4.50F, -8.20F, -5.50F, 9.0F, 3.0F, 1.0F, 0.10F),
			cube(37, 0, -5.30F, -5.50F, -1.50F, 1.0F, 6.0F, 4.0F, 0.05F),
			cube(48, 0, 4.30F, -5.50F, -1.50F, 1.0F, 6.0F, 4.0F, 0.05F),
			cube(21, 14, -4.50F, -5.50F, 3.80F, 9.0F, 3.0F, 1.0F, 0.10F))),
	GROUND_ARMOR(64, 64, false,
		part(Anchor.GROUND,
			cube(0, 0, -5.00F, -2.00F, -5.50F, 10.0F, 2.0F, 11.0F),
			cube(0, 14, -4.50F, -4.00F, -4.50F, 9.0F, 2.0F, 9.0F),
			cube(0, 34, -2.50F, -4.80F, -3.50F, 5.0F, 1.0F, 2.0F),
			cube(34, 26, -4.50F, -2.00F, -7.50F, 2.0F, 1.0F, 3.0F),
			cube(45, 26, 2.50F, -2.00F, -7.50F, 2.0F, 1.0F, 3.0F),
			cube(37, 14, -7.00F, -2.00F, -2.50F, 2.0F, 2.0F, 5.0F),
			cube(0, 26, 5.00F, -2.00F, -2.50F, 2.0F, 2.0F, 5.0F),
			cube(15, 26, -3.50F, -4.00F, -6.50F, 7.0F, 2.0F, 2.0F))),
	GROUND_BACKPACK_LARGE(64, 64, false,
		part(Anchor.GROUND,
			cube(0, 0, -5.00F, -5.50F, -6.00F, 10.0F, 5.0F, 12.0F),
			cube(19, 42, -4.50F, -5.00F, -8.00F, 9.0F, 4.0F, 2.0F),
			cube(23, 53, -4.50F, -7.00F, -8.50F, 9.0F, 3.0F, 2.0F),
			cube(0, 31, -3.50F, -7.50F, -3.50F, 7.0F, 2.0F, 8.0F),
			cube(31, 31, -7.00F, -4.00F, -3.50F, 2.0F, 3.0F, 7.0F),
			cube(0, 42, 5.00F, -4.00F, -3.50F, 2.0F, 3.0F, 7.0F),
			cube(0, 18, -4.00F, -1.00F, -5.50F, 2.0F, 1.0F, 11.0F),
			cube(27, 18, 2.00F, -1.00F, -5.50F, 2.0F, 1.0F, 11.0F),
			cube(42, 42, -5.20F, -5.80F, -2.00F, 10.0F, 5.0F, 1.0F, 0.10F),
			cube(0, 53, -5.20F, -5.80F, 2.50F, 10.0F, 5.0F, 1.0F, 0.10F))),
	GROUND_BACKPACK_SMALL(64, 64, false,
		part(Anchor.GROUND,
			cube(0, 0, -4.00F, -4.50F, -4.50F, 8.0F, 4.0F, 9.0F),
			cube(44, 14, -3.50F, -4.00F, -5.50F, 7.0F, 3.0F, 1.0F),
			cube(21, 14, -3.00F, -5.50F, -2.00F, 6.0F, 1.0F, 5.0F),
			cube(0, 24, -1.50F, -3.00F, -6.50F, 3.0F, 1.0F, 1.0F),
			cube(35, 0, -4.20F, -1.00F, -4.00F, 2.0F, 1.0F, 8.0F),
			cube(0, 14, 2.20F, -1.00F, -4.00F, 2.0F, 1.0F, 8.0F))),
	GROUND_CASE_MEDKIT(64, 64, false,
		part(Anchor.GROUND,
			cube(0, 0, -5.00F, -4.00F, -3.50F, 10.0F, 4.0F, 7.0F),
			cube(0, 12, -5.00F, -5.00F, -3.50F, 10.0F, 1.0F, 7.0F, 0.05F),
			cube(35, 12, -3.00F, -5.00F, -4.00F, 1.0F, 2.0F, 1.0F),
			cube(40, 12, 2.00F, -5.00F, -4.00F, 1.0F, 2.0F, 1.0F),
			cube(45, 12, -2.00F, -2.50F, -4.50F, 4.0F, 1.0F, 1.0F),
			cube(35, 0, -5.30F, -4.50F, -3.00F, 1.0F, 4.0F, 6.0F),
			cube(50, 0, 4.30F, -4.50F, -3.00F, 1.0F, 4.0F, 6.0F))),
	GROUND_HELMET_OPEN(64, 64, false,
		part(Anchor.GROUND,
			cube(0, 13, -4.50F, -2.00F, -4.50F, 9.0F, 2.0F, 9.0F),
			cube(0, 0, -4.50F, -5.00F, -4.50F, 9.0F, 3.0F, 9.0F, 0.10F),
			cube(0, 25, -3.50F, -6.00F, -3.50F, 7.0F, 1.0F, 7.0F),
			cube(0, 34, -1.50F, -4.50F, -5.20F, 3.0F, 2.0F, 1.0F),
			cube(29, 25, -5.20F, -3.20F, -3.00F, 1.0F, 1.0F, 6.0F),
			cube(44, 25, 4.20F, -3.20F, -3.00F, 1.0F, 1.0F, 6.0F),
			cube(9, 34, -5.50F, -1.00F, -1.50F, 2.0F, 1.0F, 2.0F),
			cube(18, 34, 3.50F, -1.00F, -1.50F, 2.0F, 1.0F, 2.0F))),
	GROUND_HELMET_VISOR(64, 64, true,
		part(Anchor.GROUND,
			cube(0, 0, -5.00F, -4.00F, -4.50F, 10.0F, 4.0F, 9.0F),
			cube(0, 14, -4.50F, -6.00F, -4.00F, 9.0F, 2.0F, 8.0F),
			cube(35, 14, -3.50F, -7.00F, -3.00F, 7.0F, 1.0F, 6.0F),
			cube(0, 25, -4.50F, -5.50F, -5.50F, 9.0F, 5.0F, 1.0F),
			cube(21, 25, -5.60F, -4.50F, -2.00F, 1.0F, 2.0F, 2.0F),
			cube(28, 25, 4.60F, -4.50F, -2.00F, 1.0F, 2.0F, 2.0F),
			cube(35, 25, -4.00F, -2.00F, 4.20F, 8.0F, 2.0F, 1.0F))),
	GROUND_MASK_TAGILLA(64, 64, false,
		part(Anchor.GROUND,
			cube(0, 0, -5.00F, -3.00F, -4.00F, 1.0F, 3.0F, 8.0F),
			cube(19, 0, 4.00F, -3.00F, -4.00F, 1.0F, 3.0F, 8.0F),
			cube(37, 12, -4.50F, -3.00F, -5.00F, 9.0F, 3.0F, 1.0F),
			cube(0, 12, -4.50F, -4.00F, -4.50F, 9.0F, 1.0F, 9.0F, 0.05F),
			cube(0, 23, -3.50F, -4.80F, -3.50F, 7.0F, 1.0F, 2.0F),
			cube(19, 23, -3.50F, -1.00F, 4.00F, 7.0F, 1.0F, 2.0F))),
	GROUND_RIG(64, 64, false,
		part(Anchor.GROUND,
			cube(0, 0, -5.50F, -2.00F, -4.00F, 11.0F, 2.0F, 8.0F),
			cube(39, 0, -4.50F, -4.00F, -1.00F, 2.0F, 2.0F, 4.0F),
			cube(52, 0, -1.00F, -4.00F, -1.00F, 2.0F, 2.0F, 4.0F),
			cube(0, 11, 2.50F, -4.00F, -1.00F, 2.0F, 2.0F, 4.0F),
			cube(0, 18, -3.00F, -3.00F, -3.50F, 6.0F, 1.0F, 2.0F),
			cube(13, 11, -7.50F, -2.50F, -2.00F, 2.0F, 2.0F, 4.0F),
			cube(26, 11, 5.50F, -2.50F, -2.00F, 2.0F, 2.0F, 4.0F),
			cube(39, 11, -4.00F, -1.00F, -7.00F, 2.0F, 1.0F, 3.0F),
			cube(50, 11, 2.00F, -1.00F, -7.00F, 2.0F, 1.0F, 3.0F),
			cube(17, 18, -3.50F, -2.00F, -4.50F, 1.0F, 1.0F, 1.0F),
			cube(22, 18, 2.50F, -2.00F, -4.50F, 1.0F, 1.0F, 1.0F)));

	private static final float DROPPED_SCALE = 0.85F;
	private static final float PIXELS_PER_BLOCK = 16.0F;

	private final BakedPart[] parts;
	private final Identifier baseTexture;
	private final boolean translucent;
	private final boolean groundModel;
	private final double centerX;
	private final double centerDepth;
	private final double bottom;

	GearModel(int textureWidth, int textureHeight, boolean translucent, Part... geometry) {
		this(textureWidth, textureHeight, translucent, null, geometry);
	}

	GearModel(int textureWidth, int textureHeight, boolean translucent, String textureName, Part... geometry) {
		this.groundModel = geometry[0].anchor() == Anchor.GROUND;
		this.translucent = translucent;
		String folder = groundModel ? "ground" : "wearable";
		String file = textureName == null ? name().toLowerCase(Locale.ROOT) : textureName;
		this.baseTexture = new Identifier("tarkovcraft", "textures/equipment/" + folder + "/" + file + ".png");

		ModelData data = new ModelData();
		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minDepth = Float.POSITIVE_INFINITY;
		float maxDepth = Float.NEGATIVE_INFINITY;
		float lowestPoint = Float.POSITIVE_INFINITY;
		for (Part part : geometry) {
			ModelPartBuilder builder = ModelPartBuilder.create();
			Anchor anchor = part.anchor();
			for (Cuboid cube : part.cuboids()) {
				cube.addTo(builder);
				minX = Math.min(minX, anchor.x + cube.x() - cube.dilation());
				maxX = Math.max(maxX, anchor.x + cube.x() + cube.width() + cube.dilation());
				if (groundModel) {
					minDepth = Math.min(minDepth, cube.z() - cube.dilation());
					maxDepth = Math.max(maxDepth, cube.z() + cube.depth() + cube.dilation());
					lowestPoint = Math.min(lowestPoint, -(cube.y() + cube.height() + cube.dilation()));
				} else {
					// When laid face up, model Y becomes horizontal depth and -Z becomes height.
					minDepth = Math.min(minDepth, -(anchor.y + cube.y() + cube.height() + cube.dilation()));
					maxDepth = Math.max(maxDepth, -(anchor.y + cube.y() - cube.dilation()));
					lowestPoint = Math.min(lowestPoint, -(cube.z() + cube.depth() + cube.dilation()));
				}
			}
			data.getRoot().addChild(anchor.name(), builder, ModelTransform.NONE);
		}
		ModelPart model = TexturedModelData.of(data, textureWidth, textureHeight).createModel();
		this.parts = new BakedPart[geometry.length];
		for (int index = 0; index < geometry.length; index++) {
			Anchor anchor = geometry[index].anchor();
			parts[index] = new BakedPart(anchor, model.getChild(anchor.name()));
		}
		this.centerX = (minX + maxX) / (2.0 * PIXELS_PER_BLOCK);
		this.centerDepth = (minDepth + maxDepth) / (2.0 * PIXELS_PER_BLOCK);
		this.bottom = lowestPoint / PIXELS_PER_BLOCK;
	}

	public void renderWorn(MatrixStack matrices, VertexConsumerProvider consumers, int light, BipedEntityModel<?> playerModel, Item item) {
		if (groundModel) {
			return;
		}
		VertexConsumer vertices = consumers.getBuffer(renderLayer(item));
		for (BakedPart part : parts) {
			ModelPart anchor = part.anchor().fromPlayer(playerModel);
			if (!anchor.visible || anchor.hidden) {
				continue;
			}
			matrices.push();
			anchor.rotate(matrices);
			part.model().render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
	}

	public void renderOnGround(MatrixStack matrices, VertexConsumerProvider consumers, int light, Item item) {
		VertexConsumer vertices = consumers.getBuffer(renderLayer(item));
		matrices.push();
		matrices.scale(DROPPED_SCALE, DROPPED_SCALE, DROPPED_SCALE);
		matrices.translate(centerX, -bottom, -centerDepth);
		if (!groundModel) {
			matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
		}
		matrices.scale(-1.0F, -1.0F, 1.0F);
		for (BakedPart part : parts) {
			Anchor anchor = part.anchor();
			matrices.push();
			matrices.translate(anchor.x / PIXELS_PER_BLOCK, anchor.y / PIXELS_PER_BLOCK, 0.0);
			part.model().render(matrices, vertices, light, OverlayTexture.DEFAULT_UV);
			matrices.pop();
		}
		matrices.pop();
	}

	private RenderLayer renderLayer(Item item) {
		Identifier texture = this == GROUND_CASE_MEDKIT || this == VISOR ? baseTexture : GearPalette.textureFor(item, baseTexture);
		return translucent ? RenderLayer.getEntityTranslucent(texture) : RenderLayer.getEntityCutoutNoCull(texture);
	}

	private static Part part(Anchor anchor, Cuboid... cuboids) {
		return new Part(anchor, cuboids);
	}

	private static Cuboid cube(int u, int v, float x, float y, float z, float width, float height, float depth) {
		return cube(u, v, x, y, z, width, height, depth, 0.0F);
	}

	private static Cuboid cube(int u, int v, float x, float y, float z, float width, float height, float depth, float dilation) {
		return new Cuboid(u, v, x, y, z, width, height, depth, dilation);
	}

	private record Cuboid(int u, int v, float x, float y, float z, float width, float height, float depth, float dilation) {
		void addTo(ModelPartBuilder builder) {
			builder.uv(u, v).cuboid(x, y, z, width, height, depth, new Dilation(dilation));
		}
	}

	private record Part(Anchor anchor, Cuboid[] cuboids) {}
	private record BakedPart(Anchor anchor, ModelPart model) {}

	private enum Anchor {
		HEAD(0, 0),
		BODY(0, 0),
		RIGHT_ARM(-5, 2),
		LEFT_ARM(5, 2),
		GROUND(0, 0);

		private final float x;
		private final float y;

		Anchor(float x, float y) {
			this.x = x;
			this.y = y;
		}

		ModelPart fromPlayer(BipedEntityModel<?> playerModel) {
			return switch (this) {
				case HEAD -> playerModel.head;
				case BODY -> playerModel.body;
				case RIGHT_ARM -> playerModel.rightArm;
				case LEFT_ARM -> playerModel.leftArm;
				case GROUND -> throw new IllegalStateException("Ground geometry has no player model part");
			};
		}
	}
}
