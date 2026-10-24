import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * Builds copper golem trim base textures (64x64, golem UV layout) from the vanilla humanoid armour trim textures.
 * Pixels are copied, never blended, so the palette key colours survive and the game's palette swap still works.
 * args: vanillaTexturesDir outDir previewDir
 */
public class TrimGen {
	enum V { TOP, BOTTOM, SCALE, MID }

	record Box(int u, int v, int w, int h, int d) {}

	static File vanilla;

	public static void main(String[] args) throws Exception {
		vanilla = new File(args[0]);
		File out = new File(args[1]);
		File preview = new File(args[2]);
		out.mkdirs();
		preview.mkdirs();
		V headMode = args.length > 3 ? V.valueOf(args[3]) : V.TOP;
		File[] patterns = new File(vanilla, "trims/entity/humanoid").listFiles((dir, name) -> name.endsWith(".png"));
		BufferedImage golem = ImageIO.read(new File(vanilla, "entity/copper_golem/copper_golem.png"));
		int[] keys = row(ImageIO.read(new File(vanilla, "palettes/trim_base.png")));
		int[] iron = row(ImageIO.read(new File(vanilla, "palettes/trim/gold.png")));
		BufferedImage sheet = new BufferedImage(18 * 300, 20 * 10, BufferedImage.TYPE_INT_ARGB);
		int index = 0;
		for (File file : patterns) {
			String name = file.getName();
			BufferedImage humanoid = ImageIO.read(file);
			BufferedImage dst = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
			// head (8x5x10) <- helmet (8x8x8)
			box(humanoid, new Box(0, 0, 8, 8, 8), dst, new Box(0, 0, 8, 5, 10), headMode);
			// body (8x6x6) <- chestplate body (8x12x4), the chest part
			box(humanoid, new Box(16, 16, 8, 12, 4), dst, new Box(0, 15, 8, 6, 6), V.TOP);
			// arms (3x10x4) <- chestplate arm (4x12x4), from the shoulder down
			box(humanoid, new Box(40, 16, 4, 12, 4), dst, new Box(36, 16, 3, 10, 4), V.TOP);
			box(humanoid, new Box(40, 16, 4, 12, 4), dst, new Box(50, 16, 3, 10, 4), V.TOP);
			// legs (4x5x4) <- boots (bottom of the humanoid leg)
			box(humanoid, new Box(0, 16, 4, 12, 4), dst, new Box(0, 27, 4, 5, 4), V.BOTTOM);
			box(humanoid, new Box(0, 16, 4, 12, 4), dst, new Box(16, 27, 4, 5, 4), V.BOTTOM);
			snapToKeys(dst, keys);
			ImageIO.write(dst, "png", new File(out, name));
			Files.writeString(new File(out, name + ".mcmeta").toPath(), "{\n  \"palette\": {\n    \"base_palette\": \"trim_base\"\n  }\n}\n");

			BufferedImage tinted = swap(dst, keys, iron);
			drawFigure(sheet, golem, tinted, index * 300, false);
			drawFigure(sheet, golem, tinted, index * 300 + 150, true);
			index++;
		}
		ImageIO.write(sheet, "png", new File(preview, "trims_" + headMode + ".png"));
	}

	/** Some vanilla patterns use greys that are not palette keys (and so never get coloured); snap them to the nearest key. */
	static void snapToKeys(BufferedImage image, int[] keys) {
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				int argb = image.getRGB(x, y);
				if (argb >>> 24 == 0) continue;
				int grey = argb & 0xFF;
				int best = keys[1];
				for (int i = 1; i < keys.length; i++) {
					if (Math.abs((keys[i] & 0xFF) - grey) < Math.abs((best & 0xFF) - grey)) best = keys[i];
				}
				image.setRGB(x, y, (argb & 0xFF000000) | (best & 0xFFFFFF));
			}
		}
	}

	static int[] row(BufferedImage image) {
		int[] colors = new int[image.getWidth()];
		for (int x = 0; x < colors.length; x++) {
			colors[x] = image.getRGB(x, 0);
		}
		return colors;
	}

	static BufferedImage swap(BufferedImage src, int[] keys, int[] colors) {
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 0; i < keys.length; i++) {
			map.put(keys[i] & 0xFFFFFF, colors[i] & 0xFFFFFF);
		}
		BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < src.getHeight(); y++) {
			for (int x = 0; x < src.getWidth(); x++) {
				int argb = src.getRGB(x, y);
				int alpha = argb >>> 24;
				if (alpha == 0) continue;
				Integer to = map.get(argb & 0xFFFFFF);
				out.setRGB(x, y, alpha << 24 | (to == null ? argb & 0xFFFFFF : to));
			}
		}
		return out;
	}

	/** Copies the six faces of a box. */
	static void box(BufferedImage src, Box s, BufferedImage dst, Box t, V mode) {
		// top and bottom: width x depth
		face(src, s.u + s.d, s.v, s.w, s.d, dst, t.u + t.d, t.v, t.w, t.d, V.SCALE);
		face(src, s.u + s.d + s.w, s.v, s.w, s.d, dst, t.u + t.d + t.w, t.v, t.w, t.d, V.SCALE);
		// sides: depth x height
		face(src, s.u, s.v + s.d, s.d, s.h, dst, t.u, t.v + t.d, t.d, t.h, mode);
		face(src, s.u + s.d + s.w, s.v + s.d, s.d, s.h, dst, t.u + t.d + t.w, t.v + t.d, t.d, t.h, mode);
		// front and back: width x height
		face(src, s.u + s.d, s.v + s.d, s.w, s.h, dst, t.u + t.d, t.v + t.d, t.w, t.h, mode);
		face(src, s.u + 2 * s.d + s.w, s.v + s.d, s.w, s.h, dst, t.u + 2 * t.d + t.w, t.v + t.d, t.w, t.h, mode);
	}

	static void face(BufferedImage src, int sx, int sy, int sw, int sh, BufferedImage dst, int dx, int dy, int dw, int dh, V mode) {
		for (int y = 0; y < dh; y++) {
			int fy = switch (mode) {
				case TOP -> dh <= sh ? y : y * sh / dh;
				case BOTTOM -> dh <= sh ? sh - dh + y : y * sh / dh;
				case MID -> dh <= sh ? (sh - dh) / 2 + y : y * sh / dh;
				case SCALE -> y * sh / dh;
			};
			for (int x = 0; x < dw; x++) {
				int fx = dw <= sw ? (sw - dw) / 2 + x : x * sw / dw;
				dst.setRGB(dx + x, dy + y, src.getRGB(sx + fx, sy + fy));
			}
		}
	}

	/** A crude front (or back) view of the golem, 10x scaled, with the trim on top. */
	static void drawFigure(BufferedImage sheet, BufferedImage golem, BufferedImage trim, int ox, boolean back) {
		int s = 10;
		// head front 8x5 at (10,0) uv front (10,10); back (28,10)
		blit(sheet, golem, trim, back ? 28 : 10, 10, 8, 5, ox + 3 * s, 2 * s, s);
		// body front (6,21) 8x6 ; back (20,21)
		blit(sheet, golem, trim, back ? 20 : 6, 21, 8, 6, ox + 3 * s, 7 * s, s);
		// arms front (40,20) 3x10 and (54,20)
		blit(sheet, golem, trim, back ? 47 : 40, 20, 3, 10, ox, 7 * s, s);
		blit(sheet, golem, trim, back ? 61 : 54, 20, 3, 10, ox + 11 * s, 7 * s, s);
		// legs front (4,31) 4x5 and (20,31)
		blit(sheet, golem, trim, back ? 12 : 4, 31, 4, 5, ox + 3 * s, 13 * s, s);
		blit(sheet, golem, trim, back ? 28 : 20, 31, 4, 5, ox + 7 * s, 13 * s, s);
	}

	static void blit(BufferedImage sheet, BufferedImage golem, BufferedImage trim, int u, int v, int w, int h, int x0, int y0, int s) {
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int c = golem.getRGB(u + x, v + y);
				int t = trim.getRGB(u + x, v + y);
				if (t >>> 24 != 0) c = t;
				for (int yy = 0; yy < s; yy++) for (int xx = 0; xx < s; xx++) {
					int px = x0 + x * s + xx, py = y0 + y * s + yy;
					if (px >= 0 && px < sheet.getWidth() && py < sheet.getHeight()) sheet.setRGB(px, py, c | 0xFF000000);
				}
			}
		}
	}
}
