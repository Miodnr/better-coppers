# Mios Coppergolems Automatisation

**An add-on for [Mios Coppergolems](../README.md): conveyor belts, funnels, chutes, presses, saws and grabber arms, all worked by copper golems.**

It feels a bit like Create, but there is no power network, no water wheels and no rotation to route. **Every machine has a crank, and a copper golem has to turn it.** No golem, no movement. Your factory runs on golems: they walk to their machines in the morning, crank away all day, and go to their charge pads at night.

---

## The operator

1. Craft a **Copper Wrench** and right-click a copper golem with it.
2. Link the golem to a machine: sneak + right-click the golem with an empty hand, then right-click the machine (within 32 blocks).

The golem is now an **operator**. Whenever its machine has work, it walks over (within about 2 blocks) and turns the crank. You'll see its arm reach out and hear the crank clank. With nothing to do, it potters about nearby.

Operators pay the usual Mios Coppergolems running costs:
- **Energy:** one point per second of cranking.
- **Charging:** they recharge on a redstone pad near their machine.
- **Rest:** they stop at night.
- **Rust:** rusty golems crank more slowly.

One golem works one machine. To get the wrench back, right-click the golem with an empty hand.

---

## Machines

| Block | Needs an operator? | What it does |
|---|---|---|
| **Conveyor Belt** | via its Belt Drive | Carries items, mobs and players along at a brisk walk. Items queue up behind anything that stands still. |
| **Belt Drive** | yes | Runs every belt line it touches, up to 64 belts. |
| **Funnel** | no | Pulls items into the container behind it, or pushes stacks out. Has a filter. |
| **Chute** | no | Drops items straight down, a whole stack at a time. |
| **Press** | yes | Hangs over a belt and flattens ingots into sheets. |
| **Saw** | yes | Hangs over a belt and cuts logs into planks, or stone into whatever its filter shows. |
| **Grabber Arm** | yes | Swings items from its input spots to its output spots. |

### Conveyor Belt and Belt Drive
- Belts run the way you face when you place them. Belts that run into each other form one line, so corners work.
- Put a **Belt Drive** next to any belt of a line and give it an operator. As long as the golem cranks, the whole line runs: the drive's gear turns and the belt surface moves.
- Belts carry items, mobs and players. Sneak to stand still on a running belt.
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
Place it **one block above a belt**, just like the press. It catches items the same way.
- **Logs and wood** become planks: half again as many as crafting gives (one log makes 6 planks).
- **Stone-like blocks** are cut with stonecutter recipes, into whatever its **filter** shows. Right-click it with, say, a stone slab. Without a filter it only cuts wood.

### Grabber Arm
A copper arm on a turntable. It reaches 4 blocks sideways and 3 up or down, and swings up to 16 items at a time from its inputs to its outputs.
1. Right-click the arm with the **wrench**.
2. Right-click blocks with the wrench to mark spots: **once** = input (blue dust), **twice** = output (orange dust), **three times** = forget.
3. Right-click the arm again when you're done.

A spot can be a container (chest, barrel, chute…) or any other block. On other blocks it grabs items lying there, or puts them down on top, onto a belt for example. It takes inputs in turn and only fetches what some output has room for.

---

## Example: an iron sheet line
```
 [chest]→[funnel, pushing]→ belt → belt → belt → belt → belt →[funnel]→[chest]
                             ↑               ↑
                        [Belt Drive]      [Press] (one block above the belt)
                        + operator        + operator
```
Put iron ingots in the first chest, and iron sheets come out in the last one, as long as two golems keep cranking.

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

You need the press before you can build a saw or an arm, since both are made with sheets.

---

## For developers
The add-on hooks into Mios Coppergolems through its small API (`dev.mioscoppergolems.api`):
- `MiosCoppergolemsApi.registerJob(GolemJob)`: the operator job (status message, running costs, particle line to its workplace).
- `registerBehavior(...)`: the operator's brain behaviour.
- `registerLinkTarget(...)`: linking golems to machines.

Moving parts are drawn by block entity renderers from the models of a hidden `machine_part` block, so they mostly use vanilla copper, iron and stonecutter textures. The few own textures come from `tools/AutoTexGen.java`; models, loot tables and recipes come from `tools/gen_automatisation_resources.py`.

Build and run from the repository root:
```powershell
.\gradlew.bat :automatisation:runClient   # game with both mods
.\gradlew.bat :automatisation:build       # jar in automatisation/build/libs/
```
