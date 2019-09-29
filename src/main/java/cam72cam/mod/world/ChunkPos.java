package cam72cam.mod.world;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ChunkPos {
    public final int dim;
    public final int chunkX;
    public final int chunkZ;

    public ChunkPos(World world, BlockPos pos) {
        dim = world.getDimension().getType().getId();
        chunkX = pos.getX() >> 4;
        chunkZ = pos.getZ() >> 4;
    }

    public ChunkPos(Entity entity) {
        this(entity.getEntityWorld(), entity.getPosition());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ChunkPos) {
            ChunkPos other = (ChunkPos) o;
            return other.dim == dim && other.chunkX == chunkX && other.chunkZ == chunkZ;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return dim + chunkX + chunkZ;
    }
}