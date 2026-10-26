import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Copper golem overlay textures (64x64, same layout as the vanilla golem):
 * <ul>
 * <li>eyes_iron/gold/diamond: the vanilla glowing eyes recoloured for the eye upgrades. The vanilla eyes use three
 * shades (dark rim, middle, bright centre); each tier gets its own three.</li>
 * <li>antenna_glow: the round knob on top of the antenna, lit up, for an excited miner. The base texture's shading is
 * kept so the knob still looks round.</li>
 * </ul>
 * args: vanillaCopperGolemTextureDir outDir
 */
public class GolemEyesGen {
	/** Vanilla eye shades, dark to bright. */
	static final int[] VANILLA = {0xFFA851, 0xFFDD99, 0xFFF7D2};
	static final int[][] TIERS = {
		{0x9AA3AD, 0xD3D9DF, 0xFFFFFF},
		{0xF2A900, 0xFFD94A, 0xFFF4B0},
		{0x1FC9C0, 0x7FF2EA, 0xD6FFFC},
	};
	static final String[] NAMES = {"iron", "gold", "diamond"};
	static final int GLOW = 0xFFE45C;

	public static void main(String[] args) throws Exception {
		File dir = new File(args[0]);
		File out = new File(args[1]);
		out.mkdirs();
		BufferedImage eyes = ImageIO.read(new File(dir, "copper_golem_eyes.png"));
		for (int tier = 0; tier < TIERS.length; tier++) {
			BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					int argb = eyes.getRGB(x, y);
					if (argb >>> 24 == 0) {
						continue;
					}
					image.setRGB(x, y, 0xFF000000 | TIERS[tier][shade(argb & 0xFFFFFF)]);
				}
			}
			ImageIO.write(image, "png", new File(out, "eyes_" + NAMES[tier] + ".png"));
		}
		BufferedImage golem = ImageIO.read(new File(dir, "copper_golem.png"));
		BufferedImage glow = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
		// The knob is a 4x4x4 box at texture offset 37,0: top and bottom faces in the first row, the sides below.
		for (int y = 0; y < 8; y++) {
			for (int x = 37; x < 53; x++) {
				boolean face = y >= 4 || x >= 41 && x < 49;
				int base = golem.getRGB(x, y);
				if (!face || base >>> 24 == 0) {
					continue;
				}
				double light = ((base >> 16 & 0xFF) * 0.3 + (base >> 8 & 0xFF) * 0.59 + (base & 0xFF) * 0.11) / 255.0;
				glow.setRGB(x, y, 0xFF000000 | scale(GLOW, 0.65 + light * 0.6));
			}
		}
		ImageIO.write(glow, "png", new File(out, "antenna_glow.png"));
	}

	static int shade(int rgb) {
		for (int i = 0; i < VANILLA.length; i++) {
			if (VANILLA[i] == rgb) {
				return i;
			}
		}
		return 1;
	}

	static int scale(int rgb, double factor) {
		int r = (int) Math.min(255, (rgb >> 16 & 0xFF) * factor);
		int g = (int) Math.min(255, (rgb >> 8 & 0xFF) * factor);
		int b = (int) Math.min(255, (rgb & 0xFF) * factor);
		return r << 16 | g << 8 | b;
	}
}
