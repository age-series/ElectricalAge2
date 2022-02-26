package org.eln2.mc.client.gui.library.extensions

import org.eln2.mc.client.gui.library.DynamicWindow
import org.eln2.mc.client.gui.library.Point2I
import org.eln2.mc.client.gui.library.controls.*
import org.eln2.mc.utility.McColor
import org.eln2.mc.utility.McColors

object WindowExtensions {
    fun DynamicWindow.withSkin(title : String) : DynamicWindow{
        this.addControl(ControlSkin(this, Point2I.zero, title = title))
        return this
    }

    fun DynamicWindow.withLabel(text : String, position : Point2I, color : McColor = McColors.cyan, mode : AbstractLabel.LabelAlignMode = AbstractLabel.LabelAlignMode.FromLeft) : Label{
        val label = Label(this, position, text, color, mode)
        this.addControl(label)
        return label
    }

    fun DynamicWindow.withButton(text : String, position : Point2I, click : ((AbstractButton.ClickEvent) -> Unit), textColor : McColor = McColors.white, backColor : McColor = McColor(20, 20, 20, 200)) : Button{
        val button = Button(this, position, text, click = click, color = textColor, backgroundColor = backColor)
        this.addControl(button)
        return button
    }

    fun DynamicWindow.withCheckBox(text : String, position: Point2I, checked : Boolean) : CheckBox{
        val checkBox = CheckBox(this, position, text, checked = checked)
        this.addControl(checkBox)
        return checkBox
    }
}
