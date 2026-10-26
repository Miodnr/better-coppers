# Mios Coppergolems

**Copper Golems that do real work.** A Fabric mod for **Minecraft 26.3** that turns the copper golem from a chest shuffler into a small, slightly clumsy worker. It sorts your storage, digs mineshafts, farms and fishes, carries goods across the world, rides minecarts and boats, and gets hunted by monsters. It also needs iron golems to keep it safe, and it patches those iron golems up with your iron.

Everything is meant to feel vanilla: golems are slow, sometimes shake their head and try again, and every job costs something.

---

## At a glance

| Give the golem… | It becomes a… | What it does |
|---|---|---|
| a **bundle** | Sorter | Empties its copper chest and puts every item where it belongs |
| a **pickaxe** + **mineshaft plan** | Miner | Digs a vanilla-style mineshaft, follows ores, brings the loot home |
| a **hoe** | Farmer | Harvests and replants the fields around its chest |
| an **axe** | Forester | Fells natural trees and replants them |
| a **fishing rod** | Fisher | Fishes from the shore and brings the catch home |
| a **brush** | Animal keeper | Feeds animals so they breed |
| a **crafting table** | Crafter | Crafts whatever the item frames on its chest show |
| a **route** (two chests) | Courier | Carries everything from one chest to another, however far apart |

A golem without any of these just wanders around, like in vanilla.

On top of the jobs:
- **Copper Chest Minecart:** a golem can tow one on an iron chain to carry much more.
- **Minecarts and boats:** golems ride them on long trips.
- **Monsters and guards:** monsters hunt copper golems, iron golems guard them, and copper golems repair iron golems.
- **Armour trims:** golems can wear them, just for looks.
- **Running costs:** working golems need redstone energy, rust from work, and wear out their tools.

---

## Getting started

1. **Give a golem its job item.** Right-click the golem with it (see the table above).
2. **Give it a home.** Sneak + right-click the golem with an empty hand. It glows and tells you its job, home, energy and rust. Then right-click the **copper chest** it should belong to (within 32 blocks).
3. **Check on it.** While you sneak, copper particles run from every golem near you to its home chest. A golem that has a job but no home puffs smoke.

To move a golem to another chest, link it again.

---

## Jobs

### Sorter
- Needs a **bundle**, which it keeps in its off hand. It only empties its own home chest.
- It knows every container within 32 blocks (8 up/down) and where each item belongs:
  1. a chest that already holds that item,
  2. otherwise any barrel with room,
  3. otherwise an empty chest.
- For character, it first opens 0–3 wrong chests on each trip and shakes its head.
- It carries 16 items at a time, as in vanilla, or a whole cart load with a chained cart.
- To take the bundle back, right-click the sorter with an empty hand while it carries nothing.

### Miner
1. Make a **Survey Table** (2 paper / 2 copper ingots / 2 planks, stacked in that order).
2. Put paper in, choose a length (8–128) and take out the **mineshaft plan**.
3. Mark the entrance by using the plan on the ground (or on a wall). The tunnel heads the way you look.
4. Give the golem any **pickaxe** and the plan, then link it to a copper chest near the entrance.

The golem digs a **3×3 vanilla-style mineshaft**: plank floor over gaps, a support every 4 blocks (fence posts and a plank beam), torches on the beams, and rails laid at the end.

- **No supplies, no digging.** It must carry the rails, fences and planks for what it digs. It fetches them from its chest, or waits there until you restock.
- **Ores:** it digs out every ore it can see from the tunnel, up to 16 per vein.
- **Safety:** it plugs water and lava with cobblestone and clears gravel and sand that falls in.
- **Slow and clumsy:** it digs at about half a player's speed and skips blocks it can't reach.
- **Loot:** it carries 64 items (more with a cart), then walks home. Link a sorter to the same chest and it sorts the loot, leaving the mining supplies alone.
- **Getting things back:** right-click with an empty hand to get the pickaxe and the plan back. The plan keeps its progress.

### Farmer
- Give it a **hoe**. Its field is everything within 8 blocks of its home chest.
- It harvests ripe wheat, carrots, potatoes, beetroot and nether wart and replants them right away. Empty farmland gets what the neighbouring rows grow.
- It picks pumpkins and melons and leaves the stems, and cuts sugar cane and bamboo once they are 3 high.
- It keeps 16 of every seed for replanting and never tills new land. You build the field.

### Forester, fisher, animal keeper, crafter

| Tool | Job | What it does |
|---|---|---|
| Axe | Forester | Fells natural trees within 12 blocks (never logs you placed), collects saplings, apples and sticks, replants every stump |
| Fishing rod | Fisher | Fishes open water within 10 blocks, a catch every 20–50 seconds (fish and junk, no treasure) |
| Brush | Animal keeper | Feeds pairs of animals with food from its chest so they breed, up to 16 per kind |
| Crafting table | Crafter | Crafts what item frames on or next to its chest show, from ingredients in that chest |

### Courier
A courier carries everything from one chest to another, **however far apart they are**:
1. Sneak + right-click the golem with an empty hand.
2. Right-click a plain **chest or barrel**: it picks things up here.
3. Walk to the destination (you have 10 minutes) and right-click any chest or barrel there.

The courier takes 16 items at a time, or a whole cart load with a chained cart. While travelling it keeps the chunks around itself loaded, like a thrown ender pearl, so the route keeps working when you're far away. When it gets stuck, it shakes its head, wanders off a few blocks and tries again. If the destination is full, it waits there. Linking a home copper chest ends the route.

---

## Getting around

### Copper Chest Minecart on an iron chain
- **Recipe:** minecart + any copper chest.
- **Hand it over:** right-click the cart with an **iron chain** so it follows you, then right-click a golem.
- **Unchain it:** sneak + right-click the cart.
- **Sorters** load everything *related* to the first item they pick up: stone also brings stone slabs, stairs and bricks, but not oak logs. They then deliver it all in one trip.
- **Other jobs** fill the cart before their pouch.

### Minecarts and boats
A golem heading somewhere more than 24 blocks away looks for a ride nearby:
- **Minecart:** an empty minecart on straight rails that run the right way. The golem pushes it along at about 4× walking speed (faster on powered rails) and gets off near its goal.
- **Boat:** an empty boat on water that stretches the right way. The golem paddles straight at its goal and gets out when it runs ashore.

A golem towing a chained cart always walks.

---

## Danger and protection

- **Monsters hunt copper golems.** Every monster that goes after villagers or iron golems now goes after copper golems too: zombies, skeletons, spiders, illagers and more. A copper golem has only 12 health.
- **Iron golems guard them.** An iron golem stays near the copper golems that live around it and attacks any monster that hunts or hits one. Creepers are excepted, as in vanilla.
- **Copper golems repair iron golems.** When an iron golem nearby is badly hurt, a copper golem drops what it is doing, takes **iron ingots** from its home chest (or the nearest chest with iron) and repairs it, 25 health per ingot.

So keep some iron in your chests, and build an iron golem for every work site.

---

## Armour trims
Right-click a golem with an **armour trim smithing template**, then with a **trim material** (iron, gold, copper, emerald, amethyst…). The trim shows in the material's colour, exactly like on armour, and all 18 vanilla patterns are supported.
- The material is used up. The template stays with the golem.
- A new template or material changes the trim.
- **Shears** take the trim off and give the template back.

Trims are only for looks.

---

## Running costs
Sorters and couriers are free. Every other job costs something:

- **Energy:** work uses energy. At night, or when empty, the golem stands on a **redstone-powered block** near its home to recharge. A block of redstone works, and so does any block powered by a lever, torch or dust. Build one pad per golem.
- **Rust:** steady work makes golems rust one stage every few days. Exposed golems work at 85 % speed and weathered ones at 70 %. Fully oxidized or waxed golems don't work at all.
- **Scraping:** use an axe by hand, or put an **axe in a dispenser**: each redstone pulse scrapes the golem in front of it by one stage.
- **Tools** wear out and break.

---

## Quick reference

| You do | What happens |
|---|---|
| Right-click golem with a job item | Gives it that job |
| Sneak + right-click golem (empty hand) | Shows its status and starts linking |
| …then right-click a copper chest | Sets its home |
| …then right-click a chest/barrel, then another | Sets a courier route |
| Right-click golem (empty hand) | Takes its tool or bundle back |
| Right-click golem with template, then material | Gives it an armour trim |
| Right-click golem with shears | Removes the trim |
| Right-click cart with iron chain, then a golem | Hands the cart over |
| Sneak (just hold it) | Shows lines from golems to their homes |

---

## Add-on: Mios Coppergolems Automatisation

The [`automatisation/`](automatisation/README.md) folder holds an add-on with **conveyor belts, funnels, chutes, presses, saws and grabber arms**, in the spirit of Create but more mechanical. There is no power network: every machine has a crank that a copper golem with a **Copper Wrench** has to turn. See its [README](automatisation/README.md).

---

## Building and running

Needs **Java 25**, Fabric Loader and Fabric API.

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-25.0.2.10-hotspot"
.\gradlew.bat runClient   # start the game with the mod
.\gradlew.bat build       # mod jar in build/libs/ (and the add-on's in automatisation/build/libs/)
.\gradlew.bat :automatisation:runClient   # the game with the mod and the add-on
```

## Project structure

```
src/main/java/dev/mioscoppergolems/
  golem/     SortItemsBehavior (sorting AI), ItemFamilies (what counts as related), CartChaining, Inventories
  courier/   CourierBehavior, Couriers (routes, chunk tickets), CourierRoute
  travel/    RideBehavior (minecarts and boats)
  combat/    GolemGuards (monsters hunt copper golems, iron golems guard them) and the iron golem goals
  repair/    RepairBehavior (iron golem repairs), Errands
  trim/      GolemTrims (trim templates and materials)
  mining/    MineBehavior (mining AI), MineLayout, MinePlan, MinerData, MinerInteractions
  job/       JobBehavior (shared home-chest routine), Forest-/Fish-/Keeper-/CraftBehavior, WorkerData
  farming/   FarmBehavior, FarmerData, FarmerInteractions
  upkeep/    Upkeep (energy, rust, scraping), ChargeBehavior, AxeDispenseBehavior
  home/      Homes, HomeLinking (linking homes and routes), HomeDisplay (particle lines)
  block/, menu/  Survey Table and its menu
  entity/, item/  Copper Chest Minecart
  api/       hooks for add-ons: jobs, brain behaviours, link targets
  mixin/     swaps in the new golem AI, goal accessors
src/client/java/dev/mioscoppergolems/client/
  render/    cart renderer with the iron chain, GolemTrimLayer
automatisation/  the Automatisation add-on (its own Gradle subproject)
tools/       TrimGen.java (golem trim textures from the vanilla armour trims), SurveyGen.java (Survey Table textures)
```
