package cam72cam.mod.item;

public enum ToolType {
    SHOVEL(net.minecraftforge.common.ToolType.SHOVEL),
    ;
    final net.minecraftforge.common.ToolType internal;

    ToolType(net.minecraftforge.common.ToolType internal) {
        this.internal = internal;
    }

    public String toString() {
        return this.internal.getName();
    }
}
