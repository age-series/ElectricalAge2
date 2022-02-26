package org.eln2.mc.client.gui.library.controls

import org.eln2.mc.client.gui.library.AbstractWindow
import org.eln2.mc.client.gui.library.MouseInfo
import org.eln2.mc.client.gui.library.Point2I

abstract class AbstractButton(
    parent : AbstractWindow,
    pos : Point2I,
    var text : String,
    var marginVertical : Int = 2,
    var marginHorizontal : Int = 2,
    var click : ((ClickEvent) -> Unit)
    ) : AbstractControl(parent, pos) {

    class ClickEvent(info : MouseInfo, pos : Point2I)

}
