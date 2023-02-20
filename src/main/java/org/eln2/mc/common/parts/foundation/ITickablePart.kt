package org.eln2.mc.common.parts.foundation

import org.eln2.mc.common.blocks.foundation.MultipartBlockEntity

/**
 * Represents a part that can be ticked by the multipart block entity.
 * @see MultipartBlockEntity.addTicker
 * @see MultipartBlockEntity.removeTicker
 * */
interface ITickablePart {
    fun tick()
}
