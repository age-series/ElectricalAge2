@file:Suppress("ObjectPropertyName")

package org.eln2.mc.sim

import org.ageseries.libage.data.multiMapOf
import org.eln2.mc.data.*
import org.eln2.mc.formattedPercentN
import org.eln2.mc.mathematics.approxEq
import org.eln2.mc.utility.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap
import kotlin.math.pow

// Dream on!

enum class ChemicalMetallicSpeciesType {
    Metal,
    NonMetal,
    Metalloid
}

enum class ChemicalPhaseType {
    Gas,
    Solid,
    Liquid,
    Artificial
}

enum class ChemicalElementType {
    Actinide,
    AlkalineEarthMetal,
    Lanthanide,
    Halogen,
    NobleGas,
    Metal,
    Metalloid,
    Transactinide,
    NonMetal,
    AlkaliMetal,
    TransitionMetal,
    Undefined
}
private fun dsDensity(v: Double) = Quantity(v, G_PER_CM3)
private fun dsExcitationEnergy(v: Double) = Quantity(v, eV)

@Suppress("PropertyName")
enum class ChemicalElement(
    val Z: Int,
    val label: String,
    val symbol: String,
    val A: Double,
    val neutrons: Int,
    val protons: Int,
    val electrons: Int,
    val period: Int,
    val group: Int,
    val phase: ChemicalPhaseType,
    val isRadioactive: Boolean,
    val isNatural: Boolean,
    val metallicProperty: ChemicalMetallicSpeciesType,
    val type: ChemicalElementType,
    val atomicRadius: Double,
    val electronegativity: Double,
    val firstIonization: Double,
    val density: Quantity<Density>,
    val meltingPoint: Double,
    val boilingPoint: Double,
    val isotopeCount: Int,
    val discoverer: String,
    val discoveryYear: Int,
    val specificHeat: Double,
    val electronShells: Int,
    val valence: Int,
    val excitationEnergy: Quantity<Energy>
) {
    Hydrogen(1, "Hydrogen", "H", 1.007, 0, 1, 1, 1, 1, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NonMetal, 0.79, 2.2, 13.5984, dsDensity(8.99E-5), 14.175, 20.28, 3, "Cavendish", 1766, 14.304, 1, 1, dsExcitationEnergy(19.2)),
    Helium(2, "Helium", "He", 4.002, 2, 2, 2, 1, 18, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NobleGas, 0.49, 0.0, 24.5874, dsDensity(1.79E-4), 0.0, 4.22, 5, "Janssen", 1868, 5.193, 1, 0, dsExcitationEnergy(41.8)),
    Lithium(3, "Lithium", "Li", 6.941, 4, 3, 3, 2, 1, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkaliMetal, 2.1, 0.98, 5.3917, dsDensity(0.534), 453.85, 1615.0, 5, "Arfvedson", 1817, 3.582, 2, 1, dsExcitationEnergy(40.0)),
    Beryllium(4, "Beryllium", "Be", 9.012, 5, 4, 4, 2, 2, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkalineEarthMetal, 1.4, 1.57, 9.3227, dsDensity(1.85), 1560.15, 2742.0, 6, "Vaulquelin", 1798, 1.825, 2, 2, dsExcitationEnergy(63.7)),
    Boron(5, "Boron", "B", 10.811, 6, 5, 5, 2, 13, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metalloid, ChemicalElementType.Metalloid, 1.2, 2.04, 8.298, dsDensity(2.34), 2573.15, 4200.0, 6, "Gay-Lussac", 1808, 1.026, 2, 3, dsExcitationEnergy(76.0)),
    Carbon(6, "Carbon", "C", 12.011, 6, 6, 6, 2, 14, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NonMetal, 0.91, 2.55, 11.2603, dsDensity(2.27), 3948.15, 4300.0, 7, "N/A", 0, 0.709, 2, 4, dsExcitationEnergy(78.0)),
    Nitrogen(7, "Nitrogen", "N", 14.007, 7, 7, 7, 2, 15, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NonMetal, 0.75, 3.04, 14.5341, dsDensity(0.00125), 63.29, 77.36, 8, "Rutherford", 1772, 1.04, 2, 5, dsExcitationEnergy(82.0)),
    Oxygen(8, "Oxygen", "O", 15.999, 8, 8, 8, 2, 16, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NonMetal, 0.65, 3.44, 13.6181, dsDensity(0.00143), 50.5, 90.2, 8, "Priestley/Scheele", 1774, 0.918, 2, 6, dsExcitationEnergy(95.0)),
    Fluorine(9, "Fluorine", "F", 18.998, 10, 9, 9, 2, 17, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.Halogen, 0.57, 3.98, 17.4228, dsDensity(0.0017), 53.63, 85.03, 6, "Moissan", 1886, 0.824, 2, 7, dsExcitationEnergy(115.0)),
    Neon(10, "Neon", "Ne", 20.18, 10, 10, 10, 2, 18, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NobleGas, 0.51, 0.0, 21.5645, dsDensity(9.0E-4), 24.703, 27.07, 8, "Ramsay and Travers", 1898, 1.03, 2, 8, dsExcitationEnergy(137.0)),
    Sodium(11, "Sodium", "Na", 22.99, 12, 11, 11, 3, 1, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkaliMetal, 2.2, 0.93, 5.1391, dsDensity(0.971), 371.15, 1156.0, 7, "Davy", 1807, 1.228, 3, 1, dsExcitationEnergy(149.0)),
    Magnesium(12, "Magnesium", "Mg", 24.305, 12, 12, 12, 3, 2, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkalineEarthMetal, 1.7, 1.31, 7.6462, dsDensity(1.74), 923.15, 1363.0, 8, "Black", 1755, 1.023, 3, 2, dsExcitationEnergy(156.0)),
    Aluminum(13, "Aluminum", "Al", 26.982, 14, 13, 13, 3, 13, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Metal, 1.8, 1.61, 5.9858, dsDensity(2.7), 933.4, 2792.0, 8, "Wshler", 1827, 0.897, 3, 3, dsExcitationEnergy(166.0)),
    Silicon(14, "Silicon", "Si", 28.086, 14, 14, 14, 3, 14, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metalloid, ChemicalElementType.Metalloid, 1.5, 1.9, 8.1517, dsDensity(2.33), 1683.15, 3538.0, 8, "Berzelius", 1824, 0.705, 3, 4, dsExcitationEnergy(173.0)),
    Phosphorus(15, "Phosphorus", "P", 30.974, 16, 15, 15, 3, 15, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NonMetal, 1.2, 2.19, 10.4867, dsDensity(1.82), 317.25, 553.0, 7, "BranBrand", 1669, 0.769, 3, 5, dsExcitationEnergy(173.0)),
    Sulfur(16, "Sulfur", "S", 32.065, 16, 16, 16, 3, 16, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NonMetal, 1.1, 2.58, 10.36, dsDensity(2.07), 388.51, 717.8, 10, "N/A", 0, 0.71, 3, 6, dsExcitationEnergy(180.0)),
    Chlorine(17, "Chlorine", "Cl", 35.453, 18, 17, 17, 3, 17, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.Halogen, 0.97, 3.16, 12.9676, dsDensity(0.00321), 172.31, 239.11, 11, "Scheele", 1774, 0.479, 3, 7, dsExcitationEnergy(174.0)),
    Argon(18, "Argon", "Ar", 39.948, 22, 18, 18, 3, 18, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NobleGas, 0.88, 0.0, 15.7596, dsDensity(0.00178), 83.96, 87.3, 8, "Rayleigh and Ramsay", 1894, 0.52, 3, 8, dsExcitationEnergy(188.0)),
    Potassium(19, "Potassium", "K", 39.098, 20, 19, 19, 4, 1, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkaliMetal, 2.8, 0.82, 4.3407, dsDensity(0.862), 336.5, 1032.0, 10, "Davy", 1807, 0.757, 4, 1, dsExcitationEnergy(190.0)),
    Calcium(20, "Calcium", "Ca", 40.078, 20, 20, 20, 4, 2, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkalineEarthMetal, 2.2, 1.0, 6.1132, dsDensity(1.54), 1112.15, 1757.0, 14, "Davy", 1808, 0.647, 4, 2, dsExcitationEnergy(191.0)),
    Scandium(21, "Scandium", "Sc", 44.956, 24, 21, 21, 4, 3, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.1, 1.36, 6.5615, dsDensity(2.99), 1812.15, 3109.0, 15, "Nilson", 1878, 0.568, 4, 0, dsExcitationEnergy(216.0)),
    Titanium(22, "Titanium", "Ti", 47.867, 26, 22, 22, 4, 4, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.0, 1.54, 6.8281, dsDensity(4.54), 1933.15, 3560.0, 9, "Gregor", 1791, 0.523, 4, 0, dsExcitationEnergy(233.0)),
    Vanadium(23, "Vanadium", "V", 50.942, 28, 23, 23, 4, 5, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.9, 1.63, 6.7462, dsDensity(6.11), 2175.15, 3680.0, 9, "del Rio", 1801, 0.489, 4, 0, dsExcitationEnergy(245.0)),
    Chromium(24, "Chromium", "Cr", 51.996, 28, 24, 24, 4, 6, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.9, 1.66, 6.7665, dsDensity(7.15), 2130.15, 2944.0, 9, "Vauquelin", 1797, 0.449, 4, 0, dsExcitationEnergy(257.0)),
    Manganese(25, "Manganese", "Mn", 54.938, 30, 25, 25, 4, 7, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.8, 1.55, 7.434, dsDensity(7.44), 1519.15, 2334.0, 11, "Gahn, Scheele", 1774, 0.479, 4, 0, dsExcitationEnergy(272.0)),
    Iron(26, "Iron", "Fe", 55.845, 30, 26, 26, 4, 8, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.7, 1.83, 7.9024, dsDensity(7.87), 1808.15, 3134.0, 10, "N/A", 0, 0.449, 4, 0, dsExcitationEnergy(286.0)),
    Cobalt(27, "Cobalt", "Co", 58.933, 32, 27, 27, 4, 9, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.7, 1.88, 7.881, dsDensity(8.86), 1768.15, 3200.0, 14, "Brandt", 1735, 0.421, 4, 0, dsExcitationEnergy(297.0)),
    Nickel(28, "Nickel", "Ni", 58.693, 31, 28, 28, 4, 10, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.6, 1.91, 7.6398, dsDensity(8.91), 1726.15, 3186.0, 11, "Cronstedt", 1751, 0.444, 4, 0, dsExcitationEnergy(311.0)),
    Copper(29, "Copper", "Cu", 63.546, 35, 29, 29, 4, 11, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.6, 1.9, 7.7264, dsDensity(8.96), 1357.75, 2835.0, 11, "N/A", 0, 0.385, 4, 0, dsExcitationEnergy(322.0)),
    Zinc(30, "Zinc", "Zn", 65.38, 35, 30, 30, 4, 12, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.5, 1.65, 9.3942, dsDensity(7.13), 692.88, 1180.0, 15, "N/A", 0, 0.388, 4, 0, dsExcitationEnergy(330.0)),
    Gallium(31, "Gallium", "Ga", 69.723, 39, 31, 31, 4, 13, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Metal, 1.8, 1.81, 5.9993, dsDensity(5.91), 302.91, 2477.0, 14, "de Boisbaudran", 1875, 0.371, 4, 3, dsExcitationEnergy(334.0)),
    Germanium(32, "Germanium", "Ge", 72.64, 41, 32, 32, 4, 14, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metalloid, ChemicalElementType.Metalloid, 1.5, 2.01, 7.8994, dsDensity(5.32), 1211.45, 3106.0, 17, "Winkler", 1886, 0.32, 4, 4, dsExcitationEnergy(350.0)),
    Arsenic(33, "Arsenic", "As", 74.922, 42, 33, 33, 4, 15, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metalloid, ChemicalElementType.Metalloid, 1.3, 2.18, 9.7886, dsDensity(5.78), 1090.15, 887.0, 14, "Albertus Magnus", 1250, 0.329, 4, 5, dsExcitationEnergy(347.0)),
    Selenium(34, "Selenium", "Se", 78.96, 45, 34, 34, 4, 16, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NonMetal, 1.2, 2.55, 9.7524, dsDensity(4.81), 494.15, 958.0, 20, "Berzelius", 1817, 0.321, 4, 6, dsExcitationEnergy(348.0)),
    Bromine(35, "Bromine", "Br", 79.904, 45, 35, 35, 4, 17, ChemicalPhaseType.Liquid, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.Halogen, 1.1, 2.96, 11.8138, dsDensity(3.12), 266.05, 332.0, 19, "Balard", 1826, 0.474, 4, 7, dsExcitationEnergy(343.0)),
    Krypton(36, "Krypton", "Kr", 83.798, 48, 36, 36, 4, 18, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NobleGas, 1.0, 0.0, 13.9996, dsDensity(0.00373), 115.93, 119.93, 23, "Ramsay and Travers", 1898, 0.248, 4, 8, dsExcitationEnergy(352.0)),
    Rubidium(37, "Rubidium", "Rb", 85.468, 48, 37, 37, 5, 1, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkaliMetal, 3.0, 0.82, 4.1771, dsDensity(1.53), 312.79, 961.0, 20, "Bunsen and Kirchoff", 1861, 0.363, 5, 1, dsExcitationEnergy(363.0)),
    Strontium(38, "Strontium", "Sr", 87.62, 50, 38, 38, 5, 2, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkalineEarthMetal, 2.5, 0.95, 5.6949, dsDensity(2.64), 1042.15, 1655.0, 18, "Davy", 1808, 0.301, 5, 2, dsExcitationEnergy(366.0)),
    Yttrium(39, "Yttrium", "Y", 88.906, 50, 39, 39, 5, 3, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.3, 1.22, 6.2173, dsDensity(4.47), 1799.15, 3609.0, 21, "Gadolin", 1794, 0.298, 5, 0, dsExcitationEnergy(379.0)),
    Zirconium(40, "Zirconium", "Zr", 91.224, 51, 40, 40, 5, 4, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.2, 1.33, 6.6339, dsDensity(6.51), 2125.15, 4682.0, 20, "Klaproth", 1789, 0.278, 5, 0, dsExcitationEnergy(393.0)),
    Niobium(41, "Niobium", "Nb", 92.906, 52, 41, 41, 5, 5, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.1, 1.6, 6.7589, dsDensity(8.57), 2741.15, 5017.0, 24, "Hatchett", 1801, 0.265, 5, 0, dsExcitationEnergy(417.0)),
    Molybdenum(42, "Molybdenum", "Mo", 95.96, 54, 42, 42, 5, 6, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.0, 2.16, 7.0924, dsDensity(10.2), 2890.15, 4912.0, 20, "Scheele", 1778, 0.251, 5, 0, dsExcitationEnergy(424.0)),
    Technetium(43, "Technetium", "Tc", 98.0, 55, 43, 43, 5, 7, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.0, 1.9, 7.28, dsDensity(11.5), 2473.15, 5150.0, 23, "Perrier and Segrè", 1937, 0.0, 5, 0, dsExcitationEnergy(428.0)),
    Ruthenium(44, "Ruthenium", "Ru", 101.07, 57, 44, 44, 5, 8, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.9, 2.2, 7.3605, dsDensity(12.4), 2523.15, 4423.0, 16, "Klaus", 1844, 0.238, 5, 0, dsExcitationEnergy(441.0)),
    Rhodium(45, "Rhodium", "Rh", 102.906, 58, 45, 45, 5, 9, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.8, 2.28, 7.4589, dsDensity(12.4), 2239.15, 3968.0, 20, "Wollaston", 1803, 0.243, 5, 0, dsExcitationEnergy(449.0)),
    Palladium(46, "Palladium", "Pd", 106.42, 60, 46, 46, 5, 10, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.8, 2.2, 8.3369, dsDensity(12.0), 1825.15, 3236.0, 21, "Wollaston", 1803, 0.244, 5, 0, dsExcitationEnergy(470.0)),
    Silver(47, "Silver", "Ag", 107.868, 61, 47, 47, 5, 11, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.8, 1.93, 7.5762, dsDensity(10.5), 1234.15, 2435.0, 27, "N/A", 0, 0.235, 5, 0, dsExcitationEnergy(470.0)),
    Cadmium(48, "Cadmium", "Cd", 112.411, 64, 48, 48, 5, 12, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.7, 1.69, 8.9938, dsDensity(8.69), 594.33, 1040.0, 22, "Stromeyer", 1817, 0.232, 5, 0, dsExcitationEnergy(469.0)),
    Indium(49, "Indium", "In", 114.818, 66, 49, 49, 5, 13, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Metal, 2.0, 1.78, 5.7864, dsDensity(7.31), 429.91, 2345.0, 34, "Reich and Richter", 1863, 0.233, 5, 3, dsExcitationEnergy(488.0)),
    Tin(50, "Tin", "Sn", 118.71, 69, 50, 50, 5, 14, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Metal, 1.7, 1.96, 7.3439, dsDensity(7.29), 505.21, 2875.0, 28, "N/A", 0, 0.228, 5, 4, dsExcitationEnergy(488.0)),
    Antimony(51, "Antimony", "Sb", 121.76, 71, 51, 51, 5, 15, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metalloid, ChemicalElementType.Metalloid, 1.5, 2.05, 8.6084, dsDensity(6.69), 904.05, 1860.0, 29, "N/A", 0, 0.207, 5, 5, dsExcitationEnergy(487.0)),
    Tellurium(52, "Tellurium", "Te", 127.6, 76, 52, 52, 5, 16, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metalloid, ChemicalElementType.Metalloid, 1.4, 2.1, 9.0096, dsDensity(6.23), 722.8, 1261.0, 29, "von Reichenstein", 1782, 0.202, 5, 6, dsExcitationEnergy(485.0)),
    Iodine(53, "Iodine", "I", 126.904, 74, 53, 53, 5, 17, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.Halogen, 1.3, 2.66, 10.4513, dsDensity(4.93), 386.65, 457.4, 24, "Courtois", 1811, 0.214, 5, 7, dsExcitationEnergy(491.0)),
    Xenon(54, "Xenon", "Xe", 131.293, 77, 54, 54, 5, 18, ChemicalPhaseType.Gas, false, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NobleGas, 1.2, 0.0, 12.1298, dsDensity(0.00589), 161.45, 165.03, 31, "Ramsay and Travers", 1898, 0.158, 5, 8, dsExcitationEnergy(482.0)),
    Cesium(55, "Cesium", "Cs", 132.905, 78, 55, 55, 6, 1, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkaliMetal, 3.3, 0.79, 3.8939, dsDensity(1.87), 301.7, 944.0, 22, "Bunsen and Kirchoff", 1860, 0.242, 6, 1, dsExcitationEnergy(488.0)),
    Barium(56, "Barium", "Ba", 137.327, 81, 56, 56, 6, 2, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkalineEarthMetal, 2.8, 0.89, 5.2117, dsDensity(3.59), 1002.15, 2170.0, 25, "Davy", 1808, 0.204, 6, 2, dsExcitationEnergy(491.0)),
    Lanthanum(57, "Lanthanum", "La", 138.905, 82, 57, 57, 6, 3, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.7, 1.1, 5.5769, dsDensity(6.15), 1193.15, 3737.0, 19, "Mosander", 1839, 0.195, 6, 0, dsExcitationEnergy(501.0)),
    Cerium(58, "Cerium", "Ce", 140.116, 82, 58, 58, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.7, 1.12, 5.5387, dsDensity(6.77), 1071.15, 3716.0, 19, "Berzelius", 1803, 0.192, 6, 0, dsExcitationEnergy(523.0)),
    Praseodymium(59, "Praseodymium", "Pr", 140.908, 82, 59, 59, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.7, 1.13, 5.473, dsDensity(6.77), 1204.15, 3793.0, 15, "von Welsbach", 1885, 0.193, 6, 0, dsExcitationEnergy(535.0)),
    Neodymium(60, "Neodymium", "Nd", 144.242, 84, 60, 60, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.6, 1.14, 5.525, dsDensity(7.01), 1289.15, 3347.0, 16, "von Welsbach", 1885, 0.19, 6, 0, dsExcitationEnergy(546.0)),
    Promethium(61, "Promethium", "Pm", 145.0, 84, 61, 61, 6, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.6, 1.13, 5.582, dsDensity(7.26), 1204.15, 3273.0, 14, "Marinsky et al.", 1945, 0.0, 6, 0, dsExcitationEnergy(560.0)),
    Samarium(62, "Samarium", "Sm", 150.36, 88, 62, 62, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.6, 1.17, 5.6437, dsDensity(7.52), 1345.15, 2067.0, 17, "Boisbaudran", 1879, 0.197, 6, 0, dsExcitationEnergy(574.0)),
    Europium(63, "Europium", "Eu", 151.964, 89, 63, 63, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.6, 1.2, 5.6704, dsDensity(5.24), 1095.15, 1802.0, 21, "Demarcay", 1901, 0.182, 6, 0, dsExcitationEnergy(580.0)),
    Gadolinium(64, "Gadolinium", "Gd", 157.25, 93, 64, 64, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.5, 1.2, 6.1501, dsDensity(7.9), 1585.15, 3546.0, 17, "de Marignac", 1880, 0.236, 6, 0, dsExcitationEnergy(591.0)),
    Terbium(65, "Terbium", "Tb", 158.925, 94, 65, 65, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.5, 1.2, 5.8638, dsDensity(8.23), 1630.15, 3503.0, 24, "Mosander", 1843, 0.182, 6, 0, dsExcitationEnergy(614.0)),
    Dysprosium(66, "Dysprosium", "Dy", 162.5, 97, 66, 66, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.5, 1.22, 5.9389, dsDensity(8.55), 1680.15, 2840.0, 21, "de Boisbaudran", 1886, 0.17, 6, 0, dsExcitationEnergy(628.0)),
    Holmium(67, "Holmium", "Ho", 164.93, 98, 67, 67, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.5, 1.23, 6.0215, dsDensity(8.8), 1743.15, 2993.0, 29, "Delafontaine and Soret", 1878, 0.165, 6, 0, dsExcitationEnergy(650.0)),
    Erbium(68, "Erbium", "Er", 167.259, 99, 68, 68, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.5, 1.24, 6.1077, dsDensity(9.07), 1795.15, 3503.0, 16, "Mosander", 1843, 0.168, 6, 0, dsExcitationEnergy(658.0)),
    Thulium(69, "Thulium", "Tm", 168.934, 100, 69, 69, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.4, 1.25, 6.1843, dsDensity(9.32), 1818.15, 2223.0, 18, "Cleve", 1879, 0.16, 6, 0, dsExcitationEnergy(674.0)),
    Ytterbium(70, "Ytterbium", "Yb", 173.054, 103, 70, 70, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.4, 1.1, 6.2542, dsDensity(6.97), 1097.15, 1469.0, 16, "Marignac", 1878, 0.155, 6, 0, dsExcitationEnergy(684.0)),
    Lutetium(71, "Lutetium", "Lu", 174.967, 104, 71, 71, 6, 0, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Lanthanide, 2.3, 1.27, 5.4259, dsDensity(9.84), 1936.15, 3675.0, 22, "Urbain / von Welsbach", 1907, 0.154, 6, 0, dsExcitationEnergy(694.0)),
    Hafnium(72, "Hafnium", "Hf", 178.49, 106, 72, 72, 6, 4, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.2, 1.3, 6.8251, dsDensity(13.3), 2500.15, 4876.0, 17, "Coster and von Hevesy", 1923, 0.144, 6, 0, dsExcitationEnergy(705.0)),
    Tantalum(73, "Tantalum", "Ta", 180.948, 108, 73, 73, 6, 5, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.1, 1.5, 7.5496, dsDensity(16.7), 3269.15, 5731.0, 19, "Ekeberg", 1801, 0.14, 6, 0, dsExcitationEnergy(718.0)),
    Wolfram(74, "Wolfram", "W", 183.84, 110, 74, 74, 6, 6, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.0, 2.36, 7.864, dsDensity(19.3), 3680.15, 5828.0, 22, "J. and F. d'Elhuyar", 1783, 0.132, 6, 0, dsExcitationEnergy(727.0)),
    Rhenium(75, "Rhenium", "Re", 186.207, 111, 75, 75, 6, 7, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 2.0, 1.9, 7.8335, dsDensity(21.0), 3453.15, 5869.0, 21, "Noddack, Berg and Tacke", 1925, 0.137, 6, 0, dsExcitationEnergy(736.0)),
    Osmium(76, "Osmium", "Os", 190.23, 114, 76, 76, 6, 8, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.9, 2.2, 8.4382, dsDensity(22.6), 3300.15, 5285.0, 19, "Tennant", 1803, 0.13, 6, 0, dsExcitationEnergy(746.0)),
    Iridium(77, "Iridium", "Ir", 192.217, 115, 77, 77, 6, 9, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.9, 2.2, 8.967, dsDensity(22.6), 2716.15, 4701.0, 25, "Tennant", 1804, 0.131, 6, 0, dsExcitationEnergy(757.0)),
    Platinum(78, "Platinum", "Pt", 195.084, 117, 78, 78, 6, 10, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.8, 2.28, 8.9587, dsDensity(21.5), 2045.15, 4098.0, 32, "Ulloa/Wood", 1735, 0.133, 6, 0, dsExcitationEnergy(790.0)),
    Gold(79, "Gold", "Au", 196.967, 118, 79, 79, 6, 11, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.8, 2.54, 9.2255, dsDensity(19.3), 1337.73, 3129.0, 21, "N/A", 0, 0.129, 6, 0, dsExcitationEnergy(790.0)),
    Mercury(80, "Mercury", "Hg", 200.59, 121, 80, 80, 6, 12, ChemicalPhaseType.Liquid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.TransitionMetal, 1.8, 2.0, 10.4375, dsDensity(13.5), 234.43, 630.0, 26, "N/A", 0, 0.14, 6, 0, dsExcitationEnergy(800.0)),
    Thallium(81, "Thallium", "Tl", 204.383, 123, 81, 81, 6, 13, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Metal, 2.1, 2.04, 6.1082, dsDensity(11.9), 577.15, 1746.0, 28, "Crookes", 1861, 0.129, 6, 3, dsExcitationEnergy(810.0)),
    Lead(82, "Lead", "Pb", 207.2, 125, 82, 82, 6, 14, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Metal, 1.8, 2.33, 7.4167, dsDensity(11.3), 600.75, 2022.0, 29, "N/A", 0, 0.129, 6, 4, dsExcitationEnergy(823.0)),
    Bismuth(83, "Bismuth", "Bi", 208.98, 126, 83, 83, 6, 15, ChemicalPhaseType.Solid, false, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Metal, 1.6, 2.02, 7.2856, dsDensity(9.81), 544.67, 1837.0, 19, "Geoffroy the Younger", 1753, 0.122, 6, 5, dsExcitationEnergy(823.0)),
    Polonium(84, "Polonium", "Po", 210.0, 126, 84, 84, 6, 16, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.Metalloid, ChemicalElementType.Metalloid, 1.5, 2.0, 8.417, dsDensity(9.32), 527.15, 1235.0, 34, "Curie", 1898, 0.0, 6, 6, dsExcitationEnergy(830.0)),
    Astatine(85, "Astatine", "At", 210.0, 125, 85, 85, 6, 17, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NobleGas, 1.4, 2.2, 9.3, dsDensity(7.0), 575.15, 610.0, 21, "Corson et al.", 1940, 0.0, 6, 7, dsExcitationEnergy(825.0)),
    Radon(86, "Radon", "Rn", 222.0, 136, 86, 86, 6, 18, ChemicalPhaseType.Gas, true, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkaliMetal, 1.3, 0.0, 10.7485, dsDensity(0.00973), 202.15, 211.3, 20, "Dorn", 1900, 0.094, 6, 8, dsExcitationEnergy(794.0)),
    Francium(87, "Francium", "Fr", 223.0, 136, 87, 87, 7, 1, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.AlkalineEarthMetal, 0.0, 0.7, 4.0727, dsDensity(1.87), 300.15, 950.0, 21, "Perey", 1939, 0.0, 7, 1, dsExcitationEnergy(827.0)),
    Radium(88, "Radium", "Ra", 226.0, 138, 88, 88, 7, 2, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 0.9, 5.2784, dsDensity(5.5), 973.15, 2010.0, 15, "Pierre and Marie Curie", 1898, 0.0, 7, 2, dsExcitationEnergy(826.0)),
    Actinium(89, "Actinium", "Ac", 227.0, 138, 89, 89, 7, 3, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.1, 5.17, dsDensity(10.1), 1323.15, 3471.0, 11, "Debierne/Giesel", 1899, 0.12, 7, 0, dsExcitationEnergy(841.0)),
    Thorium(90, "Thorium", "Th", 232.038, 142, 90, 90, 7, 0, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 6.3067, dsDensity(11.7), 2028.15, 5061.0, 12, "Berzelius", 1828, 0.113, 7, 0, dsExcitationEnergy(847.0)),
    Protactinium(91, "Protactinium", "Pa", 231.036, 140, 91, 91, 7, 0, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.5, 5.89, dsDensity(15.4), 1873.15, 4300.0, 14, "Hahn and Meitner", 1917, 0.0, 7, 0, dsExcitationEnergy(878.0)),
    Uranium(92, "Uranium", "U", 238.029, 146, 92, 92, 7, 0, ChemicalPhaseType.Solid, true, true, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.38, 6.1941, dsDensity(19.0), 1405.15, 4404.0, 15, "Peligot", 1841, 0.116, 7, 0, dsExcitationEnergy(890.0)),
    Neptunium(93, "Neptunium", "Np", 237.0, 144, 93, 93, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.36, 6.2657, dsDensity(20.5), 913.15, 4273.0, 153, "McMillan and Abelson", 1940, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Plutonium(94, "Plutonium", "Pu", 244.0, 150, 94, 94, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.28, 6.0262, dsDensity(19.8), 913.15, 3501.0, 163, "Seaborg et al.", 1940, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Americium(95, "Americium", "Am", 243.0, 148, 95, 95, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 5.9738, dsDensity(13.7), 1267.15, 2880.0, 133, "Seaborg et al.", 1944, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Curium(96, "Curium", "Cm", 247.0, 151, 96, 96, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 5.9915, dsDensity(13.5), 1340.15, 3383.0, 133, "Seaborg et al.", 1944, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Berkelium(97, "Berkelium", "Bk", 247.0, 150, 97, 97, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 6.1979, dsDensity(14.8), 1259.15, 983.0, 83, "Seaborg et al.", 1949, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Californium(98, "Californium", "Cf", 251.0, 153, 98, 98, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 6.2817, dsDensity(15.1), 1925.15, 1173.0, 123, "Seaborg et al.", 1950, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Einsteinium(99, "Einsteinium", "Es", 252.0, 153, 99, 99, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 6.42, dsDensity(13.5), 1133.15, 0.0, 123, "Ghiorso et al.", 1952, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Fermium(100, "Fermium", "Fm", 257.0, 157, 100, 100, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 6.5, dsDensity(0.0), 0.0, 0.0, 103, "Ghiorso et al.", 1953, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Mendelevium(101, "Mendelevium", "Md", 258.0, 157, 101, 101, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 6.58, dsDensity(0.0), 0.0, 0.0, 33, "Ghiorso et al.", 1955, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Nobelium(102, "Nobelium", "No", 259.0, 157, 102, 102, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 1.3, 6.65, dsDensity(0.0), 0.0, 0.0, 73, "Ghiorso et al.", 1958, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Lawrencium(103, "Lawrencium", "Lr", 262.0, 159, 103, 103, 7, 0, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Actinide, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 203, "Ghiorso et al.", 1961, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Rutherfordium(104, "Rutherfordium", "Rf", 261.0, 157, 104, 104, 7, 4, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(18.1), 0.0, 0.0, 0, "Ghiorso et al.", 1969, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Dubnium(105, "Dubnium", "Db", 262.0, 157, 105, 105, 7, 5, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(39.0), 0.0, 0.0, 0, "Ghiorso et al.", 1970, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Seaborgium(106, "Seaborgium", "Sg", 266.0, 160, 106, 106, 7, 6, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(35.0), 0.0, 0.0, 0, "Ghiorso et al.", 1974, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Bohrium(107, "Bohrium", "Bh", 264.0, 157, 107, 107, 7, 7, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(37.0), 0.0, 0.0, 0, "Armbruster and Münzenberg ", 1981, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Hassium(108, "Hassium", "Hs", 267.0, 159, 108, 108, 7, 8, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(41.0), 0.0, 0.0, 0, "Armbruster and Münzenberg ", 1983, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Meitnerium(109, "Meitnerium", "Mt", 268.0, 159, 109, 109, 7, 9, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(35.0), 0.0, 0.0, 0, "GSI, Darmstadt, West Germany", 1982, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Darmstadtium (110, "Darmstadtium ", "Ds ", 271.0, 161, 110, 110, 7, 10, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 1994, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Roentgenium (111, "Roentgenium ", "Rg ", 272.0, 161, 111, 111, 7, 11, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 1994, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Copernicium (112, "Copernicium ", "Cn ", 285.0, 173, 112, 112, 7, 12, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 1996, 0.0, 7, 0, dsExcitationEnergy(0.0)),
    Nihonium(113, "Nihonium", "Nh", 284.0, 171, 113, 113, 7, 13, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Undefined, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 2004, 0.0, 7, 3, dsExcitationEnergy(0.0)),
    Flerovium(114, "Flerovium", "Fl", 289.0, 175, 114, 114, 7, 14, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 1999, 0.0, 7, 4, dsExcitationEnergy(0.0)),
    Moscovium(115, "Moscovium", "Mc", 288.0, 173, 115, 115, 7, 15, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Undefined, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 2010, 0.0, 7, 5, dsExcitationEnergy(0.0)),
    Livermorium(116, "Livermorium", "Lv", 292.0, 176, 116, 116, 7, 16, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.Metal, ChemicalElementType.Transactinide, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 2000, 0.0, 7, 6, dsExcitationEnergy(0.0)),
    Tennessine(117, "Tennessine", "Ts", 295.0, 178, 117, 117, 7, 17, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.Undefined, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 2010, 0.0, 7, 7, dsExcitationEnergy(0.0)),
    Oganesson(118, "Oganesson", "Og", 294.0, 176, 118, 118, 7, 18, ChemicalPhaseType.Artificial, true, false, ChemicalMetallicSpeciesType.NonMetal, ChemicalElementType.NobleGas, 0.0, 0.0, 0.0, dsDensity(0.0), 0.0, 0.0, 0, "", 2006, 0.0, 7, 8, dsExcitationEnergy(0.0));

    operator fun not() = MolecularComposition(mapOf(this to 1))

    override fun toString() = symbol

    companion object {
        val byAtomicNumber = values().associateBy { it.Z }
        val bySymbol = values().associateBy { it.symbol }
        val byLabel = values().associateBy { it.label }
        val byLabelLower = values().associateBy { it.label.lowercase() }
        val byNeutronCount = values().associateByMulti { it.neutrons }
        val byProtonCount = values().associateBy { it.protons }
        val byElectronCount = values().associateBy { it.electrons }
        val byPeriod = values().associateByMulti { it.period }
        val byGroup = values().associateByMulti { it.group }
        val byPhase = values().associateByMulti { it.phase }
        val byIsRadioactive = values().associateByMulti { it.isRadioactive }
        val byIsNatural = values().associateByMulti { it.isNatural }
        val byMetallicProperty = values().associateByMulti { it.metallicProperty }
        val byType = values().associateByMulti { it.type }
        val byIsotopeCount = values().associateByMulti { it.isotopeCount }
        val byElectronShells = values().associateByMulti { it.electronShells }
        val byValence = values().associateByMulti { it.valence }
    }
}

data class IsotopeInformation(
    val neutrons: Int
)

val isotopes = multiMapOf<ChemicalElement, IsotopeInformation>()

private val mapElementSymbolElement = ChemicalElement.values().associateBy { it.symbol }
fun getChemicalElement(symbol: String) = mapElementSymbolElement[symbol]

private enum class FormulaSymbolType { Group, Parenthesis }
private class FormulaSymbol(val type: FormulaSymbolType) {
    val composition = LinkedHashMap<ChemicalElement, Int>()

    fun insert(e: ChemicalElement, count: Int) =
        if(composition.containsKey(e)) composition[e] = composition[e]!! + count
        else composition[e] = count

    fun insert(b: FormulaSymbol) = b.composition.forEach { (k, v) -> this.insert(k, v) }
    fun fold(f: Int) = composition.forEach { (k, v) -> composition[k] = v * f }
}

private enum class TokenType {
    LeftParenthesis,
    RightParenthesis,
    Element,
    Multiplier,
    Eof
}

private data class Token(val type: TokenType, val data: Any?)

private data class Lexer(val formula: String) {
    var index = 0
        private set

    val eof get() = index >= formula.length

    fun fetch(): Token {
        if (eof) return Token(TokenType.Eof, null)

        val char = formula[index++]

        if (char.isLetter) return Token(TokenType.Element, parseElement(char))
        else if (char.isDigitOrSubscriptDigit) return Token(TokenType.Multiplier, parseNumber(char))
        else if (char == '(' || char == '[') return Token(TokenType.LeftParenthesis, null)
        else if (char == ')' || char == ']') return Token(TokenType.RightParenthesis, null)
        error("Unexpected $char at $index")
    }

    private fun parseNumber(current: Char): Int {
        val builder = StringBuilder(charDigitValue(current).toString())

        while (!eof) {
            val char = formula[index]

            if (char.isDigitOrSubscriptDigit) {
                builder.append(charDigitValue(char))
                index++
            }
            else {
                break
            }
        }

        return builder.toString().toIntOrNull() ?: error("Failed to parse number $builder")
    }

    private fun parseElement(current: Char): ChemicalElement {
        val prefix = current.toString()
        if (index >= formula.length) return getChemicalElement(prefix) ?: error("Unexpected element $prefix")
        val bi = prefix + formula[index]
        val biElement = getChemicalElement(bi)

        return if (biElement != null) {
            index++
            biElement
        }
        else getChemicalElement(prefix) ?: error("Unexpected element $bi")
    }
}

data class MolecularComposition(val components: Map<ChemicalElement, Int>) {
    val molecularWeight = components.keys.sumOf { it.A * components[it]!! }

    val effectiveZRough = let {
        val electrons = components.keys.sumOf { it.Z * components[it]!! }.toDouble()
        var dragon = 0.0
        components.forEach { (atom, count) -> dragon += atom.Z.toDouble().pow(2.94) * ((atom.Z * count).toDouble() / electrons) }
        dragon.pow(1.0 / 2.94)
    }

    fun thummel(e: Quantity<Energy>) =
        Quantity(
            15.2 * ((effectiveZRough.pow(3.0 / 4.0)) / molecularWeight) * (1.0 / (e .. MeV).pow(1.485)),
            CM2_PER_G
        )

    fun constituentWeight(element: ChemicalElement): Double {
        val count = components[element] ?: error("Molecular composition does not have $element\n$this")

        return (count.toDouble() * element.A)
    }

    fun constituentWeightFraction(element: ChemicalElement) = constituentWeight(element) / molecularWeight

    operator fun plus(b: MolecularComposition) = MolecularComposition(
        LinkedHashMap<ChemicalElement, Int>().also { map ->
            this.components.forEach { (e, n) ->
                if(!map.containsKey(e)) map[e] = n
                else map[e] = map[e]!! + n
            }

            b.components.forEach { (e, n) ->
                if(!map.containsKey(e)) map[e] = n
                else map[e] = map[e]!! + n
            }
        }
    )

    operator fun not() = MassMixture(mapOf(this to 1.0))
    operator fun rangeTo(k: Double) = !this..k

    override fun toString(): String {
        val sb = StringBuilder()

        components.forEach { (element, count) ->
            sb.append(element.symbol)

            if(count != 1) {
                sb.append(count.toStringSubscript())
            }
        }

        return sb.toString()
    }
}

infix fun MolecularComposition.x(n: Int) = MolecularComposition(components.mapValues { (_, count) -> count * n })

enum class ConstituentAnalysisType {
    MolecularMass,
    MolecularMassContribution
}

data class MassMixture(val massComposition: Map<MolecularComposition, Double>) {
    init {
        require(massComposition.isNotEmpty()) {
            "Empty weight composition"
        }

        val c = massComposition.values.sum()
        require(c.approxEq(1.0)) {
            "Weight composition $c doesn't add up:\n$this"
        }
    }

    fun atomicConstituentAnalysis(type: ConstituentAnalysisType): LinkedHashMap<ChemicalElement, Double> {
        val composition = HashMap<ChemicalElement, Double>()

        var mass = 0.0

        massComposition.keys.forEach { molecular ->
            molecular.components.keys.forEach { element ->
                val k = constituentWeight(molecular, element)
                if(!composition.containsKey(element)) composition[element] = k
                else composition[element] = composition[element]!! + k

                mass += k
            }
        }

        val results = LinkedHashMap<ChemicalElement, Double>()

        composition.keys.sortedBy { composition[it]!! }.forEach {
            results[it] = when(type) {
                ConstituentAnalysisType.MolecularMass -> composition[it]!!
                ConstituentAnalysisType.MolecularMassContribution -> composition[it]!! / mass
            }

        }

        return results
    }

    fun molecularConstituentAnalysis(type: ConstituentAnalysisType): LinkedHashMap<MolecularComposition, Double> {
        val results = LinkedHashMap<MolecularComposition, Double>()

        massComposition.keys.sortedBy { massComposition[it]!! }.forEach { x ->
            results[x] = when(type) {
                ConstituentAnalysisType.MolecularMass -> massComposition[x]!! * x.molecularWeight
                ConstituentAnalysisType.MolecularMassContribution -> massComposition[x]!!
            }
        }

        return results
    }

    operator fun rangeTo(k: Double) = this to k

    override fun toString(): String {
        val sb = StringBuilder()
        massComposition.keys.sortedByDescending { massComposition[it]!! }.forEach { element ->
            sb.append((massComposition[element]!!).formattedPercentN()).append(" ")
            sb.appendLine(element)
        }

        return sb.toString()
    }

    fun constituentWeight(composition: MolecularComposition, element: ChemicalElement): Double {
        val compositionWeight = massComposition[composition] ?: error("Mixture does not have $composition\n$this")
        return compositionWeight * composition.constituentWeight(element)
    }

    val hash = hashCode()

    companion object {
        fun normalize(massComposition: Map<MolecularComposition, Double>): LinkedHashMap<MolecularComposition, Double> {
            val result = LinkedHashMap<MolecularComposition, Double>()
            val n = massComposition.values.sum()

            massComposition.forEach { (e, w) ->
                result[e] = w / n
            }

            return result
        }
    }
}

operator fun Double.rangeTo(m: MassMixture) = m..this
operator fun Double.rangeTo(m: MolecularComposition) = !m..this

fun createSolution(components: List<Pair<MassMixture, Double>>, normalize: Boolean = true): MassMixture {
    var map = LinkedHashMap<MolecularComposition, Double>()

    components.forEach { (xq, xqWeight) ->
        xq.massComposition.forEach { (molecular, molecularWeight) ->
            if(!map.containsKey(molecular)) map[molecular] = molecularWeight * xqWeight
            else map[molecular] = map[molecular]!! + molecularWeight * xqWeight
        }
    }

    if(normalize) {
       map = MassMixture.normalize(map)
    }

    return MassMixture(map)
}

fun solutionOf(vararg components: Pair<MassMixture, Double>, normalize: Boolean = true) = createSolution(components.asList(), normalize)

fun percentageSolutionOf(vararg components: Pair<MassMixture, Double>, normalize: Boolean = true) = createSolution(
    components.asList().map { (a, b) -> a to (b / 100.0) }, normalize
)

fun molecular(formula: String): MolecularComposition {
    val lexer = Lexer(formula)
    val stack = ArrayList<FormulaSymbol>()

    while (true) {
        val token = lexer.fetch()

        when(token.type) {
            TokenType.LeftParenthesis -> {
                stack.add(FormulaSymbol(FormulaSymbolType.Parenthesis))
            }
            TokenType.RightParenthesis -> {
                val item = FormulaSymbol(FormulaSymbolType.Group)

                while(stack.last().type == FormulaSymbolType.Group) {
                    item.insert(stack.removeLast())
                }

                require(stack.removeLastOrNull()?.type == FormulaSymbolType.Parenthesis) {
                    error("Unexpected parenthesis at ${lexer.index}")
                }

                require(item.composition.isNotEmpty()) {
                    "Expected elements at ${lexer.index}"
                }

                stack.add(item)
            }
            TokenType.Element -> {
                stack.add(FormulaSymbol(FormulaSymbolType.Group).apply {
                    insert(token.data as ChemicalElement, 1)
                })
            }
            TokenType.Multiplier -> {
                val last = stack.removeLast()
                require(last.type == FormulaSymbolType.Group) { "Expected element group before ${lexer.index}" }

                last.fold((token.data as Int).also {
                    require(it > 0) { "Cannot fold group by 0 elements" }
                })

                stack.add(last)
            }
            TokenType.Eof -> break
        }
    }

    val evaluation = FormulaSymbol(FormulaSymbolType.Group)

    while (stack.isNotEmpty()) {
        val last = stack.removeLast()
        require(last.type == FormulaSymbolType.Group) {
            "Unexpected ${last.type}"
        }
        evaluation.insert(last)
    }

    return MolecularComposition(evaluation.composition.let {
        val ordered = LinkedHashMap<ChemicalElement, Int>()

        it.keys.reversed().forEach { k ->
            ordered.put(k, it[k]!!)
        }

        ordered
    })
}

val H2O = molecular("H₂O")
val water = H2O
val dihydrogenMonoxide = H2O
val CO = molecular("CO")
val carbonMonoxide = CO
val CO2 = molecular("CO₂")
val carbonDioxide = CO2
val N2O = molecular("N₂O")
val dinitrogenMonoxide = N2O
val nitrousOxide = N2O
val NO = molecular("NO")
val nitrogenMonoxide = NO
val nitricOxide = NO
val NO2 = molecular("NO₂")
val nitrogenDioxide = NO2
val N2O5 = molecular("N₂O₅")
val dinitrogenPentaoxide = N2O5
val P2O5 = molecular("P₂O₅")
val phosphorusPentaoxide = P2O5
val SO2 = molecular("SO₂")
val sulfurDioxide = SO2
val sulfurousOxide = SO2
val SO3 = molecular("SO₃")
val sulfurTrioxide = SO3
val sulfuricOxide = SO3
val CaO = molecular("CaO")
val calciumOxide = CaO
val lime = CaO
val MgO = molecular("MgO")
val magnesiumOxide = MgO
val K2O = molecular("K₂O")
val potassiumOxide = K2O
val Na2O = molecular("Na₂O")
val sodiumOxide = Na2O
val Al2O3 = molecular("Al₂O₃")
val aluminiumOxide = Al2O3
val alumina = Al2O3
val SiO2 = molecular("SiO₂")
val siliconDioxide = SiO2
val silica = SiO2
val ZnO = molecular("ZnO")
val zincOxide = ZnO
val Cu2O = molecular("Cu₂O")
val `Copper (I) Oxide` = Cu2O
val cuprousOxide = Cu2O
val CuO = molecular("CuO")
val `Copper (II) Oxide` = CuO
val cupricOxide = CuO
val FeO = molecular("FeO")
val `Iron (II) Oxide` = FeO
val ferrousOxide = FeO
val Fe2O3 = molecular("Fe₂O₃")
val `Iron (III) Oxide` = Fe2O3
val ferricOxide = Fe2O3
val CrO3 = molecular("CrO₃")
val chromiumTrioxide = CrO3
val `Chromium (VI) Oxide` = CrO3
val MnO2 = molecular("MnO₂")
val manganeseDioxide = MnO2
val `Manganese (IV) Oxide` = MnO2
val Mn2O7 = molecular("Mn₂O₇")
val dimanganeseHeptoxide = Mn2O7
val manganeseHeptoxide = Mn2O7
val `Manganese (VII) Oxide` = Mn2O7
val TiO2 = molecular("TiO₂")
val titaniumDioxide = TiO2
val H2O2 = molecular("H₂O₂")
val hydrogenPeroxide = H2O2

val NaOH = molecular("NaOH")
val sodiumHydroxide = NaOH
val KOH = molecular("KOH")
val potassiumHydroxide = KOH
val `Ca(OH)2` = molecular("Ca(OH)₂")
val calciumHydroxide = `Ca(OH)2`
val `Ba(OH)2` = molecular("Ba(OH)₂")
val bariumHydroxide = `Ba(OH)2`
val `Al(OH)3` = molecular("Al(OH)₃")
val aluminiumHydroxide = `Al(OH)3`
val `Fe(OH)2` = molecular("Fe(OH)₂")
val `iron (II) Hydroxide` = `Fe(OH)2`
val ferrousHydroxide = `Fe(OH)2`
val `Fe(OH)3` = molecular("Fe(OH)₃")
val `iron (III) Hydroxide` = `Fe(OH)3`
val ferricHydroxide = `Fe(OH)3`
val `Cu(OH)2` = molecular("Cu(OH)₂")
val copperHydroxide = `Cu(OH)2`
val NH4OH = molecular("NH₄OH")
val ammoniumHydroxide = NH4OH
val aqueousAmmonia = NH4OH

val HF = molecular("HF")
val hydrofluoricAcid = HF
val hydrogenFluoride = HF
val HCl = molecular("HCl")
val hydrochloricAcid = HCl
val hydrogenChloride = HCl
val HBr = molecular("HBr")
val hydrobromicAcid = HBr
val hydrogenBromide = HBr
val HI = molecular("HI")
val hydroiodicAcid = HI
val hydrogenIodide = HI
val HCN = molecular("HCN")
val hydrocyanicAcid = HCN
val hydrogenCyanide = HCN
val H2S = molecular("H₂S")
val hydrosulfuricAcid = H2S
val hydrogenSulfide = H2S
val H3BO3 = molecular("H₃BO₃")
val boricAcid = H3BO3
val H2CO3 = molecular("H₂CO₃")
val carbonicAcid = H2CO3
val HOCN = molecular("HOCN")
val cyanicAcid = HOCN
val HSCN = molecular("HSCN")
val thiocyanicAcid = HSCN
val HNO2 = molecular("HNO₂")
val nitrousAcid = HNO2
val HNO3 = molecular("HNO₃")
val nitricAcid = HNO3
val H3PO4 = molecular("H₃PO₄")
val phosphoricAcid = H3PO4
val H2SO3 = molecular("H₂SO₃")
val sulfurousAcid = H2SO3
val H2SO4 = molecular("H₂SO₄")
val sulfuricAcid = H2SO4
val H2S2O3 = molecular("H₂S₂O₃")
val thiosulfuricAcid = H2S2O3
val HClO = molecular("HClO")
val hypochlorousAcid = HClO
val HClO2 = molecular("HClO₂")
val chlorousAcid = HClO2
val HClO3 = molecular("HClO₃")
val chloricAcid = HClO3
val HClO4 = molecular("HClO₄")
val perchloricAcid = HClO4
val H2CrO4 = molecular("H₂CrO₄")
val chromicAcid = H2CrO4
val H2Cr2O7 = molecular("H₂Cr₂O₇")
val dichromicAcid = H2Cr2O7
val HMnO4 = molecular("HMnO₄")
val permanganicAcid = HMnO4

val `Al2Si2O2(OH)2` = molecular("Al₂Si₂O₅(OH)₄")
val kaolinite = `Al2Si2O2(OH)2`

val C6H10O5 = molecular("C₆H₁₀O₅")
val celluloseUnit = C6H10O5

val C18H13N3Na2O8S2 = molecular("C₁₈H₁₃N₃Na₂O₈S₂")
val ligninUnit = C18H13N3Na2O8S2

val C24H42O21 = molecular("C₂₄H₄₂O₂₁")
val glucomannanUnit = C24H42O21
val glucomannoglycanUnit = C24H42O21

val C21H33O19 = molecular("C₂₁H₃₃O₁₉")
val glucuronoxylanDGlucuronate = C21H33O19

// ₁ ₂ ₃ ₄ ₅ ₆ ₇ ₈ ₉ ₁₀

data class KnownPhysicalProperties(
    val density: Quantity<Density>
)

private val knownProperties = mapOf(
    H2O to KnownPhysicalProperties(Quantity(1.0, G_PER_CM3)),
    SiO2 to KnownPhysicalProperties(Quantity(2.65, G_PER_CM3))
)

val MolecularComposition.properties get() = knownProperties[this]
    ?: error("Molecular composition $this does not have known properties")

val airMix = percentageSolutionOf(
    75.52 .. !!ChemicalElement.Nitrogen,
    23.14 .. !!ChemicalElement.Oxygen,
    1.29  .. !!ChemicalElement.Argon,
    0.051 .. CO2,
)

val clayMix = percentageSolutionOf(
    41.9 .. SiO2,
    22.3 .. Al2O3,
    11.1 .. CaO,
    8.0  .. Fe2O3,
    4.1  .. K2O,
    3.4  .. MgO,
    2.8  .. SO3,
    0.9  .. TiO2
)

val stoneMix = percentageSolutionOf(
    72.04 .. SiO2,
    14.42 .. Al2O3,
    4.12  .. K2O,
    3.69  .. Na2O,
    1.82  .. CaO,
    1.68  .. FeO,
    1.22  .. Fe2O3,
    0.71  .. MgO,
    0.30  .. TiO2,
    0.12  .. P2O5,
)

val ktSoilMix = percentageSolutionOf(
    46.35 .. SiO2,
    20.85 .. Al2O3,
    2.19  .. TiO2,
    2.06  .. Fe2O3,
    1.79  .. CaO,
    1.79  .. K2O,
    0.22  .. MgO,
    1.97 .. percentageSolutionOf(
        0.5 .. Na2O,
        0.5 .. K2O
    )
)

// other polysaccharides and structure not covered (not worth it)

val pinusSylvestrisMix = percentageSolutionOf(
    40.0 .. celluloseUnit,
    28.0 .. ligninUnit,
    16.0 .. glucomannoglycanUnit,
    9.0  .. glucuronoxylanDGlucuronate,
)

val piceaGlaucaMix = percentageSolutionOf(
    39.5 .. celluloseUnit,
    27.5 .. ligninUnit,
    17.2 .. glucomannoglycanUnit,
    10.4 .. glucuronoxylanDGlucuronate,
)

val betulaVerrucosaMix = percentageSolutionOf(
    41.1 .. celluloseUnit,
    22.0 .. ligninUnit,
    2.3  .. glucomannoglycanUnit,
    27.5 .. glucuronoxylanDGlucuronate,
)

val quercusFagaceaeMix = percentageSolutionOf(
    46.1 .. celluloseUnit,
    22.5 .. ligninUnit,
    14.2 .. glucomannoglycanUnit,
    12.4 .. glucuronoxylanDGlucuronate,
)

val sodaLimeGlassMix = percentageSolutionOf(
    73.1 .. SiO2,
    15.0 .. Na2O,
    7.0  .. CaO,
    4.1  .. MgO,
    1.0  .. Al2O3
)

val obsidianSpecimenMix = percentageSolutionOf(
    75.48 .. SiO2,
    11.75 .. Al2O3,
    3.47 .. Na2O,
    0.1 .. MgO,
    0.05 .. P2O5,
    5.41 .. K2O,
    0.9 .. CaO,
    0.1 .. TiO2,
    2.87 .. Fe2O3
)
