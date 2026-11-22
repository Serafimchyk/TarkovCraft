package com.tarkovcraft.client.compat;

import com.tarkovcraft.TarkovCraftMod;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.block.SearchableLootBlock;
import com.tarkovcraft.grid.GridContainer;
import com.tarkovcraft.grid.GridEntry;
import com.tarkovcraft.loot.TarkovLootGenerator;
import com.tarkovcraft.loot.TarkovLootPool;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.workbench.TarkovWorkbenchRecipes;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Full EMI integration for TarkovCraft:
 * 1. Workbench Gear recipes (Helmets, Armor, Masks, Plates, Rigs, Backpacks)
 * 2. Workbench Tiered Ammo recipes (Scratch & FMJ -> HP/AP/IGN upgrades)
 * 3. EMI-Loot-style City & Crate Loot Tables for all 16 TarkovLootPools
 * 4. Extra EMI sidebar stacks for tiered TaCZ ammo & preconfigured city loot crates
 */
@Environment(EnvType.CLIENT)
public final class TarkovEmiPlugin implements EmiPlugin {

	public static final EmiStack WORKBENCH_ICON = EmiStack.of(ModBlocks.TARKOV_WORKBENCH);
	public static final EmiStack CRATE_ICON = EmiStack.of(ModBlocks.WEAPON_CRATE);

	public static final EmiRecipeCategory WORKBENCH_GEAR_CATEGORY = new EmiRecipeCategory(
		new Identifier(TarkovCraftMod.MOD_ID, "workbench_gear"),
		WORKBENCH_ICON
	);
	public static final EmiRecipeCategory WORKBENCH_AMMO_CATEGORY = new EmiRecipeCategory(
		new Identifier(TarkovCraftMod.MOD_ID, "workbench_ammo"),
		WORKBENCH_ICON
	);
	public static final EmiRecipeCategory LOOT_TABLES_CATEGORY = new EmiRecipeCategory(
		new Identifier(TarkovCraftMod.MOD_ID, "loot_tables"),
		CRATE_ICON
	);

	@Override
	public void register(EmiRegistry registry) {
		registry.addCategory(WORKBENCH_GEAR_CATEGORY);
		registry.addCategory(WORKBENCH_AMMO_CATEGORY);
		registry.addCategory(LOOT_TABLES_CATEGORY);

		registry.addWorkstation(WORKBENCH_GEAR_CATEGORY, WORKBENCH_ICON);
		registry.addWorkstation(WORKBENCH_AMMO_CATEGORY, WORKBENCH_ICON);
		registry.addWorkstation(LOOT_TABLES_CATEGORY, EmiStack.of(ModBlocks.WEAPON_CRATE));
		registry.addWorkstation(LOOT_TABLES_CATEGORY, EmiStack.of(ModBlocks.MEDBAG));
		registry.addWorkstation(LOOT_TABLES_CATEGORY, EmiStack.of(ModBlocks.TECHNICAL_CRATE));
		registry.addWorkstation(LOOT_TABLES_CATEGORY, EmiStack.of(ModBlocks.AIRDROP_CRATE));

		registerGearRecipes(registry);
		registerAmmoRecipes(registry);
		registerLootTableRecipes(registry);
	}

	private static void registerGearRecipes(EmiRegistry registry) {
		for (TarkovWorkbenchRecipes.GearCategory cat : TarkovWorkbenchRecipes.GearCategory.values()) {
			for (TarkovWorkbenchRecipes.GearRecipe recipe : TarkovWorkbenchRecipes.getGearRecipes(cat)) {
				Identifier itemId = Registries.ITEM.getId(recipe.resultItem());
				Identifier recipeId = new Identifier(TarkovCraftMod.MOD_ID, "/workbench_gear/" + itemId.getPath());
				List<EmiIngredient> inputs = new ArrayList<>();
				for (TarkovWorkbenchRecipes.Ingredient ing : recipe.materials()) {
					inputs.add(EmiStack.of(ing.item(), ing.count()));
				}
				EmiStack output = EmiStack.of(recipe.resultItem());
				registry.addRecipe(new WorkbenchEmiRecipe(WORKBENCH_GEAR_CATEGORY, recipeId, inputs, output));
			}
		}
	}

	private static void registerAmmoRecipes(EmiRegistry registry) {
		if (!Registries.ITEM.containsId(new Identifier("tacz", "ammo"))) {
			return;
		}
		for (TaCZCaliberCatalog.CaliberSpec spec : TaCZCaliberCatalog.getAllKnownCalibers()) {
			String safeCaliber = spec.ammoId().replace(':', '_').toLowerCase(Locale.ROOT);
			for (AmmoTier tier : spec.getAllowedTiersList()) {
				ItemStack outStack = AmmoTier.createTaczAmmoStack(spec.ammoId(), tier, spec.getBatchSize());
				if (outStack.isEmpty()) {
					continue;
				}
				// Expose tiered ammo variant in EMI item sidebar
				registry.addEmiStack(EmiStack.of(AmmoTier.createTaczAmmoStack(spec.ammoId(), tier, 1)));

				// Scratch recipe
				TarkovWorkbenchRecipes.AmmoRecipe scratch = TarkovWorkbenchRecipes.getAmmoRecipe(spec.ammoId(), tier, false);
				List<EmiIngredient> scratchInputs = new ArrayList<>();
				for (TarkovWorkbenchRecipes.Ingredient ing : scratch.materials()) {
					scratchInputs.add(EmiStack.of(ing.item(), ing.count()));
				}
				Identifier scratchId = new Identifier(TarkovCraftMod.MOD_ID, "/workbench_ammo/scratch_" + safeCaliber + "_" + tier.getId());
				registry.addRecipe(new WorkbenchEmiRecipe(WORKBENCH_AMMO_CATEGORY, scratchId, scratchInputs, EmiStack.of(outStack)));

				// Upgrade from FMJ recipe
				if (tier != AmmoTier.FMJ) {
					TarkovWorkbenchRecipes.AmmoRecipe upg = TarkovWorkbenchRecipes.getAmmoRecipe(spec.ammoId(), tier, true);
					List<EmiIngredient> upgInputs = new ArrayList<>();
					ItemStack fmjStack = AmmoTier.createTaczAmmoStack(spec.ammoId(), AmmoTier.FMJ, upg.requiredFmjCount());
					if (!fmjStack.isEmpty()) {
						upgInputs.add(EmiStack.of(fmjStack));
					}
					for (TarkovWorkbenchRecipes.Ingredient ing : upg.materials()) {
						upgInputs.add(EmiStack.of(ing.item(), ing.count()));
					}
					Identifier upgId = new Identifier(TarkovCraftMod.MOD_ID, "/workbench_ammo/upgrade_" + safeCaliber + "_" + tier.getId());
					registry.addRecipe(new WorkbenchEmiRecipe(WORKBENCH_AMMO_CATEGORY, upgId, upgInputs, EmiStack.of(outStack)));
				}
			}
		}
	}

	private static void registerLootTableRecipes(EmiRegistry registry) {
		for (TarkovLootPool pool : TarkovLootPool.values()) {
			ItemStack crateStack = SearchableLootBlock.createLootCrateStack(pool);
			if (pool != TarkovLootPool.SCAV_POCKET) {
				registry.addEmiStack(EmiStack.of(crateStack));
			}
			// Sample deterministic representative drops from this pool (32 rolls -> up to 24 unique items)
			Map<String, EmiStack> uniqueDrops = new LinkedHashMap<>();
			Random rng = Random.create(0x7A9C0FL ^ (pool.ordinal() * 7919L));
			for (int sample = 0; sample < 10 && uniqueDrops.size() < 24; sample++) {
				GridContainer probe = new GridContainer(8, 8);
				TarkovLootGenerator.populateLootGrid(probe, pool, rng);
				for (GridEntry entry : probe.getEntries()) {
					ItemStack st = entry.getStack();
					if (st.isEmpty()) {
						continue;
					}
					String dedupKey = Registries.ITEM.getId(st.getItem()).toString()
						+ (st.hasNbt() ? "|" + st.getNbt().getString("GunId") + "|" + st.getNbt().getString("AttachmentId") + "|" + st.getNbt().getString("AmmoId") : "");
					if (!uniqueDrops.containsKey(dedupKey)) {
						ItemStack one = st.copy();
						one.setCount(1);
						uniqueDrops.put(dedupKey, EmiStack.of(one));
						if (uniqueDrops.size() >= 24) {
							break;
						}
					}
				}
			}
			if (!uniqueDrops.isEmpty()) {
				Identifier id = new Identifier(TarkovCraftMod.MOD_ID, "/loot_table/" + pool.getId());
				registry.addRecipe(new LootPoolEmiRecipe(id, pool, EmiStack.of(crateStack), new ArrayList<>(uniqueDrops.values())));
			}
		}
	}

	private static final class WorkbenchEmiRecipe implements EmiRecipe {
		private final EmiRecipeCategory category;
		private final Identifier id;
		private final List<EmiIngredient> inputs;
		private final EmiStack output;

		private WorkbenchEmiRecipe(EmiRecipeCategory category, Identifier id, List<EmiIngredient> inputs, EmiStack output) {
			this.category = category;
			this.id = id;
			this.inputs = inputs;
			this.output = output;
		}

		@Override
		public EmiRecipeCategory getCategory() {
			return category;
		}

		@Override
		public @Nullable Identifier getId() {
			return id;
		}

		@Override
		public List<EmiIngredient> getInputs() {
			return inputs;
		}

		@Override
		public List<EmiStack> getOutputs() {
			return List.of(output);
		}

		@Override
		public int getDisplayWidth() {
			return 144;
		}

		@Override
		public int getDisplayHeight() {
			return 38;
		}

		@Override
		public void addWidgets(WidgetHolder widgets) {
			int maxShown = Math.min(4, inputs.size());
			for (int i = 0; i < maxShown; i++) {
				widgets.addSlot(inputs.get(i), 4 + i * 19, 10);
			}
			widgets.addTexture(EmiTexture.EMPTY_ARROW, 84, 11);
			widgets.addSlot(output, 114, 6).large(true).recipeContext(this);
		}
	}

	private static final class LootPoolEmiRecipe implements EmiRecipe {
		private final Identifier id;
		private final TarkovLootPool pool;
		private final EmiStack crateIcon;
		private final List<EmiStack> outputs;

		private LootPoolEmiRecipe(Identifier id, TarkovLootPool pool, EmiStack crateIcon, List<EmiStack> outputs) {
			this.id = id;
			this.pool = pool;
			this.crateIcon = crateIcon;
			this.outputs = outputs;
		}

		@Override
		public EmiRecipeCategory getCategory() {
			return LOOT_TABLES_CATEGORY;
		}

		@Override
		public @Nullable Identifier getId() {
			return id;
		}

		@Override
		public List<EmiIngredient> getInputs() {
			return List.of(crateIcon);
		}

		@Override
		public List<EmiStack> getOutputs() {
			return outputs;
		}

		@Override
		public int getDisplayWidth() {
			return 152;
		}

		@Override
		public int getDisplayHeight() {
			int rows = Math.max(1, (outputs.size() + 7) / 8);
			return 24 + rows * 18;
		}

		@Override
		public void addWidgets(WidgetHolder widgets) {
			widgets.addSlot(crateIcon, 2, 2);
			widgets.addText(Text.translatable(pool.getTranslationKey()), 24, 6, 0xFFDDDDDD, true);
			for (int i = 0; i < outputs.size(); i++) {
				int col = i % 8;
				int row = i / 8;
				widgets.addSlot(outputs.get(i), 4 + col * 18, 22 + row * 18).recipeContext(this);
			}
		}
	}
}
