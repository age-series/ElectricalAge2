package org.eln2.mc.client.gui.library.controls

import org.eln2.mc.client.gui.library.AbstractWindow
import org.eln2.mc.client.gui.library.Point2I

abstract class AbstractCheckBox(
        parent : AbstractWindow,
        pos : Point2I,
        var text : String,
        var checked : Boolean = false
    ) : AbstractControl(parent, pos) {
}
