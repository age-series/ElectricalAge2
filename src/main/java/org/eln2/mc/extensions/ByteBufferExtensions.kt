package org.eln2.mc.extensions

import net.minecraft.network.FriendlyByteBuf
import org.eln2.mc.Eln2
import org.eln2.mc.utility.DataLabelBuilder
import org.eln2.mc.utility.McColor

object ByteBufferExtensions {
    fun FriendlyByteBuf.writeString(string: String) : FriendlyByteBuf{
        return this.writeByteArray(string.toByteArray(charset = Charsets.UTF_8))
    }

    fun FriendlyByteBuf.readString() : String{
        return this.readByteArray().toString(charset = Charsets.UTF_8)
    }

    fun FriendlyByteBuf.readColor() : McColor{
        return McColor.deserialize(this)
    }

    fun FriendlyByteBuf.writeColor(color : McColor) : FriendlyByteBuf{
        return color.serialize(this)
    }

    fun FriendlyByteBuf.readDataLabelBuilder() : DataLabelBuilder {
        val count = this.readInt()

        Eln2.LOGGER.info("Builder size: $count")

        val builder = DataLabelBuilder()

        if(count == 0){
            return builder
        }

        for (i in 0 until count){
            val label = this.readString()
            val value = this.readString()
            val color = this.readColor()
            Eln2.LOGGER.info("lbl: $label, value: $value, color: $color")
            builder.entry(DataLabelBuilder.Entry(label, value, color))
        }

        return builder
    }

    fun FriendlyByteBuf.writeDataLabelBuilder(builder: DataLabelBuilder) : FriendlyByteBuf{
        this.writeInt(builder.entries.count())

        if(builder.entries.isEmpty()){
            return this
        }

        builder.entries.forEach{entry ->
            this.writeString(entry.label)
            this.writeString(entry.value)
            this.writeColor(entry.color)
        }

        return this
    }

}
