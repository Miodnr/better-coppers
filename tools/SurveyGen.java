import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Survey Table block textures: dark wood like the cartography table, copper bands, a mineshaft plan on top, a
 * pickaxe on the front and rolled-up plans on the sides. '.' keeps the dark wood underneath.
 * args: vanillaTexturesDir outDir previewFile
 */
public class SurveyGen {
	static final Map<Character, Integer> COLORS = Map.ofEntries(
		Map.entry('c', 0xE3826C), Map.entry('C', 0xC87456), Map.entry('k', 0x9A5038),
		Map.entry('p', 0xF9E8BF), Map.entry('P', 0xFCFCF2), Map.entry('s', 0xBCA160), Map.entry('i', 0x4B2B18),
		Map.entry('r', 0xBE2633), Map.entry('h', 0x6A461E), Map.entry('H', 0x291A0C),
		Map.entry('w', 0xD8D8D8), Map.entry('M', 0x444444)
	);

	static final String[] TOP = {
		"kCc..........cCk",
		"C..............C",
		"c.ppppppppppps.c",
		"..pPpppippppps..",
		"..pppppippppps..",
		"..piiiiiiiiiis..",
		"..pppppippppps..",
		"..pppppippprps..",
		"..pppppippppps..",
		"..piiiiiiiiiis..",
		"..pppppippppps..",
		"..pPpppippppps..",
		"..ppppkCkpppps..",
		"c.ssssssssssss.c",
		"C..............C",
		"kCc..........cCk",
	};

	static final String[] SIDE = {
		"cccccccccccccccc",
		"kkkkkkkkkkkkkkkk",
		"................",
		"..........kCCk..",
		"..........CwrC..",
		"..........CMwC..",
		"..iPPrPPi.kCCk..",
		"..ipprppi.......",
		"..isssssi.......",
		"...iPPPPrPPPPi..",
		"...ipppprppppi..",
		"...isssssssssi..",
		"hhhhhhhhhhhhhhhh",
		"HHHHHHHHHHHHHHHH",
		"................",
		"kkkkkkkkkkkkkkkk",
	};

	public static void main(String[] args) throws Exception {
		File textures = new File(args[0]);
		File out = new File(args[1]);
		out.mkdirs();
		BufferedImage wood = ImageIO.read(new File(textures, "block/cartography_table_side3.png"));
		BufferedImage pickaxe = ImageIO.read(new File(textures, "item/iron_pickaxe.png"));

		BufferedImage top = paint(wood, TOP);
		BufferedImage side = paint(wood, SIDE);
		BufferedImage front = paint(wood, new String[] {SIDE[0], SIDE[1], "", "", "", "", "", "", "", "", "", "", "", "", "", SIDE[15]});
		// The pickaxe hangs on the front, a little smaller than the item: every pixel below the copper band.
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int argb = pickaxe.getRGB(x, y);
				int ty = y + 1;
				if (argb >>> 24 != 0 && ty >= 2 && ty <= 14) {
					front.setRGB(x, ty, argb | 0xFF000000);
				}
			}
		}
		ImageIO.write(top, "png", new File(out, "survey_table_top.png"));
		ImageIO.write(side, "png", new File(out, "survey_table_side.png"));
		ImageIO.write(front, "png", new File(out, "survey_table_front.png"));

		// Preview: a fake isometric-ish block (top, front, side) at 12x.
		int s = 12;
		BufferedImage preview = new BufferedImage(3 * 16 * s + 32, 16 * s, BufferedImage.TYPE_INT_ARGB);
		var g = preview.createGraphics();
		g.drawImage(top, 0, 0, 16 * s, 16 * s, null);
		g.drawImage(front, 16 * s + 16, 0, 16 * s, 16 * s, null);
		g.drawImage(side, 32 * s + 32, 0, 16 * s, 16 * s, null);
		ImageIO.write(preview, "png", new File(args[2]));
	}

	static BufferedImage paint(BufferedImage base, String[] rows) {
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				char ch = x < rows[y].length() ? rows[y].charAt(x) : '.';
				Integer color = COLORS.get(ch);
				image.setRGB(x, y, color == null ? base.getRGB(x, y) | 0xFF000000 : 0xFF000000 | color);
			}
		}
		return image;
	}
}
