package com.tarkovcraft.ux;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tarkovcraft.ammo.AmmoTier;
import com.tarkovcraft.ammo.TaCZCaliberCatalog;
import com.tarkovcraft.entity.AirdropEntity;
import com.tarkovcraft.health.BodyPart;
import com.tarkovcraft.health.BodyPartHealth;
import com.tarkovcraft.item.MedicalType;
import com.tarkovcraft.item.TarkovStimulatorItem;
import com.tarkovcraft.registry.ModBlocks;
import com.tarkovcraft.registry.ModEntities;
import com.tarkovcraft.registry.ModItems;
import net.minecraft.Bootstrap;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.NbtCompound;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UxPolishV2Test {

    @BeforeAll
    static void setupMinecraft() {
        SharedConstants.createGameVersion();
        Bootstrap.initialize();
        ModBlocks.init();
        ModItems.init();
        ModEntities.init();
    }

    @Test
    void testCaliberAllowedTiersAndClamping() {
        TaCZCaliberCatalog.CaliberSpec shotgun = TaCZCaliberCatalog.getSpecOrDynamic("tacz:12g");
        assertTrue(shotgun.isTierAllowed(AmmoTier.HP));
        assertTrue(shotgun.isTierAllowed(AmmoTier.FMJ));
        assertTrue(shotgun.isTierAllowed(AmmoTier.AP));
        assertFalse(shotgun.isTierAllowed(AmmoTier.IGN));
        assertFalse(TaCZCaliberCatalog.isTierAllowed("tacz:12g", AmmoTier.IGN));
        assertEquals(AmmoTier.AP, shotgun.clampTier(AmmoTier.IGN));

        for (String heavy : List.of("tacz:50bmg", "tacz:338")) {
            TaCZCaliberCatalog.CaliberSpec spec = TaCZCaliberCatalog.getSpecOrDynamic(heavy);
            assertFalse(spec.isTierAllowed(AmmoTier.HP), heavy + " should disallow HP");
            assertTrue(spec.isTierAllowed(AmmoTier.FMJ), heavy + " should allow FMJ");
            assertTrue(spec.isTierAllowed(AmmoTier.AP), heavy + " should allow AP");
            assertTrue(spec.isTierAllowed(AmmoTier.IGN), heavy + " should allow IGN");
            assertEquals(AmmoTier.FMJ, spec.clampTier(AmmoTier.HP));
        }

        for (String pistol : List.of("tacz:9mm", "tacz:45acp")) {
            TaCZCaliberCatalog.CaliberSpec spec = TaCZCaliberCatalog.getSpecOrDynamic(pistol);
            assertTrue(spec.isTierAllowed(AmmoTier.HP));
            assertTrue(spec.isTierAllowed(AmmoTier.FMJ));
            assertTrue(spec.isTierAllowed(AmmoTier.AP));
            assertFalse(spec.isTierAllowed(AmmoTier.IGN), pistol + " should disallow IGN");
            assertEquals(AmmoTier.AP, spec.clampTier(AmmoTier.IGN));
        }

        for (String full : List.of("tacz:57x28", "tacz:46x30", "tacz:545x39", "tacz:556x45", "tacz:308")) {
            TaCZCaliberCatalog.CaliberSpec spec = TaCZCaliberCatalog.getSpecOrDynamic(full);
            for (AmmoTier tier : AmmoTier.values()) {
                assertTrue(spec.isTierAllowed(tier), full + " should allow " + tier);
            }
        }
    }

    @Test
    void testMedicalUseDurationsAndHealthTimerNbt() {
        for (MedicalType type : MedicalType.values()) {
            assertTrue(type.getUseDurationTicks() >= 40,
                    "MedicalType " + type.name() + " duration must be >= 40 ticks");
            if (type.isSurgicalKit()) {
                assertTrue(type.getUseDurationTicks() >= 100,
                        "Surgical kit " + type.name() + " duration must be >= 100 ticks");
            }
            if (type.getCapacityMode() == MedicalType.CapacityMode.HP_POOL) {
                assertTrue(type.getUseDurationTicks() >= 45,
                        "Medkit " + type.name() + " duration must be >= 45 ticks");
            }
        }

        assertTrue(TarkovStimulatorItem.USE_DURATION_TICKS >= 30);

        BodyPartHealth health = new BodyPartHealth();
        health.setActiveMedUse("tarkovcraft:salewa_medkit", 24, 60, BodyPart.THORAX);
        assertTrue(health.isMedUseActive());
        assertEquals(0.4f, health.getActiveMedProgress(), 0.001f);
        assertEquals(1.8f, health.getActiveMedRemainingSeconds(), 0.01f);
        assertEquals("tarkovcraft:salewa_medkit", health.getActiveMedItemId());
        assertEquals(BodyPart.THORAX, health.getActiveMedTargetPart());

        NbtCompound nbt = health.writeNbt();
        BodyPartHealth restored = new BodyPartHealth();
        restored.readNbt(nbt);
        assertTrue(restored.isMedUseActive());
        assertEquals("tarkovcraft:salewa_medkit", restored.getActiveMedItemId());
        assertEquals(BodyPart.THORAX, restored.getActiveMedTargetPart());
        assertEquals(0.4f, restored.getActiveMedProgress(), 0.001f);

        restored.resetFull();
        assertFalse(restored.isMedUseActive());
        assertEquals("", restored.getActiveMedItemId());
        assertNull(restored.getActiveMedTargetPart());
        assertEquals(0.0f, restored.getActiveMedProgress(), 0.0001f);
    }

    @Test
    void testAirdropSlowDescentConstants() {
        assertTrue(Math.abs(AirdropEntity.DESCENT_SPEED) <= 0.08,
                "Airdrop descent speed should be slow (<= 0.08)");
        assertTrue(AirdropEntity.MAX_AIR_TICKS >= 3000,
                "Airdrop max air ticks should be >= 3000");
    }

    @Test
    void testUnifiedHungerScaleFormula() {
        int hunger = 7;
        float satMod = 0.75f;
        int energy = hunger * 5;
        int saturation = Math.round(hunger * satMod * 10.0f);
        assertEquals(35, energy);
        assertEquals(53, saturation);
    }

    @Test
    void testAllMeleeModelsHaveDisplayTransformsAndLangKeys() throws Exception {
        JsonObject catalog = readJson("/data/tarkovcraft/catalog/items_catalog.json");
        JsonArray items = catalog.getAsJsonArray("items");
        List<String> meleeIds = new ArrayList<>();
        for (JsonElement element : items) {
            JsonObject obj = element.getAsJsonObject();
            if ("melee".equals(obj.get("category").getAsString())) {
                meleeIds.add(obj.get("id").getAsString());
            }
        }
        assertEquals(27, meleeIds.size(), "Expected 27 melee items in items_catalog.json");

        List<String> requiredDisplayKeys = List.of(
                "firstperson_righthand",
                "thirdperson_righthand",
                "firstperson_lefthand",
                "thirdperson_lefthand"
        );
        for (String meleeId : meleeIds) {
            JsonObject model = readJson("/assets/tarkovcraft/models/item/" + meleeId + ".json");
            assertTrue(model.has("display") && model.get("display").isJsonObject(),
                    meleeId + " model missing display object");
            JsonObject display = model.getAsJsonObject("display");
            for (String key : requiredDisplayKeys) {
                assertTrue(display.has(key) && display.get(key).isJsonObject(),
                        meleeId + " display missing " + key);
            }
        }

        JsonObject enUs = readJson("/assets/tarkovcraft/lang/en_us.json");
        JsonObject ruRu = readJson("/assets/tarkovcraft/lang/ru_ru.json");
        List<String> requiredLangKeys = List.of(
                "tooltip.tarkovcraft.food_stats",
                "ui.tarkovcraft.inspect_food",
                "tooltip.tarkovcraft.gun_caliber",
                "tooltip.tarkovcraft.gun_ammo_count",
                "tooltip.tarkovcraft.gun_ammo_count_chamber",
                "tooltip.tarkovcraft.gun_fire_mode",
                "tooltip.tarkovcraft.gun_loaded_tier_full",
                "tooltip.tarkovcraft.gun_allowed_tiers",
                "tooltip.tarkovcraft.ammo_shotgun_hp",
                "tooltip.tarkovcraft.ammo_shotgun_fmj",
                "tooltip.tarkovcraft.ammo_shotgun_ap",
                "tooltip.tarkovcraft.med_use_time",
                "message.tarkovcraft.med_busy",
                "message.tarkovcraft.med_cancelled",
                "ui.tarkovcraft.med_using",
                "tooltip.tarkovcraft.quick_heal"
        );
        for (String key : requiredLangKeys) {
            assertTrue(enUs.has(key), "en_us.json missing " + key);
            assertTrue(ruRu.has(key), "ru_ru.json missing " + key);
        }
    }

    private static JsonObject readJson(String resourcePath) throws Exception {
        try (InputStream in = UxPolishV2Test.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "Missing resource: " + resourcePath);
            try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
