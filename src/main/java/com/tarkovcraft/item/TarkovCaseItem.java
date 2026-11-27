package com.tarkovcraft.item;

import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.grid.ContainerItemHelper;
import com.tarkovcraft.grid.ItemSize;
import com.tarkovcraft.loadout.PlayerLoadoutHolder;
import com.tarkovcraft.screen.TarkovScreenHandler;
import com.tarkovcraft.search.ItemRarityTier;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.MilkBucketItem;
import net.minecraft.item.PotionItem;
import net.minecraft.item.SwordItem;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Openable TarkovCraft storage case (Wallet, Keytool, Docs, SICC, Medcase, THICC, etc.).
 * Persists an internal GridContainer in NBT via ContainerItemHelper.
 */
public class TarkovCaseItem extends Item implements TarkovSizedItem {
	public enum CaseRole {
		GENERAL("general"),
		MEDICAL("medical"),
		INJECTOR("injector"),
		AMMO("ammo"),
		GRENADE("grenade"),
		FOOD("food"),
		WEAPON("weapon"),
		ARMOR_PLATE("armor_plate"),
		MONEY("money");

		private final String translationSuffix;

		CaseRole(String translationSuffix) {
			this.translationSuffix = translationSuffix;
		}

		public String getTranslationKey() {
			return "tooltip.tarkovcraft.case_role." + translationSuffix;
		}

		public static CaseRole fromString(String raw) {
			if (raw == null || raw.isBlank()) {
				return GENERAL;
			}
			for (CaseRole role : values()) {
				if (role.name().equalsIgnoreCase(raw)) {
					return role;
				}
			}
			return GENERAL;
		}
	}

	private final int gridWidth;
	private final int gridHeight;
	private final ItemSize storedItemSize;
	private final ItemRarityTier rarityTier;
	private final float weightKg;
	private final CaseRole caseRole;

	public TarkovCaseItem(
		Settings settings,
		int gridWidth,
		int gridHeight,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg
	) {
		this(settings, gridWidth, gridHeight, storedItemSize, rarityTier, weightKg, CaseRole.GENERAL);
	}

	public TarkovCaseItem(
		Settings settings,
		int gridWidth,
		int gridHeight,
		ItemSize storedItemSize,
		ItemRarityTier rarityTier,
		float weightKg,
		CaseRole caseRole
	) {
		super(settings.maxCount(1));
		this.gridWidth = Math.max(1, gridWidth);
		this.gridHeight = Math.max(1, gridHeight);
		this.storedItemSize = storedItemSize != null ? storedItemSize : ItemSize.ONE_BY_ONE;
		this.rarityTier = rarityTier != null ? rarityTier : ItemRarityTier.RARE;
		this.weightKg = Math.max(0.0f, weightKg);
		this.caseRole = caseRole != null ? caseRole : CaseRole.GENERAL;
	}

	public CaseRole getCaseRole() {
		return caseRole;
	}

	public boolean isMedicalCase() {
		return caseRole == CaseRole.MEDICAL || caseRole == CaseRole.INJECTOR;
	}

	/**
	 * Returns true if the given ItemStack is allowed inside this storage case.
	 */
	public boolean canAcceptItem(ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getItem() instanceof TarkovCaseItem) {
			return false;
		}
		Item item = stack.getItem();
		return switch (caseRole) {
			case GENERAL -> true;
			case MEDICAL -> item instanceof TarkovMedicalItem || item instanceof TarkovStimulatorItem;
			case INJECTOR -> item instanceof TarkovStimulatorItem;
			case AMMO -> isTaczAmmoOrAttachment(stack);
			case GRENADE -> item instanceof TarkovGrenadeItem;
			case FOOD -> stack.isFood() || item instanceof TarkovFoodItem
				|| item instanceof PotionItem || item instanceof MilkBucketItem;
			case WEAPON -> AmmoTier.isTaczGunStack(stack) || isTaczAmmoOrAttachment(stack)
				|| item instanceof TarkovMeleeItem || item instanceof TarkovGrenadeItem
				|| item instanceof SwordItem || item instanceof BowItem
				|| item instanceof CrossbowItem || item instanceof TridentItem;
			case ARMOR_PLATE -> item instanceof TarkovArmorRepairItem || item instanceof TarkovArmorItem;
			case MONEY -> item instanceof TarkovFunctionalItem functional && functional.isValuable()
				|| item == Items.EMERALD || item == Items.GOLD_INGOT || item == Items.GOLD_NUGGET
				|| item == Items.DIAMOND || item == Items.NETHERITE_INGOT || item == Items.NETHERITE_SCRAP
				|| item == Items.AMETHYST_SHARD || item == Items.PAPER;
		};
	}

	private static boolean isTaczAmmoOrAttachment(ItemStack stack) {
		if (AmmoTier.isTaczAmmoStack(stack)) {
			return true;
		}
		Identifier id = Registries.ITEM.getId(stack.getItem());
		if (!"tacz".equals(id.getNamespace())) {
			return false;
		}
		String path = id.getPath();
		return path.contains("ammo") || path.contains("attachment") || path.contains("mag");
	}

	public int getGridWidth() {
		return gridWidth;
	}

	public int getGridHeight() {
		return gridHeight;
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
		if (world.isClient) {
			return TypedActionResult.pass(stack);
		}
		if (!(user instanceof ServerPlayerEntity sp) || sp.isSpectator()) {
			return TypedActionResult.fail(stack);
		}
		UUID caseUuid = ContainerItemHelper.getOrCreateContainerUuid(stack);
		if (sp instanceof PlayerLoadoutHolder holder && !sp.isCreative()) {
			holder.tarkovcraft$syncLoadout();
		}
		TarkovScreenHandler.openCaseScreen(sp, caseUuid);
		return TypedActionResult.success(stack, false);
	}

	@Override
	public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
		tooltip.add(Text.translatable("tooltip.tarkovcraft.case_grid",
			gridWidth, gridHeight, gridWidth * gridHeight).formatted(Formatting.AQUA));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.case_filter",
			Text.translatable(caseRole.getTranslationKey())).formatted(Formatting.GOLD));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.case_open_hint").formatted(Formatting.YELLOW));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.item_size",
			storedItemSize.width(), storedItemSize.height()).formatted(Formatting.GRAY));
		tooltip.add(Text.translatable("tooltip.tarkovcraft.med_weight",
			String.format(Locale.ROOT, "%.2f", weightKg)).formatted(Formatting.DARK_GRAY));
	}
}
