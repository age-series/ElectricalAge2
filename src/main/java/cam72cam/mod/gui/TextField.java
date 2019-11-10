package cam72cam.mod.gui;

import cam72cam.mod.util.Hand;
import com.google.common.base.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.widget.Widget;

public class TextField extends Button {
    public TextField(IScreenBuilder builder, int x, int y, int width, int height) {
        super(builder, create(builder, x, y, width, height));
        builder.addTextField(this);
    }

    static TextFieldWidget create(IScreenBuilder builder, int x, int y, int width, int height) {
        return new TextFieldWidget(Minecraft.getInstance().fontRenderer, builder.getWidth() / 2 + x, builder.getHeight() / 4 + y, width, height, "");
    }

    TextFieldWidget internal() {
        return (TextFieldWidget) button;
    }

    public void setValidator(Predicate<String> filter) {
        internal().setValidator(filter);
    }

    public void setFocused(boolean b) {
        internal().setFocused2(b);
    }

    public String getText() {
        return internal().getText();
    }

    public void setText(String s) {
        internal().setText(s);
    }

    @Override
    public void onClick(Hand hand) {

    }

    public void setVisible(Boolean visible) {
        textfield.setVisible(visible);
    }
}
