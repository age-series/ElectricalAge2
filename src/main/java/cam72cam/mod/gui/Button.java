package cam72cam.mod.gui;

import cam72cam.mod.util.Hand;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.AbstractButton;

import java.util.function.Consumer;

public abstract class Button {
    final Widget button;

    private static class InternalButton extends AbstractButton {
        private Consumer<Hand> clicker = hand -> {};

        public InternalButton(int xIn, int yIn, int widthIn, int heightIn, String msg) {
            super(xIn, yIn, widthIn, heightIn, msg);
        }

        @Override
        protected boolean isValidClickButton(int p_isValidClickButton_1_) {
            return p_isValidClickButton_1_ == 1 || p_isValidClickButton_1_ == 0;
        }

        @Override
        public boolean mouseClicked(double p_mouseClicked_1_, double p_mouseClicked_3_, int p_mouseClicked_5_) {
            if (this.active && this.visible) {
                if (this.isValidClickButton(p_mouseClicked_5_)) {
                    boolean flag = this.clicked(p_mouseClicked_1_, p_mouseClicked_3_);
                    if (flag) {
                        this.playDownSound(Minecraft.getInstance().getSoundHandler());
                        clicker.accept(p_mouseClicked_5_ == 0 ? Hand.PRIMARY : Hand.SECONDARY);
                        return true;
                    }
                }

                return false;
            } else {
                return false;
            }
        }

        @Override
        public void onPress() {
            clicker.accept(Hand.PRIMARY);
        }
    }

    public Button(IScreenBuilder builder, int x, int y, String text) {
        this(builder, x, y, 200, 20, text);
    }

    public Button(IScreenBuilder builder, int x, int y, int width, int height, String text) {
        this(builder, new InternalButton(builder.getWidth() / 2 + x, builder.getHeight() / 4 + y, width, height, text));
        ((InternalButton)this.button).clicker = this::onClick;
    }

    Button(IScreenBuilder builder, Widget button) {
        this.button = button;
        builder.addButton(this);
    }

    public void setText(String text) {
        button.setMessage(text);
    }

    public abstract void onClick(Hand hand);

    Widget internal() {
        return button;
    }

    public void onUpdate() {

    }

    public void setTextColor(int i) {
        button.setFGColor(i);
    }

    public void setVisible(boolean b) {
        button.visible = b;
    }

    ;
}
