package org.eln2.mc.extensions

import net.minecraft.network.FriendlyByteBuf

object ByteBufferExtensions {
    fun FriendlyByteBuf.writeString(string: String){
        this.writeByteArray(string.toByteArray(charset = Charsets.UTF_8))
    }

    fun FriendlyByteBuf.readString() : String{
        return this.readByteArray().toString(charset = Charsets.UTF_8)
    }

    fun FriendlyByteBuf.writeStringMap(map: Map<String, String>) {
        val stringToFbb = { fbb: FriendlyByteBuf, str: String -> fbb.writeString(str) }
        this.writeMap(map, stringToFbb, stringToFbb)
    }

    fun FriendlyByteBuf.readStringMap(): Map<String, String> {
        val fbbToString = {fbb: FriendlyByteBuf -> fbb.readString()}
        return this.readMap(fbbToString, fbbToString)
    }
}
