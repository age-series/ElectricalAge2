package cam72cam.mod.block;

import cam72cam.mod.block.tile.TileEntity;
import cam72cam.mod.block.tile.TileEntityTickable;
import cam72cam.mod.resource.Identifier;

public abstract class BlockEntityTickable extends BlockEntity {
    public abstract void update();

    protected TileEntity supplier(Identifier id) {
        return new TileEntityTickable(id);
    }
}
