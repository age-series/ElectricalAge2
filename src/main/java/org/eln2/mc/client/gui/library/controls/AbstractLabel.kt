package org.eln2.mc.client.gui.library.controls

import org.eln2.mc.client.gui.library.AbstractWindow
import org.eln2.mc.client.gui.library.Point2I
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

abstract class AbstractLabel (
        parent : AbstractWindow,
        pos : Point2I,
        var text : String,
        var color : McColor = McColors.cyan,
        val mode : LabelAlignMode = LabelAlignMode.FromLeft
    ) : AbstractControl(parent, pos){

    enum class LabelAlignMode{
        FromLeft,
        FromCenter
    }
}
