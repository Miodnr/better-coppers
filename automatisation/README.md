# Mios Coppergolems Automatisation

**An add-on for [Mios Coppergolems](../README.md): a golem-run factory of conveyor belts, presses, saws and grabber arms, whose products go back into your golems.**

It feels a bit like Create, but there is no power network, no water wheels and no rotation to route. **Every machine has a crank, and a copper golem has to turn it.** No golem, no movement. Your golems walk to their machines in the morning, crank away all day, repair them when they jam, and go to their charge pads at night.

The add-on adds its own chapters to the **Golem Handbook** and its own **advancement tab**, which leads you from the first wrench to a golem with every upgrade.

---

## What the factory is for

What the factory makes goes back into the colony:

| You make… | …from | …for |
|---|---|---|
| **Iron Plating** | iron sheets | Golem armour: soaks up most of each monster hit |
| **Copper Coating** | copper sheets + honeycomb | No rusting from work for about three days |
| **Gold Gearwork** | gold gears + a copper gear + redstone | Golem walks and works 30 % faster for a while |
| **Copper Satchel** | leather + a copper sheet | Sorters and couriers carry 32 items per trip instead of 16 |
| **Cart Extension** | copper sheets + a chest | Turns a copper chest minecart into a double chest (54 slots) |
| **Charging Station** | gold sheets + redstone block + copper | Golems recharge without a redstone clock, twice as fast as a pad on the fastest clock |
| **Rails** | 4 iron sheets + a stick → 16 rails | Cheap rails for mining golems (by hand: 6 ingots) |
| **Fences, slabs, stairs** | planks through the saw | Supports for mining golems, building material |
| **Paper** | sugar cane through the press | Mineshaft plans |
| **New copper golems** | the **Golem Assembler** | A growing workforce |

The machines themselves wear out and are repaired with sheets, so every factory also has to feed itself.

---

## The operator

1. Craft a **Copper Wrench** and right-click a copper golem with it.
2. Link the golem to a machine: sneak + right-click the golem with an empty hand, then right-click the machine (within 32 blocks).

The golem is now an **operator**. Whenever its machine has work, it walks over (within about 2 blocks) and turns the crank. You'll see its arm reach out and hear the crank clank. With nothing to do, it potters about nearby. Copper golems walk across belts; belts don't carry them.

Operators pay the usual Mios Coppergolems running costs:
- **Energy:** one point per second of cranking.
- **Charging:** they recharge near their machine, on a Charging Pad (with a redstone clock) or a Charging Station.
- **Rest:** they stop at night.
- **Rust:** rusty golems crank more slowly.

One golem works one machine. To get the wrench back, right-click the golem with an empty hand.

### Wear and repairs
Machines wear out with use. A worn-out machine **jams**: it stops and smokes. Its operator then fetches a spare part from any chest or barrel within 8 blocks of the machine and repairs it. Without a spare part nearby, it waits.

| Machine | Lasts | Repaired with |
|---|---|---|
| Belt Drive | 20 minutes of running | Copper Sheet |
| Press, Saw | 64 stacks | Iron Sheet |
| Grabber Arm | 256 swings | Iron Sheet |
| Golem Assembler | 8 golems | Iron Sheet |

**Sneak + right-click a machine with the wrench** to see how worn it is. Keep a few sheets in a chest next to every machine.

---

## Machines

| Block | Needs an operator? | What it does |
|---|---|---|
| **Conveyor Belt** | via its Belt Drive | Carries items, mobs and players along. Items queue up behind anything that stands still. |
| **Belt Drive** | yes | Runs every belt line it touches, up to 64 belts. |
| **Funnel** | no | Pulls items into the container behind it, or pushes stacks out. Has a filter. |
| **Chute** | no | Drops items straight down, a whole stack at a time. |
| **Press** | yes | Hangs over a belt: ingots → sheets, sugar cane → paper. |
| **Saw** | yes | Hangs over a belt: sheets → gears, logs → planks, planks or stone → whatever its filter shows. |
| **Grabber Arm** | yes | Swings items from its input spots to its output spots. |
| **Golem Assembler** | yes | Builds new copper golems. |
| **Charging Station** | no | A charging pad that needs no redstone clock. |

### Conveyor Belt and Belt Drive
- Belts run the way you face when you place them. Belts that run into each other form one line, so corners work.
- Put a **Belt Drive** next to any belt of a line and give it an operator. As long as the golem cranks, the whole line runs: the drive's gear turns and the belt surface moves.
- Belts carry items, mobs and players (not copper golems). Sneak to stand still on a running belt.
- An item stops behind another item that stands still (for example one under a press), so nothing slips past a machine.
- Belts are flat. To go down, use a chute; to go up, use a grabber arm.

### Funnel
- Place it against a container. Its mouth faces away from it.
- **Pulling** (the default): it swallows every item that lands in its mouth, from a belt or thrown in.
- **Pushing:** right-click it with the wrench to switch. It pushes stacks of up to 16 out in front, onto a belt for example, but only when the spot in front is clear.
- **Filter:** right-click with any item to set it; the item isn't used up. Right-click with an empty hand to clear it. Sneak to place blocks against a funnel.

### Chute
A hollow copper shaft. Items that fall in drop down a stack at a time, much faster than a hopper:
- into the container below,
- into the next chute,
- or out of the open bottom, onto a belt or the floor.

Hoppers, funnels and arms can fill it too.

### Press
Place it **one block above a belt**. When a matching item arrives underneath, the press catches it and holds it until its operator turns the crank. Then the head comes down and the whole stack is flattened at once.

| In | Out |
|---|---|
| Copper / iron / gold ingot | Copper / iron / gold sheet |
| Sugar cane | Paper (one for one) |

### Saw
Place it **one block above a belt**, just like the press. It catches items the same way. Set its **filter** by right-clicking with an item; right-click with an empty hand to clear it.

| In | Out |
|---|---|
| Copper / gold sheet | Copper / gold gear |
| Log or wood | Planks: half again as many as crafting (6 per log) |
| Planks + filter showing that wood's fence, slab or stairs | 1 fence, 2 slabs or 1 stairs per plank |
| Anything a stonecutter cuts + filter | Whatever the filter shows |

### Grabber Arm
A copper arm on a turntable. It reaches 4 blocks sideways and 3 up or down, and swings up to 16 items at a time from its inputs to its outputs.
1. Right-click the arm with the **wrench**.
2. Right-click blocks with the wrench to mark spots: **once** = input (blue dust), **twice** = output (orange dust), **three times** = forget.
3. Right-click the arm again when you're done.

A spot can be a container (chest, barrel, chute, assembler…) or any other block. On other blocks it grabs items lying there, or puts them down on top, onto a belt for example. It takes inputs in turn and only fetches what some output has room for.

### Golem Assembler
A workshop with 9 slots. Right-click it to open it, or fill it with hoppers, funnels or a grabber arm; it only takes golem parts.
- **One golem needs:** 4 copper sheets, 2 copper gears, 1 carved pumpkin and 1 redstone dust.
- With the parts in and room in front, its operator cranks for 20 seconds and a new copper golem steps out of the front.
- New golems have no job yet. Give them one like any other golem.

### Charging Station
A gilded charging pad. It needs **no redstone clock** and charges a golem in about 12 seconds, twice as fast as a Charging Pad on the fastest clock. Put it within 16 blocks of a golem's home or machine.

---

## Golem upgrades
Right-click a copper golem with the item. Sneak + right-click the golem with an empty hand to see what it's wearing.

- **Iron Plating:** takes three quarters of every hit until it has taken 40 damage, then it's gone. Golems wearing it shrug off zombies.
- **Copper Coating:** the golem doesn't rust from work for 3000 energy, about three days of steady work.
- **Gold Gearwork:** the golem walks and works 30 % faster for 2000 energy.
- **Copper Satchel:** the golem carries twice as much per trip as a sorter or courier, for good. It drops when the golem dies.

Plating, coating and gearwork can be topped up once they've dropped below their full amount.

**Cart Extension:** right-click a copper chest minecart with it and the cart becomes a double chest (54 slots). It drops again when the cart breaks.

---

## Example: an iron sheet line
```
 [chest]→[funnel, pushing]→ belt → belt → belt → belt → belt →[funnel]→[chest]
                             ↑               ↑
                        [Belt Drive]      [Press] (one block above the belt)
                        + operator        + operator
                        [chest with copper sheets]   [chest with iron sheets]  ← spare parts
```
Put iron ingots in the first chest and iron sheets come out in the last one, as long as two golems keep cranking. Send some of the sheets back into the spare-part chests and the line repairs itself.

---

## Recipes

| Item | Recipe |
|---|---|
| Copper Wrench | 3 copper ingots + 2 sticks (diagonal) |
| Conveyor Belt ×6 | 3 dried kelp over copper ingot, redstone, copper ingot |
| Belt Drive | a stick over copper ingot, copper block, copper ingot, over 3 copper ingots |
| Funnel ×2 | 4 copper ingots in a U around dried kelp |
| Chute ×4 | 6 copper ingots in two columns |
| Press | copper ingot, copper block, copper ingot / copper ingots at the sides / piston, iron block, piston |
| Saw | 5 copper sheets around a stonecutter |
| Grabber Arm | a crafter over iron sheet, copper ingot, iron sheet over iron sheet, copper block, iron sheet |
| Golem Assembler | iron sheet, crafter, iron sheet / copper gear, copper block, copper gear / iron sheet, redstone block, iron sheet |
| Charging Station | gold sheet, redstone block, gold sheet over copper ingot, copper block, copper ingot |
| Iron Plating | 5 iron sheets (a U over a row) |
| Copper Coating | 3 copper sheets + honeycomb (shapeless) |
| Gold Gearwork | 2 gold gears + copper gear + redstone (shapeless) |
| Copper Satchel | 7 leather around a copper sheet at the top |
| Cart Extension | 4 copper sheets around a wooden chest |
| Rails ×16 | 4 iron sheets + stick (rail pattern without the bottom row) |

You need the press before anything else, since most of these are made from sheets.

---

## For developers
The add-on hooks into Mios Coppergolems through its small API (`dev.mioscoppergolems.api`):
- `registerJob`: the operator job (status message, running costs, particle line to its workplace).
- `registerBehavior`: the operator's brain behaviour, including repairs.
- `registerLinkTarget`: linking golems to machines.
- `registerModifier`: the upgrades (speed, rust, carry limit).
- `registerChargePad`: the Charging Station.
- `registerHandbookChapter` and `awardGuide`: the handbook chapters and the guide advancements.

The guide (advancements of both mods and all handbook texts, English and German) is generated by `tools/gen_guide.py`.

Moving parts are drawn by block entity renderers from the models of a hidden `machine_part` block, so they mostly use vanilla copper, iron and stonecutter textures. The few own textures come from `tools/AutoTexGen.java`; models, loot tables and recipes come from `tools/gen_automatisation_resources.py`.

Build and run from the repository root:
```powershell
.\gradlew.bat :automatisation:runClient   # game with both mods
.\gradlew.bat :automatisation:build       # jar in automatisation/build/libs/
```
