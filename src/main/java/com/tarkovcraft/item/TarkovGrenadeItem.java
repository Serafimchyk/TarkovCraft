package com.tarkovcraft.item;

import com.tarkovcraft.entity.TarkovGrenadeEntity;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Throwable TarkovCraft tactical grenade (Frag, Impact Frag, Flashbang, Smoke, CS Gas).
 */
public class TarkovGrenadeItem extends Item implements TarkovSizedItem {
	public enum Category {
		FRAG,
		IMPACT,
		FLASH,
		SMOKE,
		GAS
	}

	public enum GrenadeType {
		FRAG_F1(40, 0, 7.5f, 300.0f, 22, 0.70f, 0, Category.FRAG),
		FRAG_V40(56, 0, 4.5f, 190.0f, 15, 0.45f, 0, Category.FRAG),
		FRAG_RGN(70, 6, 5.5f, 230.0f, 18, 0.55f, 0, Category.IMPACT),
		FRAG_RGO(70, 6, 7.0f, 280.0f, 24, 0.70f, 0, Category.IMPACT),
		FLASH_M7290(40, 0, 11.0f, 0.0f, 0, 0.0f, 0, Category.FLASH),
		SMOKE_M18(30, 0, 5.5f, 0.0f, 0, 0.0f, 300, Category.SMOKE),
		GAS_CS(35, 0, 5.5f, 0.0f, 0, 0.0f, 240, Category.GAS);

		private final int fuseTicks;
		private final int armingTicks;
		private final float blastRadius;
		private final float maxDamage;
		private final int penetrationPower;
		private final float bleedChance;
		private final int cloudTicks;
		private final Category category;

		GrenadeType(
			int fuseTicks,
			int armingTicks,
			float blastRadius,
			float maxDamage,
			int penetrationPower,
			float bleedChance,
			int cloudTicks,
			Category category
		) {
			this.fuseTicks = fuseTicks;
			this.armingTicks = armingTicks;
			this.blastRadius = blastRadius;
			this.maxDamage = maxDamage;
			this.penetrationPower = penetrationPower;
			this.bleedChance = bleedChance;
			this.cloudTicks = cloudTicks;
			this.category = category;
		}

		public int getFuseTicks() {
			return fuseTicks;
		}

		public int getArmingTicks() {
			return armingTicks;
		}

		public boolean isImpactFuse() {
			return armingTicks > 0;
		}

		public float getBlastRadius() {
			return blastRadius;
		}

		public float getMaxDamage() {
			return maxDamage;
		}

		public int getPenetrationPower() {
			return penetrationPower;
		}

		public float getBleedChance() {
			return bleedChance;
		}

		public int getCloudTicks() {
			return cloudTicks;
		}

		public Category getCategory() {
			return category;
		}

		public static GrenadeType fromString(String raw) {
			if (raw == null || raw.isBlank()) {
				return FRAG_F1;
			}
			for (GrenadeType type : values()) {
				if (type.name().equalsIgnoreCase(raw.trim())) {
					return type;
				}
			}
			return FRAG_F1;
		}
	}

	private final GrenadeType grenadeType;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;

	public TarkovGrenadeItem(
		Settings settings,
		GrenadeType grenadeType,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		super(settings.maxCount(4));
		this.grenadeType = grenadeType != null ? grenadeType : GrenadeType.FRAG_F1;
		this.storedItemSize = storedItemSize != null ? storedItemSize : ItemSize.ONE_BY_ONE;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.RARE;
		this.weightKg = Math.max(0.0f, weightKg);
	}

	public GrenadeType getGrenadeType() {
		return grenadeType;
	}

	@Override
	public ItemSize getStoredItemSize() {
		return storedItemSize;
	}

	@Override
	public ItemRarityTier getRarityTier() {
		return rarityTier;
	}

	@Override
	public float getWeightKg() {
		return weightKg;
	}

	@Override
	public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
		ItemStack stack = user.getStackInHand(hand);
		user.getItemCooldownManager().set(this, 20);
		world.playSound(null, user.getX(), user.getY(), user.getZ(),
			SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.6f, 0.75f);

		if (!world.isClient) {
			TarkovGrenadeEntity grenade = new TarkovGrenadeEntity(world, user, stack, grenadeType);
			grenade.setVelocity(user, user.getPitch(), user.getYaw(), 0.0f, 1.35f, 1.0f);
			world.spawnEntity(grenade);

			if (!user.isCreative()) {
				stack.decrement(1);
				if (user instanceof ServerPlayerEntity && user instanceof PlayerLoadoutHolder holder) {
					holder.tarkovcraft$syncLoadout();
				}
			}
		}

		return TypedActionResult.success(stack, world.isClient());
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		String typeKey = switch (grenadeType.getCategory()) {
			case FRAG -> "tooltip.tarkovcraft.grenade_type_frag";
			case IMPACT -> "tooltip.tarkovcraft.grenade_type_impact";
			case FLASH -> "tooltip.tarkovcraft.grenade_type_flash";
			case SMOKE -> "tooltip.tarkovcraft.grenade_type_smoke";
			case GAS -> "tooltip.tarkovcraft.grenade_type_gas";
		};
		Formatting typeColor = switch (grenadeType.getCategory()) {
			case FRAG, IMPACT -> Formatting.RED;
			case FLASH -> Formatting.YELLOW;
			case SMOKE -> Formatting.GREEN;
			case GAS -> Formatting.GOLD;
		};
		tooltip.add(Text.translatable(typeKey).formatted(typeColor));

		String fuseSec = formatNumber(grenadeType.getFuseTicks() / 20.0f);
		if (grenadeType.isImpactFuse()) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.grenade_impact_fuse", fuseSec).formatted(Formatting.GRAY));
		} else {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.grenade_fuse", fuseSec).formatted(Formatting.GRAY));
		}

		tooltip.add(Text.translatable("tooltip.tarkovcraft.grenade_radius", formatNumber(grenadeType.getBlastRadius()))
			.formatted(Formatting.GRAY));

		if (grenadeType.getCloudTicks() > 0) {
			tooltip.add(Text.translatable("tooltip.tarkovcraft.grenade_cloud", grenadeType.getCloudTicks() / 20)
				.formatted(Formatting.AQUA));
		}

		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}

	private static String formatNumber(float value) {
		if (Math.abs(value - Math.round(value)) < 0.05f) {
			return String.valueOf(Math.round(value));
		}
		return String.format(Locale.ROOT, "%.1f", value);
	}
}
