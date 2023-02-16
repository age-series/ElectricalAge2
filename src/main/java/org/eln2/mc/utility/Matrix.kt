package org.eln2.mc.utility

import com.mojang.math.Matrix4f
import com.mojang.math.Quaternion
import com.mojang.math.Vector3f
import net.minecraft.world.phys.Vec3
import org.eln2.mc.extensions.Vec3Extensions.toVector3f

object Matrix {
    fun translation(position : Vector3f) : Matrix4f{
        return Matrix4f.createTranslateMatrix(position.x(), position.y(), position.z())
    }

    fun translation(position: Vec3) : Matrix4f{
        return translation(position.toVector3f())
    }

    fun rotation(quaternion: Quaternion) : Matrix4f{
        return Matrix4f(quaternion)
    }
}
