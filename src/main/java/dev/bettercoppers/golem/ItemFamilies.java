package dev.bettercoppers.golem;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Decides which items a golem with a cart loads together. Items are related when their ids share a material word,
 * e.g. stone, stone_slab, stone_bricks and stone_brick_stairs all share "stone". Shape words and colours are ignored,
 * so oak_slab is not related to stone_slab, but all wool colours are related to each other.
 */
public final class ItemFamilies {
	private static final Set<String> IGNORED_WORDS = Set.of(
		// shapes and variants
		"slab", "stairs", "wall", "fence", "gate", "button", "pressure", "plate", "door", "trapdoor", "sign", "hanging",
		"block", "bricks", "brick", "tiles", "tile", "pillar", "pane", "polished", "smooth", "chiseled", "cracked", "mossy",
		"cut", "cobbled", "stripped", "waxed", "exposed", "weathered", "oxidized", "infested", "raw", "stained", "glazed",
		"ore", "of", "on", "the", "and",
		// colours
		"white", "orange", "magenta", "light", "blue", "yellow", "lime", "pink", "gray", "cyan", "purple", "brown",
		"green", "red", "black"
	);

	/** Words that only mean something together with the word after them: nether bricks are not nether wart, dark oak is not oak. */
	private static final Set<String> BINDING_WORDS = Set.of("nether", "end", "dark", "pale", "sea", "glow");

	private ItemFamilies() {
	}

	public static boolean related(ItemStack a, ItemStack b) {
		if (a.is(b.getItem())) {
			return true;
		}
		// Tools, weapons and armour only travel with exactly the same item.
		if (a.has(DataComponents.MAX_DAMAGE) || b.has(DataComponents.MAX_DAMAGE)) {
			return false;
		}
		Set<String> wordsA = materialWords(a.getItem());
		for (String word : materialWords(b.getItem())) {
			if (wordsA.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static Set<String> materialWords(Item item) {
		Set<String> words = new HashSet<>();
		String[] parts = BuiltInRegistries.ITEM.getKey(item).getPath().split("_");
		for (int i = 0; i < parts.length; i++) {
			String word = parts[i];
			if (BINDING_WORDS.contains(word) && i + 1 < parts.length) {
				// "nether_bricks" becomes the single material "nether_brick", unrelated to "nether_wart".
				words.add(word + "_" + singular(parts[++i]));
			} else if (!IGNORED_WORDS.contains(word)) {
				words.add(word);
			}
		}
		return words;
	}

	private static String singular(String word) {
		return switch (word) {
			case "bricks" -> "brick";
			case "tiles" -> "tile";
			default -> word;
		};
	}
}
