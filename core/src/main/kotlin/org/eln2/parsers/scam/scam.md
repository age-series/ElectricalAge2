# SCAM Parser

SCAM - Symbolic Circuit Analysis with Matlab

This parser allows parsing of SCAM netlists.

See the [scam repository](https://github.com/echeever/scam) for code, examples, and more details.

NOTE: Some components in the format are not currently supported.

## SCAM Format

Different components have different parsers for them.

```
Rxxx N1 N2 resistance (ohms) [Resistor]
Cxxx N1 N2 capacitance (farads) [Capacitor]
Lxxx N1 N2 inductance (henry) [Inductor]
Vxxx N1 N2 Voltage (volts) [Voltage Source N1: Annode(-), N2: Cathode(+)]
Ixxx N1 N2 Current (amps) [Current Source N1 to N2]
Oxxx [Op Amp]
Exxx [Voltage controled Voltage Source]
Gxxx [Voltage controlled Current Source]
Fxxx [Current controlled Current Source]
Hxxx [Current controlled voltage source]
```

Currently, just resistors, capacitors, inductors, voltage sources, and current sources are supported.
