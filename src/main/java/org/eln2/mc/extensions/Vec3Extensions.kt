package org.eln2.mc.extensions

import net.minecraft.core.Direction
import net.minecraft.core.Direction.AxisDirection
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

object Vec3Extensions {
    //#region Operators

    operator fun Vec3.plus(b : Vec3) : Vec3{
        return Vec3(this.x + b.x, this.y + b.y, this.z + b.z);
    }

    operator fun Vec3.plus(delta : Double) : Vec3{
        return Vec3(this.x + delta, this.y + delta, this.z + delta);
    }

    operator fun Vec3.minus(b : Vec3) : Vec3{
        return Vec3(this.x - b.x, this.y - b.y, this.z - b.z);
    }

    operator fun Vec3.minus(delta : Double) : Vec3{
        return Vec3(this.x - delta, this.y - delta, this.z - delta);
    }

    operator fun Vec3.times(b : Vec3) : Vec3{
        return Vec3(this.x * b.x, this.y * b.y, this.z * b.z);
    }

    operator fun Vec3.times(scalar : Double) : Vec3{
        return Vec3(this.x * scalar, this.y * scalar, this.z * scalar);
    }

    operator fun Vec3.div(b : Vec3) : Vec3{
        return Vec3(this.x / b.x, this.y / b.y, this.z / b.z);
    }

    operator fun Vec3.div(scalar : Double) : Vec3{
        return Vec3(this.x / scalar, this.y / scalar, this.z / scalar);
    }

    //#endregion
}
