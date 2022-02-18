package org.eln2.mc.extensions

import org.eln2.mc.utility.ColoredStringFormatter
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColoredString
import org.eln2.mc.utility.McColors

object ColoredStringFormatterExtensions {
    fun ColoredStringFormatter.getColorsOrDefault(str : String, default : McColor = McColors.white) : McColoredString{
        return getColors(str) ?: McColoredString(str, default)
    }

    fun ColoredStringFormatter.contentOf(str : String) : String {
        return getColorsOrDefault(str).string
    }
}
