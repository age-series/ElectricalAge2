# AsmComputer

## Registers

Integer Registers:

```
ia
ib
ic
id
ie
if
ig
ih
```

Double (FPU) Registers:

```
dx
dy
dz
```

String Registers:

```
sx
sy
sz
cra: Code Register A
crb: Code Register B
si[0-9]: Serial in (read then clear to allow more input)
so[0-9]: Serial out (write to have output)
```

## Operators

All operators use the leftmost operand as the destination register.

* `addi <register> (<register or literal> 1 .. N]`: Adds registers and literals to a register.
* `addd <register> (<register or literal> 1 .. N]`: Adds registers and literals to a register.
* `subi <register> <register or literal>`: Subtracts a register or literal from a register.
* `subd <register> <register or literal>`: Subtracts a register or literal from a register.
* `noop`: No-Op
* `move <register> <register or literal>`: Copies the data from the second register or literal to the first register
* `swch [literal]`: Switch to the opposite code register. Allows specifying a PTR to jump to.
* `strp <register> <register> <literal> <literal>`: Allows copying a string from one string register to another with specific beginning and end points
* `strl <register> <register>`: Copies the length of the string register into an integer register
* `labl <literal>`: A label that `jump` and other code points can jump to (TODO: `swch`)
* `jump <literal>`: A PTR or label to jump to.
* `jpgt <register or literal> <register or literal> <literal>`: Jump if the left register is greater than the right one to the PTR or label specified.
* `jplt <register or literal> <register or literal> <literal>`: Jump if the left register is less athan the right one to the PTR or label specified
* `jpge <register or literal> <register or literal> <literal>`: Jump if the left register is greater than or equal to the right one to the PTR or label specified
* `jple <register or literal> <register or literal> <literal>`: Jump if the left register is less than or equal to the right one to the PTR or label specified
* `jpeq <register or literal> <register or literal> <literal>`: If the registers are equal, jump to the literal PTR or label specified.
* `hasi <register> <register or literal>`: If the register exists, put a 1 in the dest register, otherwise put a 0.
* `hasd <register> <register or literal>`: If the register exists, put a 1 in the dest register, otherwise put a 0.
* `hass <register> <register or literal>`: If the register exists, put a 1 in the dest register, otherwise put a 0.
