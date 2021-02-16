package org.eln2.sim

import kotlin.math.pow

/**
 * Material
 *
 * Contains data about materials that we need for various calculations such as electrical and thermal simulations
 */
enum class Material {
	COPPER {
		override val resistivity: Double = 1.68 * 10.0.pow(-8.0)
		override val thermalConductivity: Double = 385.0
        override val specficHeat: Double = 0.0
        override val density: Double = 8940.0
	},
	RUBBER {
		override val resistivity: Double = 1 * 10.0.pow(13.0)
		override val thermalConductivity: Double = 0.15
        override val specficHeat: Double = 0.0
        override val density: Double = 1522.0
	},
	IRON {
		override val resistivity: Double = 9.71 * 10.0.pow(-8.0)
		override val thermalConductivity: Double = 79.5
        override val specficHeat: Double = 0.0
        override val density: Double = 7874.0
	};

    // Density of the material (km/m^3)
    abstract val density: Double

/*
Density (STP)

H2O 	    1,000
Iron	    7,874
Copper	    8,950
Tungsten	19,250
Gold	    19,300
Platinum	21,450

Source: https://www.calculator.net/density-calculator.html
 */

	// resistivity (ohms/meter)
	abstract val resistivity: Double
/*
Electrical Resistivity
Material    Resistivity ρ (ohm m)
Silver:     1.59    x10^-8
Copper:     1.68    x10^-8
Aluminum:   2.65    x10^-8
Tungsten:   5.6     x10^-8
Iron:       9.71    x10^-8
Platinum    10.6    x10^-8
Lead:       22      x10^-8
Mercury     98      x10^-8
Glass       1-10000 x10^9
Rubber      1-100   x10^13
Source: http://hyperphysics.phy-astr.gsu.edu/hbase/Tables/rstiv.html
*/

	// thermal conductivity (W/m K)
	abstract val thermalConductivity: Double

/*
Thermal Conductivity
Material    Thermal conductivity (W/m K)*
Diamond:    1000
Silver:     406.0
Copper:     385.0
Gold:       314
Brass:      109.0
Aluminum:   205.0
Iron:       79.5
Steel:      50.2
Lead:       34.7
Mercury:    8.3
Glass:      0.8
Water:      0.6
Air:        0.024
Source: http://hyperphysics.phy-astr.gsu.edu/hbase/Tables/thrcn.html
*/

    abstract val specficHeat: Double

	// amperage per material type per mm^2 (possibly - calculate from thermal conductivity, power and thermal conductivity
	// abstract val amperageMaterial: Double
}
