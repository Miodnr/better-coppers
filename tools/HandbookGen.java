import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Golem Handbook item texture: the vanilla book with its leather cover recoloured to copper, with a few spots of
 * verdigris. Pages (greyish pixels) stay as they are.
 * args: vanillaBookPng outPng
 */
public class HandbookGen {
	/** Copper, from dark to light. */
	static final int[] COPPER = {0x5A2A1C, 0x7C3A26, 0x9A5038, 0xC06A4B, 0xE3826C, 0xF2A88E};

	public static void main(String[] args) throws Exception {
		BufferedImage book = ImageIO.read(new File(args[0]));
		BufferedImage out = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < 16; y++) {
			for (int x = 0; x < 16; x++) {
				int argb = book.getRGB(x, y);
				int alpha = argb >>> 24;
				int r = argb >> 16 & 0xFF, g = argb >> 8 & 0xFF, b = argb & 0xFF;
				boolean leather = alpha > 0 && r > g + 12 && r > b + 12;
				if (leather) {
					double light = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0;
					int shade = (int) Math.min(COPPER.length - 1, Math.max(0, Math.round(light * 1.9 * (COPPER.length - 1))));
					argb = 0xFF000000 | COPPER[shade];
				}
				out.setRGB(x, y, argb);
			}
		}
		// A few spots of verdigris, like a golem that has been out in the rain.
		int verdigris = 0xFF000000 | 0x5BA88A;
		int[][] spots = {{3, 6}, {4, 6}, {12, 5}, {9, 9}};
		for (int[] spot : spots) {
			if (isCover(out.getRGB(spot[0], spot[1]))) {
				out.setRGB(spot[0], spot[1], verdigris);
			}
		}
		ImageIO.write(out, "png", new File(args[1]));
	}

	static boolean isCover(int argb) {
		for (int color : COPPER) {
			if ((argb & 0xFFFFFF) == color && argb >>> 24 > 0) {
				return true;
			}
		}
		return false;
	}
}
