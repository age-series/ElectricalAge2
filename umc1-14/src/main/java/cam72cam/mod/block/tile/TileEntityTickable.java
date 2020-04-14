package cam72cam.mod.block.tile;

import cam72cam.mod.ModCore;
import cam72cam.mod.block.BlockEntityTickable;
import cam72cam.mod.resource.Identifier;
import net.minecraft.tileentity.ITickableTileEntity;

public class TileEntityTickable extends TileEntity implements ITickableTileEntity {
    public TileEntityTickable(Identifier id) {
        super(id);
    }

    private BlockEntityTickable tickable;
    @Override
    public void tick() {
        BlockEntityTickable tickable = (BlockEntityTickable) instance();
        if (tickable == null) {
            tickable = (BlockEntityTickable) instance();
            if (tickable == null) {
                ModCore.debug("uhhhhh, null tickable?");
                return;
            }
        }
        tickable.update();
    }
}
