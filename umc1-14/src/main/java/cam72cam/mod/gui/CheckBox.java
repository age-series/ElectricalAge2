package cam72cam.mod.gui;

import cam72cam.mod.util.Hand;

public abstract class CheckBox extends Button {
    public CheckBox(IScreenBuilder builder, int x, int y, String text, boolean enabled) {
        super(builder, x-25, y, 200, 20, (enabled ? "X" : "█") + " " + text);
    }

    public boolean isChecked() {
        return button.getMessage().contains("X");
    }

    @Override
    protected void onClickInternal(Hand hand) {
        this.setChecked(!this.isChecked());
        super.onClickInternal(hand);
    }

    public void setChecked(boolean val) {
        if (val) {
            button.setMessage(button.getMessage().replace("█", "X"));
        } else {
            button.setMessage(button.getMessage().replace("X", "█"));
        }
    }
}
