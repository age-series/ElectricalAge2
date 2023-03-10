# Dataset Guidelines

### Mention Source
Always mention the data source. Usually, this can be done by creating a "src" file and adding all relevant information there (e.g. the URL of the webpage).

### Always Sort Values
You should always sort values, because some interpolation APIs need that to function correcly.

### Data Types

- 1D Data Points
  - List of values
  - Mapped using a 0-1 parameter
- 1D Mapped Data Points
  - List of values with an arbitrary parameter
  - Usually written in CSV files, with one column having the key and one column having the value
- 2D Grid
  - List of values with two arbitrary parameters
  - Usually written in CSV files, with a "dead" cell in the top left corner, and the two parameters specified on the row, column adjacent to the dead cell.
