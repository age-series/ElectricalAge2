@file:Suppress("unused")

package org.eln2.mc.common.content.modules

import org.ageseries.libage.data.CELSIUS
import org.ageseries.libage.data.METER
import org.ageseries.libage.data.MILLI
import org.ageseries.libage.data.Quantity
import org.eln2.mc.common.content.HeatingElementBuilder
import org.eln2.mc.common.content.BurntHeatingElementItem
import org.eln2.mc.common.items.ItemRegistry.item

object Eln2HeatingElements : ContentModule() {
    val BURNT_HEATING_ELEMENT = item("burnt_heating_element") { BurntHeatingElementItem() }

    val LOW_VOLTAGE_IRON_HEATING_ELEMENT = HeatingElementBuilder("low_voltage_iron_heating_element")
        .setMaxTemperature(Quantity(1100.0, CELSIUS))
        .setGeometry(Quantity(5.0, METER), Quantity(1.25, MILLI * METER))
        .setResistivityCurve {
            withPoint(273.15, 8.5e-8)
            withPoint(293.15, 9.7e-8)
            withPoint(373.15, 1.47e-7)
            withPoint(473.15, 2.3e-7)
            withPoint(573.15, 3.4e-7)
            withPoint(673.15, 4.7e-7)
            withPoint(773.15, 6.2e-7)
            withPoint(873.15, 8.0e-7)
            withPoint(973.15, 1.02e-6)
            withPoint(1043.15, 1.045e-6)
            withPoint(1073.15, 1.05e-6)
            withPoint(1185.15, 1.07e-6)
            withPoint(1273.15, 1.12e-6)
            withPoint(1473.15, 1.18e-6)
            withPoint(1667.15, 1.22e-6)
            withPoint(1773.15, 1.25e-6)
            withPoint(1811.15, 1.27e-6)
            withPoint(1812.15, 1.37e-6)
            withPoint(1873.15, 1.38e-6)
        }
        .register()
}
