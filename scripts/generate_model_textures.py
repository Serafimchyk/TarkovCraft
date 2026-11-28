"""Generate the equipment textures and catalog bindings. Requires Python 3 and Pillow."""

import argparse
import colorsys
import json
import math
import re
from collections import defaultdict
from functools import lru_cache
from io import BytesIO
from pathlib import Path
from statistics import median

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "src/main/resources"
CATALOG = RESOURCES / "data/tarkovcraft/catalog/items_catalog.json"
TEXTURES = RESOURCES / "assets/tarkovcraft/textures"
JAVA = ROOT / "src/main/java/com/tarkovcraft/registry/ModItems.java"
UV_LAYOUT = ROOT / "scripts/model_texture_uv.json"
SIZE = 64

MEDKITS = {"AI2_MEDKIT", "SALEWA_MEDKIT", "CAR_MEDKIT", "IFAK", "AFAK", "GRIZZLY"}
GROUND_BY_WORN = {
    "helmet_highcut": "ground_helmet_open",
    "helmet_lowcut": "ground_helmet_open",
    "helmet_visor": "ground_helmet_visor",
    "helmet_ronin": "ground_helmet_visor",
    "mask_tagilla": "ground_mask_tagilla",
    "backpack_small": "ground_backpack_small",
    "backpack_large": "ground_backpack_large",
}


def helmet(item):
    name = item["id"]
    if "tagilly" in name:
        return "mask_tagilla"
    if "ronin" in name:
        return "helmet_ronin"
    if "ushanka" in name or "shapka" in name:
        return "ushanka"
    if "kepka" in name or name.startswith(("panama_", "beret_")) or "shlyapa" in name:
        return "tactical_cap"
    zones = item.get("zones", ())
    # These shells accept a separate visor in the face slot.
    detachable_visor = name.startswith(("broneshlem_rys_t_", "broneshlem_altyn_"))
    if "HEAD_EYES" in zones and not detachable_visor:
        return "helmet_visor"
    if item.get("blocksHeadset", False) or "HEAD_EARS" in zones:
        return "helmet_lowcut"
    return "helmet_highcut"


def mask(item):
    name = item["id"]
    if name.startswith("vizor_"):
        return "visor"
    if "tagilly" in name:
        return "mask_tagilla"
    if item.get("gasProtection", False) or name.startswith(("respirator", "protivogaz")):
        return "gas_mask"
    if "ochki" in name:
        return "tactical_goggles"
    return "balaclava"


def armor(item):
    name = item["id"]
    zones = set(item.get("zones", ()))
    if zones.intersection({"NECK", "ARMPIT_LEFT", "ARMPIT_RIGHT"}):
        return "armor_heavy"
    if any(term in name for term in ("slick", "slik", "paca", "hexgrid")):
        return "armor_slick"
    return "armor_slick" if item.get("armorClass", 0) <= 2 else "armor_medium"


def rig(item):
    name = item["id"]
    if name.startswith("chekhol_dlya_broneplit_"):
        return "rig_armored"
    if name.startswith("remenno_plechevaya_"):
        return "rig_belt_rps"
    cells = sum(width * height for width, height in item.get("compartments", [[1, 2]]))
    if cells <= 8 or name.startswith(("zhilet_okhrannika_", "okhotnichiy_zhilet_")):
        return "rig_scav_vest"
    return "rig_chest_heavy"


def silhouettes(item):
    match item["category"]:
        case "helmet":
            worn = helmet(item)
        case "face_mask":
            worn = mask(item)
        case "body_armor":
            return armor(item), "ground_armor"
        case "rig":
            return rig(item), "ground_rig"
        case "headset":
            worn = "headset_active"
        case "backpack":
            cells = item.get("gridWidth", 2) * item.get("gridHeight", 2)
            worn = "backpack_small" if cells <= 20 else "backpack_large"
        case "case":
            return None, "ground_case_medkit"
        case "medical" if item.get("medPreset") in MEDKITS:
            return None, "ground_case_medkit"
        case _:
            return None, None
    # The pack has no dedicated dropped geometry for these accessories.
    return worn, GROUND_BY_WORN.get(worn, worn)


def uv_pixels(cuboids):
    pixels = set()
    for u, v, width, height, depth in cuboids:
        # Minecraft's Box-UV layout places two cap faces above the four side faces.
        rectangles = [
            (u + depth, v, 2 * width, depth),
            (u, v + depth, 2 * (width + depth), height),
        ]
        for x, y, w, h in rectangles:
            if x < 0 or y < 0 or x + w > SIZE or y + h > SIZE:
                raise ValueError(f"Box UV leaves the 64×64 atlas at {x}, {y}, {w}, {h}")
            pixels.update((px, py) for py in range(y, y + h) for px in range(x, x + w))
    return pixels


def rgba(path):
    with Image.open(path) as image:
        return image.convert("RGBA")


def hsv(red, green, blue):
    return colorsys.rgb_to_hsv(red / 255, green / 255, blue / 255)


def icon_color(icon):
    width, height = icon.size
    clusters = defaultdict(list)
    for y in range(height):
        for x in range(width):
            red, green, blue, alpha = icon.getpixel((x, y))
            if alpha < 128:
                continue
            hue, saturation, value = hsv(red, green, blue)
            # Exclude the thin dark or gray outline before counting the interior.
            if saturation < 0.24 or value < 0.25:
                if any(
                    not (0 <= nx < width and 0 <= ny < height)
                    or icon.getpixel((nx, ny))[3] < 128
                    for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1))
                ):
                    continue
            if saturation < 0.18 or value < 0.15:
                key = ("neutral", min(int(value * 3), 2))
            else:
                key = ("hue", round(hue * 12) % 12)
            clusters[key].append((hue, saturation, value))
    if not clusters:
        return None

    def score(entry):
        key, colors = entry
        return len(colors) * (1.0 if key[0] == "hue" else 0.70)

    _, cluster = max(clusters.items(), key=score)
    hue = math.atan2(
        sum(math.sin(h * math.tau) for h, _, _ in cluster),
        sum(math.cos(h * math.tau) for h, _, _ in cluster),
    ) / math.tau % 1.0
    return hue, median(s for _, s, _ in cluster), median(v for _, _, v in cluster)


# These icons have large secondary areas or highlights that can dominate the histogram.
# Values describe the visible shell or fabric, with shading still supplied by the pack.
MATERIAL_COLORS = {
    "bronezhilet_mf_untar_5ab8e4": (43, 87, 119),
    "shlem_untar_5aa7d0": (77, 137, 170),
    "turisticheskiy_ryukzak_piligrim_59e763": (104, 97, 169),
    "bronezhilet_bnti_gzhel_k_5ab8e7": (205, 209, 207),
    "press_armor": (49, 61, 103),
    "bronezhilet_npp_klass_korund_vm_cher_5f5f41": (42, 44, 47),
}


@lru_cache
def template(silhouette):
    source = "helmet_visor" if silhouette == "visor" else silhouette
    folder = "ground" if source.startswith("ground_") else "armor"
    base = rgba(TEXTURES / "models" / folder / f"{source}.png")
    if base.size != (SIZE, SIZE):
        raise ValueError(f"Expected a 64×64 texture for {source}")
    region = UV_REGIONS[source]
    allowed = uv_pixels(region["material"]) - uv_pixels(region["details"])
    # A standalone visor is entirely lens and hardware.
    editable = [] if silhouette == "visor" else [xy for xy in allowed if base.getpixel(xy)[3] == 255]
    if silhouette != "visor" and not editable:
        raise ValueError(f"Empty material mask for {silhouette}")
    return base, editable


def recolor(silhouette, target):
    base, editable = template(silhouette)
    result = base.copy()
    if target is None or not editable:
        return result
    hue, saturation, value = target
    pixels = [(xy, hsv(*base.getpixel(xy)[:3])) for xy in editable]
    reference = median(original[2] for _, original in pixels)
    for xy, (_, _, old_value) in pixels:
        shade = min(1, max(0, value * (old_value / max(reference, 0.05)) ** 0.85))
        red, green, blue = colorsys.hsv_to_rgb(hue, saturation, shade)
        result.putpixel(xy, (round(red * 255), round(green * 255), round(blue * 255), 255))
    return result


def hardcoded_bindings():
    pattern = r'register\("([a-z0-9_]+)", (null|"[a-z_]+"), "([a-z_]+)",'
    for item_id, worn, ground in re.findall(pattern, JAVA.read_text(encoding="utf-8")):
        yield item_id, worn.strip('"') if worn != "null" else None, ground


def generate(check):
    catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
    bindings = {}
    for item in catalog["items"]:
        item_id = item["id"]
        worn, ground = silhouettes(item)
        for key in ("modelSilhouette", "groundSilhouette", "modelTexture"):
            item.pop(key, None)
        if worn:
            item["modelSilhouette"] = worn
        if ground:
            item["groundSilhouette"] = ground
        if worn or ground:
            item["modelTexture"] = f"tarkovcraft:{item_id}"
            bindings[item_id] = (worn, ground)
    catalog_bytes = (json.dumps(catalog, ensure_ascii=False, indent=2) + "\n").encode("utf-8")
    catalog_current = CATALOG.read_text(encoding="utf-8").encode("utf-8")
    stale = []
    if catalog_current != catalog_bytes:
        if check:
            stale.append(CATALOG)
        else:
            CATALOG.write_bytes(catalog_bytes)

    bindings.update((item_id, (worn, ground)) for item_id, worn, ground in hardcoded_bindings())
    count = 0
    for item_id, (worn, ground) in bindings.items():
        icon_path = TEXTURES / "item" / f"{item_id}.png"
        if item_id in MATERIAL_COLORS:
            target = hsv(*MATERIAL_COLORS[item_id])
        else:
            target = icon_color(rgba(icon_path)) if icon_path.is_file() else None
        for folder, silhouette in (("armor", worn), ("ground", ground)):
            if not silhouette:
                continue
            path = TEXTURES / "models" / folder / f"{item_id}.png"
            image = recolor(silhouette, target)
            if path.is_file() and rgba(path).tobytes() == image.tobytes():
                count += 1
                continue
            if check:
                stale.append(path)
            else:
                output = BytesIO()
                image.save(output, format="PNG", optimize=True)
                path.write_bytes(output.getvalue())
                count += 1
    if stale:
        paths = ", ".join(str(path.relative_to(ROOT)) for path in stale[:5])
        raise SystemExit(f"{len(stale)} generated files are missing or stale: {paths}")
    print(f"{len(catalog['items'])} catalog entries, {len(bindings)} modelled items, {count} item textures")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Check generated files without writing them")
    args = parser.parse_args()
    UV_REGIONS = json.loads(UV_LAYOUT.read_text(encoding="utf-8"))
    for name in UV_REGIONS:
        template(name)
    generate(args.check)
