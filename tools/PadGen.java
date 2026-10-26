import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Charging Pad textures: cut copper with a copper coil around a redstone core on top, and a redstone strip round the
 * sides. Each comes twice: dark, and lit for the moment after a redstone pulse. '.' keeps the cut copper underneath;
 * 'r' is redstone (dark or glowing).
 * args: vanillaTexturesDir outDir
 */
public class PadGen {
	static final Map<Character, Integer> COLORS = Map.of(
		'k', 0x6E3A26, 'K', 0x4A2418, 'c', 0xE3826C, 'C', 0xC06A4B, 'g', 0x3C3C3C
	);
	static final int[] REDSTONE_DARK = {0x5A1010, 0x7A1A1A};
	static final int[] REDSTONE_LIT = {0xFF3A2A, 0xFFB0A0};

	static final String[] TOP = {
		"KkkkkkkkkkkkkkkK",
		"k..............k",
		"k.KKKKKKKKKKKK.k",
		"k.K..........K.k",
		"k.K.cCCCCCCc.K.k",
		"k.K.C......C.K.k",
		"k.K.C.grRg.C.K.k",
		"k.K.C.rRRr.C.K.k",
		"k.K.C.rRRr.C.K.k",
		"k.K.C.grrg.C.K.k",
		"k.K.C......C.K.k",
		"k.K.cCCCCCCc.K.k",
		"k.K..........K.k",
		"k.KKKKKKKKKKKK.k",
		"k..............k",
		"KkkkkkkkkkkkkkkK",
	};

	static final String[] SIDE = {
		"KkkkkkkkkkkkkkkK",
		"................",
		"................",
		"................",
		"................",
		"................",
		"KKKKKKKKKKKKKKKK",
		"rrRrrrRrrrRrrrRr",
		"KKKKKKKKKKKKKKKK",
		"................",
		"................",
		"................",
		"................",
		"................",
		"................",
		"KkkkkkkkkkkkkkkK",
	};

	public static void main(String[] args) throws Exception {
		File textures = new File(args[0]);
		File out = new File(args[1]);
		out.mkdirs();
		BufferedImage base = ImageIO.read(new File(textures, "cut_copper.png"));
		for (boolean lit : new boolean[] {false, true}) {
			String suffix = lit ? "_lit" : "";
			ImageIO.write(paint(base, TOP, lit), "png", new File(out, "charging_pad_top" + suffix + ".png"));
			ImageIO.write(paint(base, SIDE, lit), "png", new File(out, "charging_pad_side" + suffix + ".png"));
		}
	}

	static BufferedImage paint(BufferedImage base, String[] rows, boolean lit) {
		int[] redstone = lit ? REDSTONE_LIT : REDSTONE_DARK;
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				char c = rows[y].charAt(x);
				int rgb;
				if (c == '.') {
					rgb = base.getRGB(x, y) & 0xFFFFFF;
				} else if (c == 'r' || c == 'R') {
					rgb = redstone[c == 'r' ? 0 : 1];
				} else {
					rgb = COLORS.get(c);
				}
				image.setRGB(x, y, 0xFF000000 | rgb);
			}
		}
		return image;
	}
}
