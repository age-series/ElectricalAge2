## Physical Fluid & Transformation Standards

The Fluid Handling in ELN2 was designed to be compatible with Forge Fluids and other mods.
This constraint doesn't allow us to have pressure. We are only able to attach temperature data to fluid transfers, using some conventions about coercing mass between the thermal world (ELN2) and the non-thermal world (other mods).
Due to the absence of pressure, we can't load real-world datasets for fluids and transformations; we have simplifications.
To make sure we don't create mass or energy, we need to follow these conventions to the letter:

### 1. The Millibucket-Mass Equivalence
We treat the Millibucket (mB) as a **Unit of Mass**, specifically anchored to the substance's **Liquid Phase**.

* **1 mB Liquid** = Mass of **1 Liter** of the substance in its **Liquid State** at 1 atmosphere.
    * *Standard Liquids:* For Water, Diesel, or Oil, this is their density at STP (to make importing data easier).
    * *When the Fluid is a Gas at STP:* For substances like Methane, Propane, Butane, Liquid Nitrogen, use the density of their **Cryogenic Liquid form**.
* **1 mB Gas** = Mass of **1 Liter / Gas Expansion Factor** of the substance in its **Liquid State**.
    * A `FluidStack` of 1 mB Gas has **less mass** than 1 mB of its liquid form, by the expansion factor.
    * This is because gases expand when boiled. Without pressure, we can't model real expansion ratios (which are 600x to 1000x), so we use a fixed **Gas Expansion Factor** of **100**.
    * This means 1 mB of liquid boils into **100 mB** of gas, and each mB of gas carries **1/100th** of the liquid mass.
    * The expansion factor is stored per-fluid in the physical fluid data file as `gasExpansionFactor`. Liquids use `1.0`, gases use `100.0`.

### 2. Fluid Data (`data/eln2/physical_fluid/`)
Derive all properties from the **Liquid State Density**, even for fluids that are naturally gaseous.

* **Density (`density`):**
    * **Value:** Real-world density of the **Liquid Phase** in **kg/L** (thanks to **1 mB Liquid** = **1L**)
    * *Example (Diesel):* 0.82 kg/L -> `0.82`.
    * *Example (Methane):* Liquid Methane is ~0.42 kg/L -> `0.42`.
    * **Gases:** _Must_ use this same Liquid Density. The expansion factor handles the mass reduction for gas mB.

* **Specific Heat (`specificHeatCapacity`):**
    * Defined in $J / (mB \cdot K)$, as the **liquid-equivalent** value.
    * **Formula:**
      $$Cp_{eln2} = Cp_{real\_gravimetric} \times Density_{liquid\_anchor}$$
    * *Example:* Methane Gas ($Cp \approx 2200 \text{ J/kgK}$).
      $$2200 \times 0.42 = 924\ J/(mB \cdot K)$$
    * This is the heat capacity per mB of **liquid-equivalent**. The code divides by the expansion factor when computing the actual thermal energy of a gas `FluidStack`.

* **Gas Expansion Factor (`gasExpansionFactor`):**
    * **Liquids:** `1.0` (1 mB liquid = 1 mB liquid-equivalent).
    * **Gases:** `100.0` (1 mB gas = 0.01 mB liquid-equivalent).
    * The effective mass of a gas `FluidStack` is: `amount * density / gasExpansionFactor`.
    * The effective heat capacity of a gas `FluidStack` is: `amount * specificHeatCapacity / gasExpansionFactor`.

### 3. Transformations and Enthalpy
Phase changes obey strict energy conservation. This is very important, as we don't want an exploit that generates free energy.

* **Enthalpy (`enthalpy`):**
    * Defined as **Joules per Liquid-Equivalent Input mB**.
    * This is the energy required to boil 1 mB of liquid (or released by condensing 1 mB of liquid-equivalent gas).
    * The code applies enthalpy to the **liquid-equivalent** amount, not the raw gas mB amount.

* **Standard Phase Change (1:1):**
    * Use the real-world Enthalpy of Vaporization (scaled to 1 L/kg).
    * *Example:* Water -> Steam ($2260 kJ/kg$). Enthalpy = `2,260,000`.
    * When boiling 1 mB of water, 100 mB of steam is produced. The latent heat is `2,260,000 * 1 = 2,260,000 J` (applied to the liquid input amount, not the gas output amount).

* **Fractional Distillation:**
    * If boiling splits a fluid (e.g., Oil -> Naphtha Gas + Heavy Oil), you **only pay enthalpy for the portion that becomes gas**.
    * **Formula:**
      $$Enthalpy_{eln2} = Enthalpy_{real} \times \frac{GasProportion}{1000}$$
    * *Example:* Distilling Heavy Oil (Enthalpy ~300kJ). If yield is 60% Gas / 40% Heavy Oil:
      $$Enthalpy = 300,000 \times 0.6 = 180,000$$
    * *Reasoning:* The Heavy Oil remains liquid; it retains its sensible heat and does not require Latent Heat of Vaporization.
    * The enthalpy is applied to the **liquid input amount** (the mB being boiled), not the gas output amount. The `resultGasProportion` determines how much gas is produced, but the enthalpy is already pre-scaled by the proportion in the data file.

* **Gas Amounts in Transformations:**
    * When boiling `N` mB of liquid with `resultGasProportion = P` and `gasExpansionFactor = F`:
      * Gas produced = $N \times \frac{P}{1000} \times F$ mB of gas.
    * When condensing `M` mB of gas with `resultLiquidProportion = P` and `gasExpansionFactor = F`:
      * Liquid produced = $M \times \frac{P}{1000} \div F$ mB of liquid.
    * The enthalpy is always applied to the **liquid-equivalent** amount:
      * Evaporation: `latentHeat = enthalpy * liquidInputAmount`
      * Condensation: `latentHeat = enthalpy * (gasInputAmount / gasExpansionFactor)`

### 4. Naming Conventions
We use different naming conventions for liquids at STP and gases at STP:
* **Liquids at STP:**
    * Liquid Form - simply the usual name:
      * *Example:* Water (`minecraft:water`), Naphtha (`eln2:naphtha`)
    * Gas Form - apply the suffix "gas":
      * *Example:* Naphtha Gas (`eln2:naphtha_gas`)
* **Gases at STP:**
    * Liquid (Cryogenic) Form - apply the prefix "liquid":
      * *Example:* Liquid Hydrogen (`eln2:liquid_hydrogen`)
    * Gas Form - simply the usual name:
      * *Example:* Hydrogen (`eln2:hydrogen`)

### 5. Game-Energy Scaling
Real-world fluid energy values are enormous compared to the thermal mass of in-game machines. A single distillation module is 50 kg of copper (19,250 J/K), while water's enthalpy of vaporization is 2.26 MJ/kg. At real-world values, condensing a small amount of steam would spike a module's temperature beyond playability.

To bring the ratio of fluid energy to machine thermal mass into a playable range, all fluid-specific energy properties are scaled by a constant `PROPERTY_SCALAR` ($1/10$) at load time:

* **Applied to:** `specificHeatCapacity` and `enthalpy` (in both boiling and condensation transformations).
* **Not applied to:** `density`, `gasExpansionFactor`, fuel energy density (e.g. coal at 24 MJ/kg), or machine thermal mass. These are material/structural properties, not fluid properties.
* **JSON files keep real-world values.** The scaling is applied in the loaders (`PhysicalFluidManager.apply` and `FluidTransformationManager.apply`), so the DTOs and all downstream code see scaled values. Tooltips display the scaled (game-internal) values, since those are what the simulation actually uses.
* **Effect:** Per gas mB energy drops 10x, giving the player time to react to heating and making passive cooling feasible. Total fuel energy is conserved: the same coal boils more water per mB, but the total energy released is unchanged.
