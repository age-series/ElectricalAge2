package cam72cam.mod.entity.custom;

import cam72cam.mod.entity.boundingbox.IBoundingBox;
import net.minecraft.util.math.AxisAlignedBB;

public interface ICollision {
    ICollision NOP = () -> IBoundingBox.from(new AxisAlignedBB(0.0D, 0.0D, 0.0D, 0.0D, 0.0D, 0.0D));

    static ICollision get(Object o) {
        if (o instanceof ICollision) {
            return (ICollision) o;
        }
        return NOP;
    }

    IBoundingBox getCollision();
}
