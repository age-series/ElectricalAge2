package cam72cam.mod.util;


public enum Hand {
    PRIMARY(net.minecraft.util.Hand.MAIN_HAND),
    SECONDARY(net.minecraft.util.Hand.OFF_HAND),
    ;

    public final net.minecraft.util.Hand internal;

    Hand(net.minecraft.util.Hand internal) {
        this.internal = internal;
    }

    public static Hand from(net.minecraft.util.Hand hand) {
        switch (hand) {
            case MAIN_HAND:
                return PRIMARY;
            case OFF_HAND:
                return SECONDARY;
            default:
                return null;
        }
    }
}
