package org.eln2.mc.scientific.chemistry.data

import org.eln2.mc.scientific.chemistry.*

val airMix = percentageSolutionOf(
    75.52..!!ChemicalElement.Nitrogen,
    23.14..!!ChemicalElement.Oxygen,
    1.290..!!ChemicalElement.Argon,
    0.051..CO2,
)
val clayMix = percentageSolutionOf(
    41.9..SiO2,
    22.3..Al2O3,
    11.1..CaO,
    8.00..Fe2O3,
    4.10..K2O,
    3.40..MgO,
    2.80..SO3,
    0.90..TiO2
)
val stoneMix = percentageSolutionOf(
    72.04..SiO2,
    14.42..Al2O3,
    4.120..K2O,
    3.690..Na2O,
    1.820..CaO,
    1.680..FeO,
    1.220..Fe2O3,
    0.710..MgO,
    0.300..TiO2,
    0.120..P2O5,
)
val ktSoilMix = percentageSolutionOf(
    46.35..SiO2,
    20.85..Al2O3,
    2.190..TiO2,
    2.060..Fe2O3,
    1.790..CaO,
    1.790..K2O,
    0.220..MgO,
    1.97..percentageSolutionOf(
        0.5..Na2O,
        0.5..K2O
    )
)
val pinusSylvestrisMix = percentageSolutionOf(
    40.0..celluloseUnit,
    28.0..ligninUnit,
    16.0..C24H42O21,
    9.00..C21H33O19,
)
val piceaGlaucaMix = percentageSolutionOf(
    39.5..celluloseUnit,
    27.5..ligninUnit,
    17.2..C24H42O21,
    10.4..C21H33O19,
)
val betulaVerrucosaMix = percentageSolutionOf(
    41.1..celluloseUnit,
    22.0..ligninUnit,
    2.30..C24H42O21,
    27.5..C21H33O19,
)
val quercusFagaceaeMix = percentageSolutionOf(
    46.1..celluloseUnit,
    22.5..ligninUnit,
    14.2..C24H42O21,
    12.4..C21H33O19,
)
val sodaLimeGlassMix = percentageSolutionOf(
    73.1..SiO2,
    15.0..Na2O,
    7.00..CaO,
    4.10..MgO,
    1.00..Al2O3
)
val obsidianMix = percentageSolutionOf(
    75.48..SiO2,
    11.75..Al2O3,
    3.470..Na2O,
    0.100..MgO,
    0.050..P2O5,
    5.410..K2O,
    0.900..CaO,
    0.100..TiO2,
    2.870..Fe2O3
)
