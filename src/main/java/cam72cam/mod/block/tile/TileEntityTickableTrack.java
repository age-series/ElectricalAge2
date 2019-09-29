package cam72cam.mod.block.tile;

import cam72cam.mod.ModCore;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.util.ITrack;
import net.minecraft.tileentity.TileEntityType;

public class TileEntityTickableTrack extends TileEntityTickable implements trackapi.lib.ITrack {
    static {
        ctrs.put(TileEntityTickableTrack.class, TileEntityTickableTrack::new);
        names.put(TileEntityTickableTrack.class, new Identifier(ModCore.MODID, "hack"));
    }

    public TileEntityTickableTrack(TileEntityType type) {
        super(type);
    }

    public TileEntityTickableTrack(TileEntityType type, Identifier id) {
        super(type, id);
    }

    private trackapi.lib.ITrack track() {
        return instance() instanceof ITrack ? ((ITrack) instance()).to() : null;
    }

    @Override
    public double getTrackGauge() {
        return track() != null ? track().getTrackGauge() : 0;
    }

    @Override
    public net.minecraft.util.math.Vec3d getNextPosition(net.minecraft.util.math.Vec3d pos, net.minecraft.util.math.Vec3d mot) {
        return track() != null ? track().getNextPosition(pos, mot) : pos;
    }

    @Override
    public Identifier getName() {
        return new Identifier(ModCore.MODID, "tile_track");
    }
}
