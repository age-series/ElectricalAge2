using Vintagestory.API.Common;
using Vintagestory.API.Common.Entities;
using Vintagestory.API.MathTools;

namespace org.Eln2 {
    public class Flubber : Block {
        private AssetLocation tickSound = new AssetLocation("game", "sounds/tick");

        public override void OnEntityCollide(IWorldAccessor world, Entity entity, BlockPos pos, BlockFacing facing, Vec3d collideSpeed, bool isImpact) {
            if (isImpact && facing.Axis == EnumAxis.Y) {
                world.PlaySoundAt(tickSound, entity.Pos.X, entity.Pos.Y, entity.Pos.Z);
                entity.Pos.Motion.Y *= -0.9;
            }
            if(isImpact && facing.Axis == EnumAxis.X) {
                world.PlaySoundAt(tickSound, entity.Pos.X, entity.Pos.Y, entity.Pos.Z);
                entity.Pos.Motion.X *= -5.0;
            }
            if(isImpact && facing.Axis == EnumAxis.Z) {
                world.PlaySoundAt(tickSound, entity.Pos.X, entity.Pos.Y, entity.Pos.Z);
                entity.Pos.Motion.Z *= -5.0;
            }
        }
    }
}