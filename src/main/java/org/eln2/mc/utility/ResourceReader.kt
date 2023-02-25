package org.eln2.mc.utility

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.Resource
import java.io.InputStream
import java.nio.charset.Charset

object ResourceReader {
    fun getResource(location: ResourceLocation): Resource {
        val manager = Minecraft.getInstance().resourceManager

        return manager.getResource(location)
    }

    fun getResourceStream(location: ResourceLocation): InputStream {
        return getResource(location).inputStream
    }

    fun getResourceString(location: ResourceLocation): String{
        return getResourceStream(location).readAllBytes().toString(Charset.defaultCharset())
    }
}
