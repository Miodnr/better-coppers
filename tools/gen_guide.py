"""Writes the guide: advancement trees, the handbook recipe and loot table, and all guide texts (advancements and
Golem Handbook chapters, English and German) for Mios Coppergolems and the Automatisation add-on.

Run from the repository root: python tools/gen_guide.py
Lang entries are merged into the existing lang files; everything else is overwritten.
"""
import json
import os

MAIN = "mioscoppergolems"
AUTO = "mioscoppergolems_automatisation"
RES = {MAIN: "src/main/resources", AUTO: "automatisation/src/main/resources"}


def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def done():
    """A step unlocked from code."""
    return {"done": {"trigger": "minecraft:impossible"}}


def has(*items):
    """A step unlocked by having any of these items."""
    return {item.split(":")[1]: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}} for item in items}


def advancement(ns, name, parent, icon, criteria, frame="task", any_of=True, root=None):
    display = {
        "icon": {"id": icon},
        "title": {"translate": f"advancements.{ns}.guide.{name}.title"},
        "description": {"translate": f"advancements.{ns}.guide.{name}.description"},
        "frame": frame,
    }
    data = {}
    if parent:
        data["parent"] = f"{ns}:guide/{parent}"
    if root:
        display.update(background=root, show_toast=False, announce_to_chat=False)
    data["criteria"] = criteria
    data["display"] = display
    data["requirements"] = [list(criteria)] if any_of else [[c] for c in criteria]
    return data


JOBS = ["sorter", "miner", "farmer", "forester", "fisher", "keeper", "crafter", "courier"]

MAIN_TREE = [
    # name, parent, icon, criteria, frame, any_of
    ("root", None, "minecraft:copper_golem_statue", {"joined": {"trigger": "minecraft:tick"}}, "task", True),
    ("build_golem", "root", "minecraft:carved_pumpkin", {"built": {
        "trigger": "minecraft:summoned_entity",
        "conditions": {"entity": {"type": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": "minecraft:copper_golem"}}},
    }}, "task", True),
    ("home", "build_golem", "minecraft:copper_chest", done(), "task", True),
    ("sorter", "home", "minecraft:bundle", done(), "task", True),
    ("cart", "sorter", f"{MAIN}:copper_chest_minecart", done(), "task", True),
    ("farmer", "home", "minecraft:copper_hoe", done(), "task", True),
    ("odd_jobs", "home", "minecraft:copper_axe", {job: {"trigger": "minecraft:impossible"} for job in ["forester", "fisher", "keeper", "crafter"]}, "task", True),
    ("full_employment", "odd_jobs", "minecraft:bell", {job: {"trigger": "minecraft:impossible"} for job in JOBS}, "challenge", False),
    ("survey", "home", f"{MAIN}:survey_table", has(f"{MAIN}:mineshaft_plan"), "task", True),
    ("miner", "survey", "minecraft:copper_pickaxe", done(), "task", True),
    ("diamonds", "miner", "minecraft:diamond", done(), "goal", True),
    ("eyes", "home", "minecraft:gold_block", done(), "task", True),
    ("courier", "home", "minecraft:barrel", done(), "task", True),
    ("long_haul", "courier", "minecraft:compass", done(), "goal", True),
    ("ride", "home", "minecraft:minecart", done(), "task", True),
    ("recharge", "home", f"{MAIN}:charging_pad", done(), "task", True),
    ("scrape", "recharge", "minecraft:dispenser", done(), "task", True),
    ("guard", "build_golem", "minecraft:poppy", done(), "task", True),
    ("repair", "guard", "minecraft:iron_ingot", done(), "task", True),
    ("trim", "build_golem", "minecraft:coast_armor_trim_smithing_template", done(), "task", True),
]

AUTO_TREE = [
    ("root", None, f"{AUTO}:belt_drive", {"joined": {"trigger": "minecraft:tick"}}, "task", True),
    ("wrench", "root", f"{AUTO}:copper_wrench", has(f"{AUTO}:copper_wrench"), "task", True),
    ("operator", "wrench", f"{AUTO}:press", done(), "task", True),
    ("belt", "operator", f"{AUTO}:conveyor_belt", done(), "task", True),
    ("arm", "operator", f"{AUTO}:grabber_arm", done(), "task", True),
    ("repair", "operator", "minecraft:anvil", done(), "task", True),
    ("sheets", "operator", f"{AUTO}:copper_sheet", has(f"{AUTO}:copper_sheet", f"{AUTO}:iron_sheet", f"{AUTO}:gold_sheet"), "task", True),
    ("gears", "sheets", f"{AUTO}:copper_gear", has(f"{AUTO}:copper_gear", f"{AUTO}:gold_gear"), "task", True),
    ("assembler", "gears", f"{AUTO}:golem_assembler", done(), "goal", True),
    ("upgrade", "sheets", f"{AUTO}:iron_plating", done(), "task", True),
    ("fully_loaded", "upgrade", f"{AUTO}:gold_gearwork", done(), "challenge", True),
    ("cart_extension", "sheets", f"{AUTO}:cart_extension", done(), "task", True),
    ("charging_station", "sheets", f"{AUTO}:charging_station", has(f"{AUTO}:charging_station"), "task", True),
]

# name -> (title, description), English then German
MAIN_ADV = {
    "root": (("Mios Coppergolems", "Copper golems that work for you. Read the Golem Handbook!"),
             ("Mios Coppergolems", "Kupfergolems, die für dich arbeiten. Lies das Golem-Handbuch!")),
    "build_golem": (("A Little Helper", "Build a copper golem: a carved pumpkin on a copper block"),
                    ("Ein kleiner Helfer", "Baue einen Kupfergolem: ein geschnitzter Kürbis auf einem Kupferblock")),
    "home": (("Home Sweet Chest", "Link a golem to a copper chest: sneak + right-click it, then the chest"),
             ("Trautes Truhenheim", "Verbinde einen Golem mit einer Kupfertruhe: Schleichen + Rechtsklick auf ihn, dann auf die Truhe")),
    "sorter": (("Tidy Up", "Give a golem a bundle to make it a sorter"),
               ("Aufräumen!", "Gib einem Golem ein Bündel, damit er sortiert")),
    "cart": (("Heavy Load", "Hand a copper chest minecart to a golem with an iron or copper chain"),
             ("Schwere Ladung", "Übergib einem Golem mit einer Eisen- oder Kupferkette eine Kupfertruhen-Lore")),
    "farmer": (("Green Fingers", "Give a golem a copper hoe to make it a farmer"),
               ("Grüner Daumen", "Gib einem Golem eine Kupferhacke, damit er Bauer wird")),
    "odd_jobs": (("Odd Jobs", "Give a golem a copper axe, a fishing rod, a brush or a crafting table"),
                 ("Mädchen für alles", "Gib einem Golem eine Kupferaxt, eine Angel, einen Pinsel oder eine Werkbank")),
    "full_employment": (("Full Employment", "Give golems every job: sorter, miner, farmer, forester, fisher, animal keeper, crafter and courier"),
                        ("Vollbeschäftigung", "Gib Golems jeden Beruf: Sortierer, Bergmann, Bauer, Förster, Fischer, Tierpfleger, Handwerker und Kurier")),
    "survey": (("Surveyor", "Make a mineshaft plan at a Survey Table"),
               ("Vermesser", "Erstelle an einem Vermessungstisch einen Stollenplan")),
    "miner": (("Down the Shaft", "Give a golem a copper pickaxe and a mineshaft plan"),
              ("Ab in den Stollen", "Gib einem Golem eine Kupferspitzhacke und einen Stollenplan")),
    "diamonds": (("Shiny!", "Watch a miner find diamonds and hurry home with its antenna blinking"),
                 ("Glitzer!", "Sieh zu, wie ein Bergmann Diamanten findet und mit blinkender Antenne heimeilt")),
    "eyes": (("Eagle Eyes", "Give a golem iron, gold or diamond eyes to widen its work radius"),
             ("Adleraugen", "Gib einem Golem Augen aus Eisen, Gold oder Diamant, damit er weiter arbeitet")),
    "courier": (("Special Delivery", "Set up a courier route between two chests"),
                ("Eilzustellung", "Richte eine Kurierroute zwischen zwei Truhen ein")),
    "long_haul": (("Long Haul", "Set up a courier route over 500 blocks long"),
                  ("Fernverkehr", "Richte eine Kurierroute über mehr als 500 Blöcke ein")),
    "ride": (("All Aboard", "Watch a golem ride a minecart or a boat"),
             ("Alles einsteigen", "Sieh zu, wie ein Golem Lore oder Boot fährt")),
    "recharge": (("Recharged", "Charge a working golem to full on a Charging Pad driven by a redstone clock"),
                 ("Aufgeladen", "Lade einen Golem auf einem Ladefeld mit Redstone-Taktgeber ganz auf")),
    "scrape": (("Good as New", "Scrape a rusty golem with an axe in a dispenser"),
               ("Wie neu", "Kratze einen rostigen Golem mit einer Axt im Werfer ab")),
    "guard": (("Bodyguard", "Watch an iron golem defend a copper golem"),
              ("Leibwächter", "Sieh zu, wie ein Eisengolem einen Kupfergolem verteidigt")),
    "repair": (("Field Medic", "Watch a copper golem repair an iron golem with iron"),
               ("Sanitäter", "Sieh zu, wie ein Kupfergolem einen Eisengolem mit Eisen repariert")),
    "trim": (("Dressed to Impress", "Give a golem an armour trim: a template, then a material"),
             ("Schick gemacht", "Gib einem Golem einen Rüstungsbesatz: erst eine Vorlage, dann ein Material")),
}

AUTO_ADV = {
    "root": (("Mios Coppergolems Automatisation", "A golem-run factory: no power network, golems turn every crank"),
             ("Mios Coppergolems Automatisation", "Eine Fabrik von Golems: kein Stromnetz, Golems drehen jede Kurbel")),
    "wrench": (("Grease Monkey", "Craft a Copper Wrench"),
               ("Schrauber", "Stelle einen Kupferschraubenschlüssel her")),
    "operator": (("Turn the Crank", "Give a golem a wrench and link it to a machine"),
                 ("Kurbel drehen", "Gib einem Golem einen Schraubenschlüssel und verbinde ihn mit einer Maschine")),
    "belt": (("Keep It Moving", "Get a belt line running with a Belt Drive"),
             ("Läuft wie geschmiert", "Bring ein Förderband mit einem Bandantrieb zum Laufen")),
    "arm": (("Pick and Place", "Set up a Grabber Arm's inputs and outputs with the wrench"),
            ("Greifen und Ablegen", "Stelle die Ein- und Ausgänge eines Greifarms mit dem Schraubenschlüssel ein")),
    "repair": (("Maintenance Crew", "Watch an operator repair a jammed machine"),
               ("Wartungstrupp", "Sieh zu, wie ein Maschinist eine verklemmte Maschine repariert")),
    "sheets": (("Flat Out", "Press an ingot into a sheet"),
               ("Platt gemacht", "Presse einen Barren zu einem Blech")),
    "gears": (("Cogs and Teeth", "Saw a sheet into a gear"),
              ("Zahn um Zahn", "Säge ein Blech zu einem Zahnrad")),
    "assembler": (("Golem Factory", "Watch a Golem Assembler build a new copper golem"),
                  ("Golemfabrik", "Sieh zu, wie ein Golem-Montagewerk einen neuen Kupfergolem baut")),
    "upgrade": (("Kitted Out", "Fit a golem with plating, coating, gearwork or a satchel"),
                ("Aufgerüstet", "Rüste einen Golem mit Panzerung, Beschichtung, Getriebe oder Tasche aus")),
    "fully_loaded": (("Fully Loaded", "Give one golem all four upgrades at once"),
                     ("Voll ausgestattet", "Gib einem Golem alle vier Verbesserungen auf einmal")),
    "cart_extension": (("Double Decker", "Turn a copper chest minecart into a double chest"),
                       ("Doppeldecker", "Mach aus einer Kupfertruhen-Lore eine Doppeltruhe")),
    "charging_station": (("Fast Charger", "Build a Charging Station"),
                         ("Schnellladen", "Baue eine Ladestation")),
}

TOOLS_EN = "# Copper tools only\nGolems work with copper pickaxes, axes and hoes, nothing fancier. Fishing rods, brushes and crafting tables are fine as they are.\n\n# Broken tools\nWhen a tool breaks, the golem keeps its job. It brings home what it carries, then looks for the same tool in its home chest, and then in every chest and barrel within 16 blocks. So keep a few spare copper tools next to it. With none anywhere it waits and looks again every half minute.\n\nTo end its job instead, right-click it with an empty hand.\n\n# Eyes\nRight-click a golem with a block of iron, gold or diamond. Its eyes glow in that colour and it works further from home:\n- Iron: 1.5 times the radius\n- Gold: 2 times\n- Diamond: 3 times\nThis widens fields, forests, fishing water, animal pens, the chests a sorter knows, how far a miner follows a vein and where an operator looks for spare parts. Couriers don't need it. A new block replaces the old one, and the block drops when the golem dies."
TOOLS_DE = '# Nur Kupferwerkzeug\nGolems arbeiten mit Spitzhacken, Äxten und Hacken aus Kupfer, mit nichts Edlerem. Angeln, Pinsel und Werkbänke gehen so, wie sie sind.\n\n# Kaputtes Werkzeug\nGeht ein Werkzeug kaputt, behält der Golem seinen Beruf. Er bringt heim, was er trägt, und sucht dann dasselbe Werkzeug in seiner Heimattruhe und danach in jeder Truhe und jedem Fass im Umkreis von 16 Blöcken. Leg ihm also ein paar Ersatzwerkzeuge aus Kupfer bereit. Findet er keins, wartet er und sucht alle halbe Minute erneut.\n\nWillst du seinen Beruf stattdessen beenden, rechtsklicke ihn mit leerer Hand.\n\n# Augen\nRechtsklicke einen Golem mit einem Eisen-, Gold- oder Diamantblock. Seine Augen leuchten in dieser Farbe, und er arbeitet weiter von zu Hause entfernt:\n- Eisen: 1,5-facher Radius\n- Gold: doppelt\n- Diamant: dreifach\nDas vergrößert Felder, Wälder, Angelgewässer, Tiergehege, die Truhen eines Sortierers, wie weit ein Bergmann einer Ader folgt und wo ein Maschinist Ersatzteile sucht. Kuriere brauchen das nicht. Ein neuer Block ersetzt den alten, und der Block fällt heraus, wenn der Golem stirbt.'

# The Golem Handbook. "# " starts a heading; a blank line separates paragraphs.
MAIN_BOOK = {
    "title": ("Golem Handbook", "Golem-Handbuch"),
    "welcome": (("Welcome", """Copper golems in this mod are small, slightly clumsy workers. Give one a job item and a home chest, and it sorts, mines, farms, fishes or carries goods for you.

# Your first golem
Put a carved pumpkin on a copper block. The golem appears, and the copper block turns into a copper chest: that chest can be its home.

# Three steps
1. Right-click the golem with a job item.
2. Sneak + right-click it with an empty hand. It glows and shows its status.
3. Right-click its copper chest (within 32 blocks). That is now its home.

# The guide
Open your advancements (L). The Mios Coppergolems tab leads you through everything, one step at a time."""),
                ("Willkommen", """Kupfergolems sind in dieser Mod kleine, etwas tollpatschige Arbeiter. Gib einem einen Arbeitsgegenstand und eine Heimattruhe, und er sortiert, gräbt, erntet, angelt oder trägt Waren für dich.

# Dein erster Golem
Setze einen geschnitzten Kürbis auf einen Kupferblock. Der Golem erscheint, und der Kupferblock wird zu einer Kupfertruhe: Sie kann sein Zuhause werden.

# Drei Schritte
1. Rechtsklicke den Golem mit einem Arbeitsgegenstand.
2. Schleichen + Rechtsklick mit leerer Hand. Er leuchtet und zeigt seinen Zustand.
3. Rechtsklicke seine Kupfertruhe (bis 32 Blöcke entfernt). Das ist jetzt sein Zuhause.

# Der Leitfaden
Öffne deine Fortschritte (L). Der Reiter Mios Coppergolems führt dich Schritt für Schritt durch alles.""")),
    "homes": (("Homes and Status", """Every working golem belongs to a copper chest, its home. It brings what it makes there and fetches its supplies from there.

# Linking
Sneak + right-click the golem with an empty hand, then right-click the copper chest. To move a golem, link it again.

# Status
The same sneak-click shows its job, home, energy and rust in the chat.

# Seeing homes
While you sneak, copper particles run from every golem near you to its home. A golem with a job but no home puffs smoke.

# Staying close
With nothing to do, a golem with a home strolls about within 6 blocks of its chest.

# Taking things back
Right-click a golem with an empty hand to get its tool or bundle back."""),
              ("Zuhause und Zustand", """Jeder arbeitende Golem gehört zu einer Kupfertruhe, seinem Zuhause. Dorthin bringt er, was er erarbeitet, und dort holt er seinen Nachschub.

# Verbinden
Schleichen + Rechtsklick mit leerer Hand auf den Golem, dann Rechtsklick auf die Kupfertruhe. Zum Umziehen verbindest du ihn einfach neu.

# Zustand
Derselbe Schleich-Klick zeigt Beruf, Zuhause, Energie und Rost im Chat.

# Zuhause sehen
Solange du schleichst, laufen Kupferpartikel von jedem Golem in deiner Nähe zu seinem Zuhause. Ein Golem mit Beruf, aber ohne Zuhause, qualmt.

# In der Nähe bleiben
Hat er nichts zu tun, schlendert ein Golem mit Zuhause höchstens 6 Blöcke um seine Truhe herum.

# Zurücknehmen
Rechtsklicke einen Golem mit leerer Hand, um sein Werkzeug oder Bündel zurückzubekommen.""")),
    "sorter": (("Sorter", """Give a golem a bundle. It keeps it in its off hand and empties its home chest, item by item.

# Where things go
1. a chest that already holds that item,
2. otherwise any barrel with room,
3. otherwise an empty chest.
It knows every container within 32 blocks (8 up and down).

# Item frames
Hang item frames on a chest or barrel to reserve it: it then takes only the items its frames show, and they go there before anywhere else.

It carries 16 items per trip. Now and then it opens a wrong chest first and shakes its head: that is just its character.

Sorters are free to run: no energy, no rust."""),
               ("Sortierer", """Gib einem Golem ein Bündel. Er trägt es in der Nebenhand und leert seine Heimattruhe, Gegenstand für Gegenstand.

# Wohin damit?
1. in eine Truhe, die diesen Gegenstand schon enthält,
2. sonst in ein Fass mit Platz,
3. sonst in eine leere Truhe.
Er kennt jeden Behälter im Umkreis von 32 Blöcken (8 nach oben und unten).

# Rahmen
Häng Rahmen an eine Truhe oder ein Fass, um sie zu reservieren: Dann kommen nur die gezeigten Gegenstände hinein, und zwar vor allen anderen Truhen.

Er trägt 16 Gegenstände pro Gang. Ab und zu öffnet er erst eine falsche Truhe und schüttelt den Kopf: Das ist einfach sein Charakter.

Sortierer kosten nichts: keine Energie, kein Rost.""")),
    "cart": (("Copper Chest Minecart", """Craft a minecart with any copper chest. A golem towing one carries a whole cart load.

# Handing it over
Right-click the cart with an iron or copper chain so it follows you, then right-click a golem. Sneak + right-click the cart to unchain it.

# Related items
A sorter loads everything related to the first item it picks up: stone also brings stone slabs, stairs and bricks, but not oak logs. Then it delivers everything in one trip.

Other jobs fill the cart before their own pouch."""),
             ("Kupfertruhen-Lore", """Stelle eine Lore mit einer beliebigen Kupfertruhe her. Ein Golem, der sie zieht, trägt eine ganze Wagenladung.

# Übergeben
Rechtsklicke die Lore mit einer Eisen- oder Kupferkette, damit sie dir folgt, dann rechtsklicke einen Golem. Schleichen + Rechtsklick auf die Lore löst die Kette.

# Verwandte Gegenstände
Ein Sortierer lädt alles, was mit dem ersten Gegenstand verwandt ist: Stein bringt auch Steinstufen, -treppen und -ziegel mit, aber keine Eichenstämme. Dann liefert er alles in einem Gang aus.

Andere Berufe füllen erst die Lore, dann ihren Beutel.""")),
    "miner": (("Miner", """Miners dig a vanilla-style 3x3 mineshaft with plank floors, supports, torches and rails.

# The plan
Craft a Survey Table: 2 paper over 2 copper ingots over 2 planks. Put paper in, choose a length (8 to 128) and take out the mineshaft plan.

Use the plan on the ground where the mine should start. The tunnel heads the way you look.

# The golem
Give it a copper pickaxe and the plan, then link it to a copper chest near the entrance.

# Supplies
No supplies, no digging. Keep planks, fences, rails and torches in its chest; it fetches them when it needs them.

# On the job
It digs out every ore it can see (up to 16 per vein), plugs water and lava with cobblestone, and walks home with up to 64 items. Link a sorter to the same chest to sort the loot.

# Diamonds!
When it finds diamonds, it digs out the whole vein and then hurries home with its antenna blinking, to show you.

Right-click it with an empty hand to get the pickaxe and plan back. The plan keeps its progress."""),
              ("Bergmann", """Bergleute graben einen 3x3-Stollen wie in Vanilla, mit Bretterboden, Stützen, Fackeln und Schienen.

# Der Plan
Stelle einen Vermessungstisch her: 2 Papier über 2 Kupferbarren über 2 Bretter. Lege Papier hinein, wähle eine Länge (8 bis 128) und nimm den Stollenplan heraus.

Benutze den Plan auf dem Boden, wo der Stollen beginnen soll. Er führt in deine Blickrichtung.

# Der Golem
Gib ihm eine Kupferspitzhacke und den Plan und verbinde ihn mit einer Kupfertruhe nahe am Eingang.

# Nachschub
Ohne Material wird nicht gegraben. Lege Bretter, Zäune, Schienen und Fackeln in seine Truhe; er holt sie, wenn er sie braucht.

# Bei der Arbeit
Er baut jedes Erz ab, das er sieht (bis zu 16 pro Ader), stopft Wasser und Lava mit Bruchstein zu und geht mit bis zu 64 Gegenständen nach Hause. Ein Sortierer an derselben Truhe sortiert die Beute.

# Diamanten!
Findet er Diamanten, baut er die ganze Ader ab und eilt dann mit blinkender Antenne nach Hause, um sie dir zu zeigen.

Rechtsklick mit leerer Hand gibt dir Spitzhacke und Plan zurück. Der Plan merkt sich den Fortschritt.""")),
    "farmer": (("Farmer", """Give a golem a copper hoe. Its field is everything within 8 blocks of its home chest.

- It harvests ripe wheat, carrots, potatoes, beetroot and nether wart and replants them right away.
- It picks pumpkins and melons and leaves the stems.
- It cuts sugar cane and bamboo once they are 3 high.

It keeps 16 of every seed for replanting. It never tills new land: you build the field, the golem looks after it. Right-click the farmer with seeds to give it some."""),
               ("Bauer", """Gib einem Golem eine Kupferhacke. Sein Feld ist alles im Umkreis von 8 Blöcken um seine Heimattruhe.

- Er erntet reifen Weizen, Karotten, Kartoffeln, Rote Bete und Netherwarzen und pflanzt sofort nach.
- Er erntet Kürbisse und Melonen und lässt die Stiele stehen.
- Er schneidet Zuckerrohr und Bambus, sobald sie 3 hoch sind.

Er behält 16 von jedem Saatgut zum Nachpflanzen. Neues Land pflügt er nie: Du legst das Feld an, der Golem pflegt es. Rechtsklicke den Bauern mit Saatgut, um ihm welches zu geben.""")),
    "workers": (("Forester, Fisher, Keeper, Crafter", """# Forester: a copper axe
Fells natural trees within 12 blocks, never logs you placed. It collects saplings, apples and sticks and replants every stump. Use the axe on a clean golem; on a rusty one it scrapes instead.

# Fisher: a fishing rod
Casts its line into open water within 10 blocks and waits for the float to dip: a catch every 20 to 50 seconds, fish and junk but no treasure.

# Animal keeper: a brush
Feeds pairs of animals with food from its chest so they breed, up to 16 of each kind.

# Crafter: a crafting table
Crafts what the item frames on or next to its chest show, from the ingredients in that chest."""),
                ("Förster, Fischer, Tierpfleger, Handwerker", """# Förster: eine Kupferaxt
Fällt natürliche Bäume im Umkreis von 12 Blöcken, nie Stämme, die du gesetzt hast. Er sammelt Setzlinge, Äpfel und Stöcke und pflanzt jeden Baum nach. Gib die Axt einem sauberen Golem; einen rostigen kratzt sie stattdessen ab.

# Fischer: eine Angel
Wirft die Angel in offenes Wasser im Umkreis von 10 Blöcken aus und wartet, bis der Schwimmer abtaucht: alle 20 bis 50 Sekunden ein Fang, Fisch und Plunder, aber keine Schätze.

# Tierpfleger: ein Pinsel
Füttert Tierpaare mit Futter aus seiner Truhe, damit sie sich vermehren, bis zu 16 von jeder Art.

# Handwerker: eine Werkbank
Stellt her, was die Rahmen an oder neben seiner Truhe zeigen, aus den Zutaten in dieser Truhe.""")),
    "courier": (("Courier", """A courier carries everything from one chest to another, however far apart they are.

1. Sneak + right-click the golem with an empty hand.
2. Right-click a plain chest or barrel: it picks things up here.
3. Walk to the destination (you have 10 minutes) and right-click a chest or barrel there.

It takes 16 items per trip, or a cart load with a chained cart. On the road it keeps the chunks around it loaded, so the route keeps working while you are far away. If the destination is full, it waits.

Linking it to a home copper chest ends the route."""),
                ("Kurier", """Ein Kurier trägt alles von einer Truhe zu einer anderen, egal wie weit sie auseinander stehen.

1. Schleichen + Rechtsklick mit leerer Hand auf den Golem.
2. Rechtsklicke eine normale Truhe oder ein Fass: Hier holt er ab.
3. Geh zum Ziel (du hast 10 Minuten) und rechtsklicke dort eine Truhe oder ein Fass.

Er nimmt 16 Gegenstände pro Gang mit, mit angeketteter Lore eine ganze Ladung. Unterwegs hält er die Chunks um sich geladen, damit die Route auch funktioniert, wenn du weit weg bist. Ist das Ziel voll, wartet er.

Verbindest du ihn mit einer Kupfertruhe als Zuhause, endet die Route.""")),
    "travel": (("Minecarts and Boats", """A golem heading somewhere more than 24 blocks away looks around for a ride.

# Minecart
An empty minecart on straight rails that run the right way. The golem pushes it along at about four times walking speed and gets off near its goal.

# Boat
An empty boat on water that stretches the right way. The golem paddles straight towards its goal and climbs out at the shore.

A golem towing a cart always walks."""),
               ("Loren und Boote", """Ein Golem, der mehr als 24 Blöcke weit muss, sieht sich nach einer Mitfahrgelegenheit um.

# Lore
Eine leere Lore auf geraden Schienen in die richtige Richtung. Der Golem schiebt sie mit etwa vierfacher Gehgeschwindigkeit und steigt nahe am Ziel aus.

# Boot
Ein leeres Boot auf Wasser, das sich in die richtige Richtung erstreckt. Der Golem paddelt geradewegs zum Ziel und klettert am Ufer heraus.

Ein Golem, der eine Lore zieht, geht immer zu Fuß.""")),
    "danger": (("Monsters and Iron Golems", """Monsters hunt copper golems: zombies, skeletons, spiders, illagers and more. A copper golem has only 12 health.

# Guards
Iron golems stay near the copper golems around them and attack any monster that hunts or hits one (except creepers).

# Repairs
When an iron golem nearby is badly hurt, a copper golem takes iron ingots from its home chest (or the nearest chest with iron) and repairs it: 25 health per ingot.

So build an iron golem for every work site, and keep some iron in your chests."""),
               ("Monster und Eisengolems", """Monster jagen Kupfergolems: Zombies, Skelette, Spinnen, Illager und mehr. Ein Kupfergolem hat nur 12 Lebenspunkte.

# Wächter
Eisengolems bleiben in der Nähe der Kupfergolems und greifen jedes Monster an, das einen jagt oder schlägt (außer Creeper).

# Reparaturen
Ist ein Eisengolem in der Nähe schwer verletzt, holt ein Kupfergolem Eisenbarren aus seiner Heimattruhe (oder der nächsten Truhe mit Eisen) und repariert ihn: 25 Lebenspunkte pro Barren.

Bau also für jeden Arbeitsplatz einen Eisengolem und halte etwas Eisen in deinen Truhen bereit.""")),
    "upkeep": (("Energy and Rust", """Sorters and couriers are free. Every other job costs something.

# Energy
Work uses energy. At night, or when it runs empty, the golem walks to a free Charging Pad near its home (3 copper ingots, lightning rod, redstone) and stands on it.

A steady signal does nothing: the pad charges on redstone pulses, so it needs a redstone clock. Every pulse gives a little energy; the faster the clock, the faster the charge, up to full in about 25 seconds. Build one pad per golem.

# Sleeping
When you sleep through the night, every working golem with a free pad near its home wakes up fully charged, clock or not.

# Rust
Steady work makes a golem rust one stage every few days. Exposed golems work at 85% speed, weathered ones at 70%. Oxidized or waxed golems don't work at all.

# Scraping
Use an axe on the golem, or put an axe in a dispenser: every redstone pulse scrapes the golem in front of it by one stage.

# Tools
Job tools wear out and break."""),
               ("Energie und Rost", """Sortierer und Kuriere kosten nichts. Alle anderen Berufe haben ihren Preis.

# Energie
Arbeit kostet Energie. Nachts oder wenn sie leer ist, geht der Golem zu einem freien Ladefeld nahe seinem Zuhause (3 Kupferbarren, Blitzableiter, Redstone) und stellt sich darauf.

Ein Dauersignal bringt nichts: Das Ladefeld lädt bei Redstone-Impulsen, es braucht also einen Taktgeber. Jeder Impuls gibt etwas Energie; je schneller der Takt, desto schneller die Ladung, bis zu voll in etwa 25 Sekunden. Bau für jeden Golem ein Ladefeld.

# Schlafen
Schläfst du durch die Nacht, wacht jeder arbeitende Golem mit einem freien Ladefeld nahe seinem Zuhause voll geladen auf, mit oder ohne Taktgeber.

# Rost
Ständige Arbeit lässt einen Golem alle paar Tage eine Stufe rosten. Angelaufene Golems arbeiten mit 85 %, verwitterte mit 70 %. Oxidierte oder gewachste Golems arbeiten gar nicht.

# Abkratzen
Benutze eine Axt am Golem, oder lege eine Axt in einen Werfer: Jeder Redstone-Impuls kratzt den Golem davor um eine Stufe ab.

# Werkzeuge
Arbeitswerkzeuge nutzen sich ab und gehen kaputt.""")),
    "tools": (("Tools and Eyes", TOOLS_EN), ("Werkzeug und Augen", TOOLS_DE)),
    "trims": (("Armour Trims", """Golems can wear armour trims, just for looks.

Right-click a golem with an armour trim smithing template, then with a trim material (iron, gold, copper, emerald, amethyst...). The material is used up; the template stays with the golem.

A new template or material changes the trim. Shears take it off and give the template back."""),
              ("Rüstungsbesätze", """Golems können Rüstungsbesätze tragen, nur zur Zierde.

Rechtsklicke einen Golem mit einer Schmiedevorlage, dann mit einem Besatzmaterial (Eisen, Gold, Kupfer, Smaragd, Amethyst ...). Das Material wird verbraucht, die Vorlage bleibt beim Golem.

Eine neue Vorlage oder ein neues Material ändert den Besatz. Eine Schere nimmt ihn ab und gibt dir die Vorlage zurück.""")),
    "reference": (("Quick Reference", """# Job items
Bundle: sorter
Copper pickaxe + plan: miner
Copper hoe: farmer
Copper axe: forester
Fishing rod: fisher
Brush: animal keeper
Crafting table: crafter
Two chests: courier

# Clicks
Item on golem: hand it over
Sneak + empty hand on golem: status and linking
...then a copper chest: home
...then a chest, then another: courier route
Empty hand on golem: tool back
Holding sneak: home lines

# Energy
Charging Pad + redstone clock

# Eyes
Iron / gold / diamond block: 1.5x / 2x / 3x radius

Lost this book? Craft a new one from a book and a copper ingot."""),
                  ("Kurzübersicht", """# Arbeitsgegenstände
Bündel: Sortierer
Kupferspitzhacke + Plan: Bergmann
Kupferhacke: Bauer
Kupferaxt: Förster
Angel: Fischer
Pinsel: Tierpfleger
Werkbank: Handwerker
Zwei Truhen: Kurier

# Klicks
Gegenstand auf Golem: übergeben
Schleichen + leere Hand auf Golem: Zustand und Verbinden
... dann Kupfertruhe: Zuhause
... dann Truhe, dann noch eine: Kurierroute
Leere Hand auf Golem: Werkzeug zurück
Schleichen halten: Linien zum Zuhause

# Energie
Ladefeld + Redstone-Taktgeber

# Augen
Eisen-, Gold-, Diamantblock: 1,5- / 2- / 3-facher Radius

Buch verloren? Stelle ein neues aus einem Buch und einem Kupferbarren her.""")),
}

AUTO_BOOK = {
    "machines": (("Machines and Operators", """The Automatisation add-on adds a factory run by golems. There is no power network: every machine has a crank, and a copper golem has to turn it.

# Operators
1. Craft a Copper Wrench (3 copper ingots and 2 sticks) and right-click a golem with it.
2. Sneak + right-click the golem, then right-click the machine (within 32 blocks).

Whenever the machine has work, the operator walks over and cranks. One golem works one machine. Operators need energy and rust like other workers.

# Where to start
Build a press first: most things are made from sheets."""),
                 ("Maschinen und Maschinisten", """Die Erweiterung Automatisation bringt eine Fabrik, die Golems betreiben. Es gibt kein Stromnetz: Jede Maschine hat eine Kurbel, und ein Kupfergolem muss sie drehen.

# Maschinisten
1. Stelle einen Kupferschraubenschlüssel her (3 Kupferbarren und 2 Stöcke) und rechtsklicke damit einen Golem.
2. Schleichen + Rechtsklick auf den Golem, dann Rechtsklick auf die Maschine (bis 32 Blöcke).

Hat die Maschine Arbeit, geht der Maschinist hin und kurbelt. Ein Golem bedient eine Maschine. Maschinisten brauchen Energie und rosten wie andere Arbeiter.

# Wo anfangen?
Bau zuerst eine Presse: Das meiste wird aus Blechen gemacht.""")),
    "belts": (("Belts, Funnels, Chutes", """# Conveyor Belt
Belts run the way you face when you place them; corners work. A Belt Drive next to any belt runs the whole line (up to 64 belts) while its operator cranks. Items queue up behind items that stand still. Belts carry mobs and players too, but not copper golems.

# Funnel
Place it against a container. It pulls in items that land in its mouth. Right-click it with the wrench to make it push stacks out instead. Right-click with an item to set a filter, with an empty hand to clear it.

# Chute
Drops items straight down, a stack at a time: into a container, into the next chute, or out onto a belt."""),
              ("Bänder, Trichter, Fallrohre", """# Förderband
Bänder laufen in die Richtung, in die du beim Setzen schaust; Ecken gehen auch. Ein Bandantrieb neben einem Band treibt die ganze Strecke an (bis 64 Bänder), solange sein Maschinist kurbelt. Gegenstände stauen sich hinter stehenden Gegenständen. Bänder tragen auch Tiere und Spieler, aber keine Kupfergolems.

# Kupfertrichter
Setze ihn an einen Behälter. Er zieht Gegenstände ein, die in seiner Öffnung landen. Mit dem Schraubenschlüssel schaltest du auf Hinausschieben um. Rechtsklick mit einem Gegenstand setzt einen Filter, mit leerer Hand entfernst du ihn.

# Fallrohr
Lässt Gegenstände stapelweise senkrecht fallen: in einen Behälter, ins nächste Fallrohr oder unten auf ein Band.""")),
    "press": (("Press and Saw", """Both hang one block above a belt. They catch matching items and hold them until the operator cranks.

# Press
Copper, iron and gold ingots become sheets. Sugar cane becomes paper.

# Saw
- Copper and gold sheets become gears.
- Logs become planks: 6 per log.
- Planks become fences, slabs or stairs of their wood, as the filter shows.
- Stone becomes whatever a stonecutter makes, as the filter shows.
Right-click the saw with an item to set its filter."""),
              ("Presse und Säge", """Beide hängen einen Block über einem Band. Sie fangen passende Gegenstände ab und halten sie fest, bis der Maschinist kurbelt.

# Presse
Kupfer-, Eisen- und Goldbarren werden zu Blechen. Zuckerrohr wird zu Papier.

# Säge
- Kupfer- und Goldbleche werden zu Zahnrädern.
- Stämme werden zu Brettern: 6 pro Stamm.
- Bretter werden zu Zäunen, Stufen oder Treppen ihres Holzes, je nach Filter.
- Stein wird zu allem, was ein Steinschneider macht, je nach Filter.
Rechtsklicke die Säge mit einem Gegenstand, um den Filter zu setzen.""")),
    "arm": (("Grabber Arm", """The arm swings up to 16 items at a time from its inputs to its outputs. It reaches 4 blocks sideways and 3 up or down.

1. Right-click the arm with the wrench.
2. Right-click blocks with the wrench: once = input (blue), twice = output (orange), three times = forget.
3. Right-click the arm again when you are done.

A spot can be a container, or any other block: there it picks up items lying on top, or puts them down, onto a belt for example."""),
            ("Greifarm", """Der Arm schwenkt bis zu 16 Gegenstände auf einmal von seinen Eingängen zu seinen Ausgängen. Er reicht 4 Blöcke zur Seite und 3 nach oben oder unten.

1. Rechtsklicke den Arm mit dem Schraubenschlüssel.
2. Rechtsklicke Blöcke mit dem Schraubenschlüssel: einmal = Eingang (blau), zweimal = Ausgang (orange), dreimal = vergessen.
3. Rechtsklicke den Arm erneut, wenn du fertig bist.

Eine Stelle kann ein Behälter sein oder jeder andere Block: Dort hebt er Gegenstände auf, die obendrauf liegen, oder legt sie ab, zum Beispiel auf ein Band.""")),
    "wear": (("Wear and Repairs", """Machines wear out. A worn-out machine jams: it stops and smokes. Its operator then fetches a spare part from a chest or barrel within 8 blocks and repairs it.

- Belt Drive: 20 minutes of running, a copper sheet
- Press, Saw: 64 stacks, an iron sheet
- Grabber Arm: 256 swings, an iron sheet
- Golem Assembler: 8 golems, an iron sheet

Sneak + right-click a machine with the wrench to see its wear. Keep a few sheets next to every machine."""),
             ("Verschleiß und Reparatur", """Maschinen nutzen sich ab. Eine abgenutzte Maschine verklemmt: Sie bleibt stehen und qualmt. Ihr Maschinist holt dann ein Ersatzteil aus einer Truhe oder einem Fass im Umkreis von 8 Blöcken und repariert sie.

- Bandantrieb: 20 Minuten Laufzeit, ein Kupferblech
- Presse, Säge: 64 Stapel, ein Eisenblech
- Greifarm: 256 Schwünge, ein Eisenblech
- Golem-Montagewerk: 8 Golems, ein Eisenblech

Schleichen + Rechtsklick mit dem Schraubenschlüssel zeigt den Verschleiß. Halte neben jeder Maschine ein paar Bleche bereit.""")),
    "upgrades": (("Golem Upgrades", """Right-click a golem with these:

# Iron Plating
5 iron sheets. Takes three quarters of every hit until it has taken 40 damage.

# Copper Coating
3 copper sheets and a honeycomb. No rust from work for about three days.

# Gold Gearwork
2 gold gears, a copper gear and redstone. 30% faster for a while.

# Copper Satchel
Leather and a copper sheet. Sorters and couriers carry 32 items instead of 16.

# Cart Extension
4 copper sheets and a chest. Use it on a copper chest minecart to make it a double chest.

Sneak + right-click a golem to see what it wears."""),
                 ("Golem-Verbesserungen", """Rechtsklicke einen Golem damit:

# Eisenpanzerung
5 Eisenbleche. Fängt drei Viertel jedes Treffers ab, bis sie 40 Schaden genommen hat.

# Kupferbeschichtung
3 Kupferbleche und eine Honigwabe. Etwa drei Tage lang kein Rost durch Arbeit.

# Goldgetriebe
2 Goldzahnräder, ein Kupferzahnrad und Redstone. Eine Weile 30 % schneller.

# Kupfertasche
Leder und ein Kupferblech. Sortierer und Kuriere tragen 32 statt 16 Gegenstände.

# Loren-Erweiterung
4 Kupferbleche und eine Truhe. Benutze sie an einer Kupfertruhen-Lore, um daraus eine Doppeltruhe zu machen.

Schleichen + Rechtsklick auf einen Golem zeigt, was er trägt.""")),
    "assembler": (("Assembler and Charging", """# Golem Assembler
Builds new copper golems. Fill its 9 slots by hand or with hoppers, funnels or an arm. One golem needs 4 copper sheets, 2 copper gears, a carved pumpkin and redstone dust. With room in front, its operator cranks for 20 seconds and a new golem steps out.

# Charging Station
A charging pad that needs no redstone clock and charges twice as fast as a Charging Pad on the fastest clock. Put it within 16 blocks of a golem's home or machine.

# Supplies
4 iron sheets and a stick make 16 rails. The saw makes fences and slabs for miners, and the press makes paper for plans."""),
                  ("Montagewerk und Laden", """# Golem-Montagewerk
Baut neue Kupfergolems. Fülle seine 9 Plätze von Hand oder mit Trichtern, Kupfertrichtern oder einem Arm. Ein Golem braucht 4 Kupferbleche, 2 Kupferzahnräder, einen geschnitzten Kürbis und Redstone-Staub. Ist vorne Platz, kurbelt sein Maschinist 20 Sekunden und ein neuer Golem tritt heraus.

# Ladestation
Ein Ladefeld, das keinen Taktgeber braucht und doppelt so schnell lädt wie ein normales Ladefeld am schnellsten Takt. Stelle es höchstens 16 Blöcke vom Zuhause oder der Maschine eines Golems auf.

# Nachschub
4 Eisenbleche und ein Stock ergeben 16 Schienen. Die Säge macht Zäune und Stufen für Bergleute, die Presse Papier für Pläne.""")),
}

AUTO_CHAPTERS = list(AUTO_BOOK)

# Other texts: tools, eyes, the fishing float.
MESSAGES = [
    ("message.mioscoppergolems.copper_tools_only", "Copper golems only work with copper tools.", "Kupfergolems arbeiten nur mit Kupferwerkzeug."),
    ("message.mioscoppergolems.job_ended", "The golem gives up its job.", "Der Golem gibt seinen Beruf auf."),
    ("message.mioscoppergolems.eyes_courier", "Couriers don't need sharper eyes: they go wherever their route leads.", "Kuriere brauchen keine schärferen Augen: Sie gehen, wohin ihre Route führt."),
    ("message.mioscoppergolems.eyes_set", "The golem's eyes glow: it now works %s%% as far from home.", "Die Augen des Golems leuchten: Er arbeitet jetzt %s %% so weit von zu Hause."),
    ("status.mioscoppergolems.lost_tool", "looking for a new %s", "sucht Ersatz: %s"),
    ("status.mioscoppergolems.eyes", "eyes %s%%", "Augen %s %%"),
    ("entity.mioscoppergolems.golem_bobber", "Golem Bobber", "Golem-Schwimmer"),
]


def main():
    lang = {MAIN: ({}, {}), AUTO: ({}, {})}
    for ns, tree, texts in [(MAIN, MAIN_TREE, MAIN_ADV), (AUTO, AUTO_TREE, AUTO_ADV)]:
        folder = os.path.join(RES[ns], "data", ns, "advancement", "guide")
        for name, parent, icon, criteria, frame, any_of in tree:
            root = None
            if parent is None:
                root = "minecraft:block/cut_copper" if ns == MAIN else "minecraft:block/exposed_copper"
            data = advancement(ns, name, parent, icon, criteria, frame, any_of, root)
            if ns == MAIN and name == "root":
                data["rewards"] = {"loot": [f"{MAIN}:guide/handbook"]}
            write(os.path.join(folder, name + ".json"), data)
            for i in range(2):
                title, description = texts[name][i]
                lang[ns][i][f"advancements.{ns}.guide.{name}.title"] = title
                lang[ns][i][f"advancements.{ns}.guide.{name}.description"] = description

    for ns, book in [(MAIN, MAIN_BOOK), (AUTO, AUTO_BOOK)]:
        for chapter, value in book.items():
            if chapter == "title":
                for i in range(2):
                    lang[ns][i][f"handbook.{ns}.title"] = value[i]
                continue
            for i in range(2):
                title, text = value[i]
                lang[ns][i][f"handbook.{ns}.{chapter}.title"] = title
                lang[ns][i][f"handbook.{ns}.{chapter}.text"] = text
    lang[MAIN][0]["item.mioscoppergolems.golem_handbook"] = "Golem Handbook"
    lang[MAIN][1]["item.mioscoppergolems.golem_handbook"] = "Golem-Handbuch"
    for key, en, de in MESSAGES:
        lang[MAIN][0][key] = en
        lang[MAIN][1][key] = de

    write(os.path.join(RES[MAIN], "data", MAIN, "loot_table", "guide", "handbook.json"), {
        "type": "minecraft:advancement_reward",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{MAIN}:golem_handbook"}]}],
    })
    write(os.path.join(RES[MAIN], "data", MAIN, "recipe", "golem_handbook.json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": ["minecraft:book", "minecraft:copper_ingot"],
        "result": {"id": f"{MAIN}:golem_handbook"},
    })
    write(os.path.join(RES[MAIN], "assets", MAIN, "items", "golem_handbook.json"),
          {"model": {"type": "minecraft:model", "model": f"{MAIN}:item/golem_handbook"}})
    write(os.path.join(RES[MAIN], "assets", MAIN, "models", "item", "golem_handbook.json"),
          {"parent": "minecraft:item/generated", "textures": {"layer0": f"{MAIN}:item/golem_handbook"}})

    for ns in (MAIN, AUTO):
        for i, code in enumerate(["en_us", "de_de"]):
            path = os.path.join(RES[ns], "assets", ns, "lang", code + ".json")
            with open(path, encoding="utf-8") as f:
                existing = json.load(f)
            existing.update(lang[ns][i])
            write(path, existing)
    print("chapters for the add-on:", AUTO_CHAPTERS)


if __name__ == "__main__":
    main()
