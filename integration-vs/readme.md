# Vintage Story

Vintage Story is a survivalist sandbox game similar to Minecraft. It uses C# and has a supported modding API.

It seems to be the spiritual successor of TFC for Minecraft, without the Minecraft.

## Project Setup

You need the following:

* Mono
* VS Code or VS Codium (non-telemetry VS Code clone)
* Vintage Story installed on your system

Currently, the only development platform supported is Linux. If you want to develop on Windows, it may work out of the box, but no gurantees.

You need two environment variables set:

`VINTAGE_STORY` and `VINTAGE_STORY_DATA`

There is a script you can run called `setup.sh` that will try to enter these correctly into your `~/.bashrc` and `~/.xprofile`.

Once you have those, the project file will correctly find the game.

To actually launch the game, on the left, click the Run profiles tab, and then select `Launch Client (Mono)` or `Lanch Server (Mono)`. The other option does not currently appear to work and I think it's due to using an incorrect dotnet import for full instead of core, but not sure. 

This will probably get a little more complicated once Eln2 Core is required to run this. My presumption is that Eln2 Core will be started as a network daemon on your system before you try to launch this.