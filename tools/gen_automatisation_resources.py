"""Writes blockstates, models, item definitions, loot tables, recipes and tags for the Automatisation add-on."""
import json
import os
import sys

ROOT = sys.argv[1]  # automatisation/src/main/resources
NS = "mioscoppergolems_automatisation"
A = os.path.join(ROOT, "assets", NS)
D = os.path.join(ROOT, "data", NS)


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def tex(name):
    return name if ":" in name else f"{NS}:block/{name}"


def box(frm, to, textures, uv=None, rotation=None):
    """An element; textures is a dict face -> texture variable or a single variable for all faces."""
    faces = {}
    for face in ["north", "south", "east", "west", "up", "down"]:
        t = textures if isinstance(textures, str) else textures.get(face)
        if t is None:
            continue
        entry = {"texture": t}
        if uv and face in uv:
            entry["uv"] = uv[face]
        faces[face] = entry
    element = {"from": frm, "to": to, "faces": faces}
    if rotation:
        element["rotation"] = rotation
    return element


def model(name, textures, elements, parent="minecraft:block/block"):
    write(os.path.join(A, "models", "block", name + ".json"), {"parent": parent, "textures": textures, "elements": elements})


HORIZONTAL_Y = {"north": 0, "east": 90, "south": 180, "west": 270}

# --- conveyor belt ---
for running in (False, True):
    name = "conveyor_belt_running" if running else "conveyor_belt"
    side_uv = {f: [0, 12, 16, 16] for f in ["north", "south", "east", "west"]}
    model(
        name,
        {"particle": tex("conveyor_belt_side"), "belt": tex("conveyor_belt_moving" if running else "conveyor_belt"), "side": tex("conveyor_belt_side"), "rail": "minecraft:block/cut_copper"},
        [
            box([1, 0, 0], [15, 4, 16], {"up": "#belt", "down": "#side", "north": "#side", "south": "#side", "east": "#side", "west": "#side"}, uv={**side_uv, "up": [1, 0, 15, 16]}),
            box([0, 0, 0], [1, 5, 16], "#rail"),
            box([15, 0, 0], [16, 5, 16], "#rail"),
        ],
    )
write(
    os.path.join(A, "blockstates", "conveyor_belt.json"),
    {"variants": {f"facing={f},running={str(r).lower()}": {"model": f"{NS}:block/conveyor_belt" + ("_running" if r else ""), **({"y": y} if y else {})} for f, y in HORIZONTAL_Y.items() for r in (False, True)}},
)

# --- belt drive: copper housing, the gear and crank are drawn by the renderer ---
model(
    "belt_drive",
    {"particle": "minecraft:block/cut_copper", "side": "minecraft:block/cut_copper", "top": "minecraft:block/copper_block", "iron": "minecraft:block/iron_block"},
    [
        box([1, 0, 1], [15, 10, 15], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"}),
        box([6, 10, 6], [10, 12, 10], "#iron"),
        box([6, 4, 0], [10, 8, 1], "#iron"),
    ],
)


def machine_states(name):
    write(
        os.path.join(A, "blockstates", name + ".json"),
        {"variants": {f"facing={f},running={str(r).lower()}": {"model": f"{NS}:block/{name}", **({"y": y} if y else {})} for f, y in HORIZONTAL_Y.items() for r in (False, True)}},
    )


machine_states("belt_drive")

# --- press and saw: a housing on four posts, head or blade drawn by the renderer ---
def housing(name, side, top, bottom):
    model(
        name,
        {"particle": side, "side": side, "top": top, "bottom": bottom, "post": "minecraft:block/copper_block"},
        [
            box([0, 10, 0], [16, 16, 16], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#bottom"}),
            box([0, 0, 0], [2, 10, 2], "#post"),
            box([14, 0, 0], [16, 10, 2], "#post"),
            box([0, 0, 14], [2, 10, 16], "#post"),
            box([14, 0, 14], [16, 10, 16], "#post"),
        ],
    )
    machine_states(name)


housing("press", "minecraft:block/cut_copper", "minecraft:block/copper_block", "minecraft:block/iron_block")
housing("saw", "minecraft:block/exposed_cut_copper", "minecraft:block/exposed_copper", "minecraft:block/stonecutter_bottom")

# --- grabber arm base ---
model(
    "grabber_arm",
    {"particle": "minecraft:block/cut_copper", "side": "minecraft:block/cut_copper", "top": "minecraft:block/copper_block", "iron": "minecraft:block/iron_block"},
    [
        box([2, 0, 2], [14, 4, 14], {"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"}),
        box([4, 4, 4], [12, 6, 12], "#iron"),
    ],
)
machine_states("grabber_arm")

# --- golem assembler: a crafter-like workshop; the crank is drawn by the renderer ---
model(
    "golem_assembler",
    {
        "particle": "minecraft:block/crafter_north",
        "north": "minecraft:block/crafter_north",
        "south": "minecraft:block/crafter_south",
        "east": "minecraft:block/crafter_east",
        "west": "minecraft:block/crafter_west",
        "up": "minecraft:block/crafter_top",
        "down": "minecraft:block/crafter_bottom",
    },
    [box([0, 0, 0], [16, 16, 16], {f: "#" + f for f in ["north", "south", "east", "west", "up", "down"]})],
)
machine_states("golem_assembler")

# --- charging station: a gilded pad ---
write(
    os.path.join(A, "models", "block", "charging_station.json"),
    {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": "minecraft:block/copper_bulb_lit_powered", "side": "minecraft:block/chiseled_copper", "bottom": "minecraft:block/copper_block"}},
)
write(os.path.join(A, "blockstates", "charging_station.json"), {"variants": {"": {"model": f"{NS}:block/charging_station"}}})

# --- funnel: built with the mouth up, turned like a barrel ---
for pushing in (False, True):
    name = "funnel_pushing" if pushing else "funnel"
    model(
        name,
        {"particle": "minecraft:block/cut_copper", "copper": "minecraft:block/cut_copper", "mouth": tex("funnel_push" if pushing else "funnel_pull")},
        [
            box([0, 0, 0], [16, 4, 16], {"up": "#mouth", "down": "#copper", "north": "#copper", "south": "#copper", "east": "#copper", "west": "#copper"}),
            box([0, 4, 0], [16, 8, 2], "#copper"),
            box([0, 4, 14], [16, 8, 16], "#copper"),
            box([0, 4, 2], [2, 8, 14], "#copper"),
            box([14, 4, 2], [16, 8, 14], "#copper"),
        ],
    )
FACING_ROT = {"up": {}, "down": {"x": 180}, "north": {"x": 90}, "east": {"x": 90, "y": 90}, "south": {"x": 90, "y": 180}, "west": {"x": 90, "y": 270}}
write(
    os.path.join(A, "blockstates", "funnel.json"),
    {"variants": {f"facing={f},pushing={str(p).lower()}": {"model": f"{NS}:block/funnel" + ("_pushing" if p else ""), **rot} for f, rot in FACING_ROT.items() for p in (False, True)}},
)

# --- chute: a hollow copper shaft ---
model(
    "chute",
    {"particle": "minecraft:block/cut_copper", "copper": "minecraft:block/cut_copper", "dark": "minecraft:block/exposed_cut_copper"},
    [
        box([1, 0, 1], [15, 16, 3], {"north": "#copper", "south": "#dark", "east": "#copper", "west": "#copper", "up": "#copper", "down": "#copper"}),
        box([1, 0, 13], [15, 16, 15], {"north": "#dark", "south": "#copper", "east": "#copper", "west": "#copper", "up": "#copper", "down": "#copper"}),
        box([1, 0, 3], [3, 16, 13], {"west": "#copper", "east": "#dark", "up": "#copper", "down": "#copper"}),
        box([13, 0, 3], [15, 16, 13], {"east": "#copper", "west": "#dark", "up": "#copper", "down": "#copper"}),
    ],
)
write(os.path.join(A, "blockstates", "chute.json"), {"variants": {"": {"model": f"{NS}:block/chute"}}})

# --- moving parts, drawn by the block entity renderers ---
parts = {
    "crank": (
        {"particle": "minecraft:block/iron_block", "iron": "minecraft:block/iron_block", "wood": "minecraft:block/stripped_oak_log"},
        # Axle along -z from the centre of the north face; arm up; handle pointing out.
        [box([7, 7, -2], [9, 9, 0], "#iron"), box([7, 7, -3], [9, 14, -2], "#iron"), box([7, 12, -7], [9, 14, -3], "#wood")],
    ),
    "gear": (
        {"particle": "minecraft:block/copper_block", "plate": "minecraft:block/cut_copper", "teeth": "minecraft:block/copper_block"},
        [
            box([3, 0, 3], [13, 2, 13], "#plate"),
            box([7, 0, 0], [9, 2, 16], "#teeth"),
            box([0, 0, 7], [16, 2, 9], "#teeth"),
            box([7, 0, 0], [9, 2, 16], "#teeth", rotation={"angle": 45, "axis": "y", "origin": [8, 1, 8]}),
            box([0, 0, 7], [16, 2, 9], "#teeth", rotation={"angle": 45, "axis": "y", "origin": [8, 1, 8]}),
        ],
    ),
    "press_head": (
        {"particle": "minecraft:block/piston_top", "face": "minecraft:block/piston_top", "iron": "minecraft:block/iron_block"},
        [box([3, 0, 3], [13, 4, 13], {"down": "#face", "up": "#iron", "north": "#iron", "south": "#iron", "east": "#iron", "west": "#iron"}), box([7, 4, 7], [9, 16, 9], "#iron")],
    ),
    "saw_blade": (
        {"particle": "minecraft:block/stonecutter_saw", "saw": "minecraft:block/stonecutter_saw", "iron": "minecraft:block/iron_block"},
        [
            {"from": [1, 0, 8], "to": [15, 7, 8], "faces": {"north": {"uv": [1, 16, 15, 9], "texture": "#saw"}, "south": {"uv": [15, 16, 1, 9], "texture": "#saw"}}},
            box([7, 7, 7], [9, 10, 9], "#iron"),
        ],
    ),
    "arm_turret": (
        {"particle": "minecraft:block/copper_block", "copper": "minecraft:block/copper_block", "iron": "minecraft:block/iron_block"},
        [box([4, 0, 4], [12, 3, 12], "#copper"), box([6, 3, 6], [10, 7, 10], "#iron")],
    ),
    "arm_segment": (
        {"particle": "minecraft:block/cut_copper", "copper": "minecraft:block/cut_copper", "iron": "minecraft:block/iron_block"},
        [box([7, 0, 7], [9, 16, 9], {"north": "#copper", "south": "#copper", "east": "#copper", "west": "#copper", "up": "#iron", "down": "#iron"})],
    ),
    "arm_claw": (
        {"particle": "minecraft:block/iron_block", "iron": "minecraft:block/iron_block", "copper": "minecraft:block/copper_block"},
        [box([6, 4, 6], [10, 7, 10], "#copper"), box([6, 0, 7], [7, 4, 9], "#iron"), box([9, 0, 7], [10, 4, 9], "#iron"), box([7, 0, 6], [9, 4, 7], "#iron"), box([7, 0, 9], [9, 4, 10], "#iron")],
    ),
}
for part, (textures, elements) in parts.items():
    model("part_" + part, textures, elements)
write(os.path.join(A, "blockstates", "machine_part.json"), {"variants": {f"part={p}": {"model": f"{NS}:block/part_{p}"} for p in parts}})

# --- items ---
BLOCKS = ["conveyor_belt", "belt_drive", "funnel", "chute", "press", "saw", "grabber_arm", "golem_assembler", "charging_station"]
for b in BLOCKS:
    write(os.path.join(A, "items", b + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:block/{b}"}})
for i in ["copper_wrench", "copper_sheet", "iron_sheet", "gold_sheet", "copper_gear", "gold_gear", "iron_plating", "copper_coating", "gold_gearwork", "copper_satchel", "cart_extension"]:
    write(os.path.join(A, "items", i + ".json"), {"model": {"type": "minecraft:model", "model": f"{NS}:item/{i}"}})
    parent = "minecraft:item/handheld" if i == "copper_wrench" else "minecraft:item/generated"
    write(os.path.join(A, "models", "item", i + ".json"), {"parent": parent, "textures": {"layer0": f"{NS}:item/{i}"}})

# --- loot tables: every block drops itself ---
for b in BLOCKS:
    write(
        os.path.join(D, "loot_table", "blocks", b + ".json"),
        {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}], "conditions": [{"condition": "minecraft:survives_explosion"}]}]},
    )

# --- recipes ---
def shaped(name, pattern, key, count=1, category="redstone"):
    write(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern, "result": {"id": f"{NS}:{name}", "count": count}})


shaped("copper_wrench", [" C", "CS", "S "], {"C": "minecraft:copper_ingot", "S": "minecraft:stick"}, category="equipment")
shaped("conveyor_belt", ["KKK", "CRC"], {"K": "minecraft:dried_kelp", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"}, count=6)
shaped("belt_drive", [" S ", "CGC", "CCC"], {"S": "minecraft:stick", "C": "minecraft:copper_ingot", "G": "minecraft:copper_block"})
shaped("funnel", ["C C", "C C", " K "], {"C": "minecraft:copper_ingot", "K": "minecraft:dried_kelp"}, count=2)
shaped("chute", ["C C", "C C", "C C"], {"C": "minecraft:copper_ingot"}, count=4)
shaped("press", ["CBC", "C C", "PIP"], {"C": "minecraft:copper_ingot", "B": "minecraft:copper_block", "P": "minecraft:piston", "I": "minecraft:iron_block"})
shaped("saw", ["CCC", "C C", " X "], {"C": f"{NS}:copper_sheet", "X": "minecraft:stonecutter"})
shaped("grabber_arm", [" G ", "SCS", "SBS"], {"G": "minecraft:crafter", "S": f"{NS}:iron_sheet", "C": "minecraft:copper_ingot", "B": "minecraft:copper_block"})


def shapeless(name, ingredients, count=1, category="misc", result=None):
    write(
        os.path.join(D, "recipe", name + ".json"),
        {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients, "result": {"id": result or f"{NS}:{name}", "count": count}},
    )


S = f"{NS}:"
shaped("golem_assembler", ["IXI", "GCG", "IRI"], {"I": S + "iron_sheet", "X": "minecraft:crafter", "G": S + "copper_gear", "C": "minecraft:copper_block", "R": "minecraft:redstone_block"})
shaped("charging_station", ["GRG", "CBC"], {"G": S + "gold_sheet", "R": "minecraft:redstone_block", "C": "minecraft:copper_ingot", "B": "minecraft:copper_block"})
shaped("iron_plating", ["I I", "III"], {"I": S + "iron_sheet"}, category="equipment")
shapeless("copper_coating", [S + "copper_sheet", S + "copper_sheet", S + "copper_sheet", "minecraft:honeycomb"], category="equipment")
shapeless("gold_gearwork", [S + "gold_gear", S + "gold_gear", S + "copper_gear", "minecraft:redstone"], category="equipment")
shaped("copper_satchel", ["LSL", "L L", "LLL"], {"L": "minecraft:leather", "S": S + "copper_sheet"}, category="equipment")
shaped("cart_extension", ["S S", "SCS"], {"S": S + "copper_sheet", "C": "#c:chests/wooden"}, category="misc")
# Rails from iron sheets: four sheets make as many rails as six ingots do by hand.
write(
    os.path.join(D, "recipe", "rail_from_iron_sheets.json"),
    {"type": "minecraft:crafting_shaped", "category": "misc", "key": {"S": S + "iron_sheet", "K": "minecraft:stick"}, "pattern": ["S S", "SKS"], "result": {"id": "minecraft:rail", "count": 16}},
)

# --- mining: machines need a pickaxe ---
write(os.path.join(ROOT, "data", "minecraft", "tags", "block", "mineable", "pickaxe.json"), {"values": [f"{NS}:{b}" for b in ["belt_drive", "funnel", "chute", "press", "saw", "grabber_arm", "golem_assembler", "charging_station"]]})
print("ok")
