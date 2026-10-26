package dev.mioscoppergolems.client.screen;

import dev.mioscoppergolems.MiosCoppergolems;
import dev.mioscoppergolems.api.MiosCoppergolemsApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

/**
 * The Golem Handbook, shown in the vanilla book screen. The pages are laid out here from the translated chapter texts:
 * contents first (click a chapter to jump there), then each chapter starting on a new page. Reopening the book goes
 * back to the page you left it at.
 */
public class HandbookScreen extends BookViewScreen {
	private static final List<String> CHAPTERS = List.of(
		"welcome", "homes", "sorter", "cart", "miner", "farmer", "workers", "courier", "travel", "danger", "upkeep", "tools", "trims", "reference"
	);
	private static final int LINE_WIDTH = TEXT_WIDTH;
	private static final int LINES_PER_PAGE = TEXT_HEIGHT / 9;
	private static final Style TITLE = Style.EMPTY.withBold(true).withColor(0x8A4B2E);
	private static final Style HEADING = Style.EMPTY.withBold(true).withColor(0x2F6B57);
	private static final Style CONTENTS = Style.EMPTY.withColor(0x2F6B57);

	private static int lastPage;
	private static int lastPageCount;
	private int page;

	private HandbookScreen(List<Component> pages) {
		super(new BookAccess(pages));
		// The same book as last time (same language, same add-ons): open it where it was left.
		this.page = pages.size() == lastPageCount ? lastPage : 0;
		lastPageCount = pages.size();
	}

	@Override
	protected void init() {
		super.init();
		this.setPage(this.page);
	}

	public static void open() {
		Minecraft minecraft = Minecraft.getInstance();
		minecraft.gui.setScreen(new HandbookScreen(layOut(minecraft.font)));
	}

	@Override
	protected void pageBack() {
		super.pageBack();
		this.page = Math.max(0, this.page - 1);
	}

	@Override
	protected void pageForward() {
		super.pageForward();
		this.page = Math.min(lastPageCount - 1, this.page + 1);
	}

	@Override
	protected boolean forcePage(int page) {
		boolean changed = super.forcePage(page);
		this.page = Math.clamp(page, 0, lastPageCount - 1);
		return changed;
	}

	@Override
	public void onClose() {
		lastPage = this.page;
		super.onClose();
	}

	private static List<Identifier> chapters() {
		List<Identifier> chapters = new ArrayList<>();
		CHAPTERS.forEach(name -> chapters.add(MiosCoppergolems.id(name)));
		chapters.addAll(MiosCoppergolemsApi.handbookChapters());
		return chapters;
	}

	private static String key(Identifier chapter, String part) {
		return "handbook." + chapter.getNamespace() + "." + chapter.getPath() + "." + part;
	}

	private static List<Component> layOut(Font font) {
		List<Identifier> chapters = chapters();
		// The contents come first, so their length decides where the chapters start.
		List<List<Component>> body = new ArrayList<>();
		List<Integer> starts = new ArrayList<>();
		for (Identifier chapter : chapters) {
			starts.add(body.size());
			body.addAll(layOutChapter(font, chapter));
		}
		List<Component> contentLines = new ArrayList<>();
		contentLines.addAll(wrap(font, I18n.get("handbook.mioscoppergolems.title"), TITLE));
		contentLines.add(Component.empty());
		int contentPages = Math.max(1, (contentLines.size() + chapters.size() + LINES_PER_PAGE - 1) / LINES_PER_PAGE);
		for (int i = 0; i < chapters.size(); i++) {
			int target = contentPages + starts.get(i) + 1;
			String title = font.plainSubstrByWidth("• " + I18n.get(key(chapters.get(i), "title")), LINE_WIDTH);
			contentLines.add(Component.literal(title).withStyle(CONTENTS
				.withClickEvent(new ClickEvent.ChangePage(target))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("book.pageIndicator", target, contentPages + body.size())))));
		}
		List<Component> pages = new ArrayList<>();
		for (int i = 0; i < contentLines.size(); i += LINES_PER_PAGE) {
			pages.add(join(contentLines.subList(i, Math.min(contentLines.size(), i + LINES_PER_PAGE))));
		}
		body.forEach(lines -> pages.add(join(lines)));
		return pages;
	}

	/** A chapter's pages, as lines. A heading never ends up alone at the bottom of a page. */
	private static List<List<Component>> layOutChapter(Font font, Identifier chapter) {
		List<List<Component>> pages = new ArrayList<>();
		List<Component> page = new ArrayList<>(wrap(font, I18n.get(key(chapter, "title")), TITLE));
		page.add(Component.empty());
		for (String line : I18n.get(key(chapter, "text")).split("\n")) {
			List<Component> wrapped;
			boolean heading = line.startsWith("# ");
			if (line.isBlank()) {
				wrapped = List.of(Component.empty());
			} else if (heading) {
				wrapped = wrap(font, line.substring(2), HEADING);
			} else {
				wrapped = wrap(font, line, Style.EMPTY);
			}
			for (int i = 0; i < wrapped.size(); i++) {
				boolean full = page.size() >= LINES_PER_PAGE || heading && i == 0 && page.size() >= LINES_PER_PAGE - 1;
				if (full) {
					pages.add(page);
					page = new ArrayList<>();
				}
				if (page.isEmpty() && wrapped.get(i).getString().isEmpty()) {
					// No blank line at the top of a page.
					continue;
				}
				page.add(wrapped.get(i));
			}
		}
		if (!page.isEmpty()) {
			pages.add(page);
		}
		return pages;
	}

	private static List<Component> wrap(Font font, String text, Style style) {
		List<Component> lines = new ArrayList<>();
		for (FormattedText part : font.getSplitter().splitLines(text, LINE_WIDTH, style)) {
			MutableComponent line = Component.empty();
			part.visit((partStyle, string) -> {
				line.append(Component.literal(string).withStyle(partStyle));
				return Optional.empty();
			}, style);
			lines.add(line);
		}
		return lines;
	}

	private static Component join(List<Component> lines) {
		MutableComponent page = Component.empty();
		for (int i = 0; i < lines.size(); i++) {
			if (i > 0) {
				page.append("\n");
			}
			page.append(lines.get(i));
		}
		return page;
	}
}
