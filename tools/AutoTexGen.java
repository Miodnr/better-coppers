import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/**
 * Textures for Mios Coppergolems Automatisation: the conveyor belt (still and moving), the funnel faces, the copper
 * wrench and the metal sheets. Everything else reuses vanilla textures.
 * args: vanillaTexturesDir outAssetsTexturesDir previewFile
 */
public class AutoTexGen {
	static final int[] COPPER = {0xFBA77E, 0xE77C56, 0xC15A36, 0x8E3A22, 0x5E2414};
	static final int[] IRON = {0xFFFFFF, 0xD8D8D8, 0xA8A8A8, 0x727272, 0x444444};
	static final int[] GOLD = {0xFFFDE0, 0xFDF55F, 0xE9B115, 0xA9700E, 0x6B4706};

	public static void main(String[] args) throws Exception {
		File vanilla = new File(args[0]);
		File out = new File(args[1]);
		new File(out, "block").mkdirs();
		new File(out, "item").mkdirs();
		BufferedImage cutCopper = ImageIO.read(new File(vanilla, "block/cut_copper.png"));

		// Belt: dark rubber with cross ridges every 4 pixels; the moving one scrolls them north (up the texture).
		ImageIO.write(belt(0), "png", new File(out, "block/conveyor_belt.png"));
		BufferedImage moving = new BufferedImage(16, 64, BufferedImage.TYPE_INT_ARGB);
		for (int frame = 0; frame < 4; frame++) {
			moving.getGraphics().drawImage(belt(frame), 0, frame * 16, null);
		}
		ImageIO.write(moving, "png", new File(out, "block/conveyor_belt_moving.png"));
		Files.writeString(new File(out, "block/conveyor_belt_moving.png.mcmeta").toPath(), "{\n  \"animation\": {\n    \"frametime\": 1\n  }\n}\n");
		ImageIO.write(beltSide(), "png", new File(out, "block/conveyor_belt_side.png"));

		ImageIO.write(funnel(cutCopper, false), "png", new File(out, "block/funnel_pull.png"));
		ImageIO.write(funnel(cutCopper, true), "png", new File(out, "block/funnel_push.png"));

		ImageIO.write(wrench(), "png", new File(out, "item/copper_wrench.png"));
		ImageIO.write(sheet(COPPER), "png", new File(out, "item/copper_sheet.png"));
		ImageIO.write(sheet(IRON), "png", new File(out, "item/iron_sheet.png"));
		ImageIO.write(sheet(GOLD), "png", new File(out, "item/gold_sheet.png"));

		int s = 10;
		BufferedImage[] all = {belt(0), beltSide(), funnel(cutCopper, false), funnel(cutCopper, true), wrench(), sheet(COPPER), sheet(IRON), sheet(GOLD)};
		BufferedImage preview = new BufferedImage(all.length * (16 * s + 8), 16 * s, BufferedImage.TYPE_INT_ARGB);
		var g = preview.createGraphics();
		g.setColor(new java.awt.Color(0x3a3a3a));
		g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
		for (int i = 0; i < all.length; i++) {
			g.drawImage(all[i], i * (16 * s + 8), 0, 16 * s, 16 * s, null);
		}
		ImageIO.write(preview, "png", new File(args[2]));
	}

	static BufferedImage belt(int shift) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			int phase = Math.floorMod(y + shift, 4);
			for (int x = 0; x < 16; x++) {
				int color;
				if (x == 0 || x == 15) {
					color = 0x1A1A1A;
				} else if (phase == 0) {
					color = 0x4C4C4C;
				} else if (phase == 1) {
					color = 0x1E1E1E;
				} else {
					color = (x * 7 + y * 3) % 5 == 0 ? 0x333333 : 0x2C2C2C;
				}
				image.setRGB(x, y, 0xFF000000 | color);
			}
		}
		return image;
	}

	static BufferedImage beltSide() {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int color;
				if (y >= 12) {
					// The belt's edge (bottom four rows are what a 4 pixel high belt shows).
					color = y == 12 ? 0x4C4C4C : y == 15 ? 0x161616 : (x % 4 == 0 ? 0x3A3A3A : 0x262626);
				} else {
					color = (x + y) % 2 == 0 ? 0x2A2A2A : 0x262626;
				}
				image.setRGB(x, y, 0xFF000000 | color);
			}
		}
		return image;
	}

	/** Copper plate with a dark throat and three chevrons: pointing in (pull) or out (push). */
	static BufferedImage funnel(BufferedImage copper, boolean push) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int color = copper.getRGB(x, y) & 0xFFFFFF;
				int d = Math.max(Math.abs(2 * x - 15), Math.abs(2 * y - 15));
				if (d <= 9) {
					color = 0x2A1A12;
				} else if (d <= 11) {
					color = COPPER[3];
				}
				image.setRGB(x, y, 0xFF000000 | color);
			}
		}
		// Two chevrons in the throat, pointing in (pull) or out (push).
		int accent = push ? 0xF2B34D : 0x7FD8E8;
		for (int chevron = 0; chevron < 2; chevron++) {
			int top = 4 + chevron * 5;
			for (int i = 0; i < 4; i++) {
				int y = push ? top + 3 - i : top + i;
				set(image, 4 + i, y, accent);
				set(image, 11 - i, y, accent);
			}
		}
		return image;
	}

	/** An open-end wrench, lower left to upper right: a copper handle and a ring-shaped jaw open to the top right. */
	static BufferedImage wrench() {
		String[] rows = {
			"................",
			"...........bb...",
			"..........baab..",
			".........ba..c..",
			".........bb.....",
			"..........ba..b.",
			".........bcbbbc.",
			"........bac.....",
			".......bac......",
			"......bac.......",
			".....bac........",
			"....bac.........",
			"...bac..........",
			"..bac...........",
			"..cc............",
			"................",
		};
		return paint(rows, COPPER);
	}

	static BufferedImage paint(String[] rows, int[] palette) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				switch (rows[y].charAt(x)) {
					case 'a' -> set(image, x, y, palette[0]);
					case 'b' -> set(image, x, y, palette[1]);
					case 'c' -> set(image, x, y, palette[3]);
					default -> {
					}
				}
			}
		}
		return image;
	}

	/** A flat metal sheet in slight perspective, with a bright edge and a dull underside. */
	static BufferedImage sheet(int[] palette) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 4; y <= 11; y++) {
			int left = 2 + (11 - y) / 3;
			int right = 13 + (11 - y) / 3 - 1;
			for (int x = left; x <= right; x++) {
				int color = palette[2];
				if (y == 4) {
					color = palette[0];
				} else if (x == left || y == 5) {
					color = palette[1];
				} else if ((x + y) % 5 == 0) {
					color = palette[1];
				}
				set(image, x, y, color);
			}
			set(image, right + 1, y, palette[3]);
		}
		for (int x = 2; x <= 13; x++) {
			set(image, x, 12, palette[4]);
		}
		return image;
	}

	static void set(BufferedImage image, int x, int y, int rgb) {
		if (x >= 0 && x < 16 && y >= 0 && y < 16) {
			image.setRGB(x, y, 0xFF000000 | rgb);
		}
	}
}
