package org.eln2.space

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class VecTests {
    @Test
    fun vec2iEqualsTest() {
        val v1 = Vec2i(2, 2)
        val v2 = Vec2i(2, 2)
        Assertions.assertEquals(true, v1 == v2)
        Assertions.assertEquals(false, v1 == Vec2i.ZERO)
    }

    @Test
    fun vec2iZeroEqualsTest() {
        val v1 = Vec2i(0, 0)
        Assertions.assertEquals(true, Vec2i.ZERO == v1)
    }

    @Test
    fun vec3iEqualsTest() {
        val v1 = Vec3i(2, 2, 2)
        val v2 = Vec3i(2, 2, 2)
        Assertions.assertEquals(true, v1 == v2)
        Assertions.assertEquals(false, v1 == Vec3i.ZERO)
    }

    @Test
    fun vec3iZeroEqualsTest() {
        val v1 = Vec3i(0, 0, 0)
        Assertions.assertEquals(true, Vec3i.ZERO == v1)
    }
}
