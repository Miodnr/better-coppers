package dev.bettercoppers.client.screen;

import dev.bettercoppers.menu.SurveyTableMenu;
import dev.bettercoppers.mining.MinePlan;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Uses the stonecutter background (input slot left, result slot right). The middle panel shows the plan and its length
 * picker instead of the recipe list.
 */
public class SurveyTableScreen extends AbstractContainerScreen<SurveyTableMenu> {
	private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/stonecutter.png");
	private static final int PANEL_X = 52;
	private static final int PANEL_Y = 14;
	private static final int PANEL_WIDTH = 80;
	private static final int PANEL_HEIGHT = 54;
	private static final int TEXT_COLOR = 0xFFE0E0E0;

	public SurveyTableScreen(SurveyTableMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
		this.titleLabelY--;
	}

	@Override
	protected void init() {
		super.init();
		int x = this.leftPos + PANEL_X;
		int y = this.topPos + PANEL_Y;
		this.addRenderableWidget(this.button("-", x + 2, y + 32, SurveyTableMenu.BUTTON_SHORTER));
		this.addRenderableWidget(this.button("+", x + PANEL_WIDTH - 14, y + 32, SurveyTableMenu.BUTTON_LONGER));
	}

	private Button button(String label, int x, int y, int buttonId) {
		return Button.builder(Component.literal(label), b -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId))
			.bounds(x, y, 12, 14)
			.build();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
		// Cover the recipe list and scroll bar with a plain dark panel.
		int x = this.leftPos + PANEL_X;
		int y = this.topPos + PANEL_Y;
		graphics.fill(x, y, x + PANEL_WIDTH, y + PANEL_HEIGHT, 0xFF3A3A3A);
		graphics.fill(x + 1, y + 1, x + PANEL_WIDTH - 1, y + PANEL_HEIGHT - 1, 0xFF5B4636);
	}

	@Override
	protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
		super.extractLabels(graphics, xm, ym);
		int center = PANEL_X + PANEL_WIDTH / 2;
		Component pattern = this.menu.getPattern().displayName();
		graphics.centeredText(this.font, pattern, center, PANEL_Y + 7, TEXT_COLOR);
		graphics.centeredText(this.font, Component.translatable("screen.bettercoppers.length"), center, PANEL_Y + 24, 0xFFB0A090);
		graphics.centeredText(this.font, Component.literal(String.valueOf(this.menu.getLength())), center, PANEL_Y + 35, TEXT_COLOR);
		if (this.menu.getLength() == MinePlan.MAX_LENGTH) {
			graphics.centeredText(this.font, Component.literal("max"), center, PANEL_Y + 45, 0xFFB0A090);
		}
	}
}
