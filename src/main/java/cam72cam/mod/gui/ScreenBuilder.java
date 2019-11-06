package cam72cam.mod.gui;

import cam72cam.mod.fluid.Fluid;
import cam72cam.mod.gui.helpers.GUIHelpers;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.Hand;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.util.text.StringTextComponent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

class ScreenBuilder extends Screen implements IScreenBuilder {
    private final IScreen screen;
    private Map<Widget, Button> buttonMap = new HashMap<net.minecraft.client.gui.widget.Widget, Button>();

    public ScreenBuilder(IScreen screen) {
        super(new StringTextComponent(""));
        this.screen = screen;
    }

    // IScreenBuilder

    @Override
    public void close() {
        screen.onClose();

        this.minecraft.displayGuiScreen(null);
        if (this.minecraft.currentScreen == null) {
            this.minecraft.setGameFocused(true);
        }
    }

    @Override
    public void onClose() {
        screen.onClose();
        super.onClose();
    }

    @Override
    public void addButton(Button btn) {
        super.addButton(btn.internal());
        this.buttonMap.put(btn.internal(), btn);
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public void drawImage(Identifier tex, int x, int y, int width, int height) {
        this.minecraft.getTextureManager().bindTexture(tex.internal);

        GUIHelpers.texturedRect(this.width / 2 + x, this.height / 4 + y, width, height);
    }

    @Override
    public void drawTank(double x, int y, double width, double height, Fluid fluid, float fluidPercent, boolean background, int color) {
        GUIHelpers.drawTankBlock(this.width / 2 + x, this.height / 4 + y, width, height, fluid, fluidPercent, background, color);
    }

    @Override
    public void drawCenteredString(String str, int x, int y, int color) {
        super.drawCenteredString(this.font, str, this.width / 2 + x, this.height / 4 + y, color);
    }

    @Override
    public void show() {
        this.minecraft.displayGuiScreen(this);
    }

    @Override
    public void addTextField(TextField textField) {
        addButton(textField);
    }

    // GuiScreen

    @Override
    public void init() {
        screen.init(this);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        for (Button btn : buttonMap.values()) {
            btn.onUpdate();
        }

        screen.draw(this);

        // draw buttons
        super.render(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean keyPressed(int typedChar, int keyCode, int mods) {
        if (super.keyPressed(typedChar, keyCode, mods)) {
            return true;
        }
        if (keyCode == 1) {
            close();
        }

        // Enter
        if (keyCode == 28 || keyCode == 156) {
            screen.onEnterKey(this);
            return true;
        }
        return false;
    }

    // Default overrides
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
