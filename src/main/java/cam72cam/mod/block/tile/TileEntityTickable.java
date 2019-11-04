package cam72cam.mod.block.tile;

import cam72cam.mod.ModCore;
import cam72cam.mod.block.BlockEntityTickable;
import cam72cam.mod.resource.Identifier;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.tileentity.TileEntityType;

public class TileEntityTickable extends TileEntity implements ITickableTileEntity {
    public TileEntityTickable(TileEntityType type) {
        super(type);
    }

    public TileEntityTickable(TileEntityType type, Identifier id) {
        super(type, id);
    }

    @Override
    public void tick() {
        BlockEntityTickable tickable = (BlockEntityTickable) instance();
        if (tickable == null) {
            System.out.println("uhhhhh, null tickable?");
            return;
        }
        tickable.update();
    }
}
