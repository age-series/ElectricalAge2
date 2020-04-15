#!/usr/bin/env bash

echo "Installing VINTAGE_STORY and VINTAGE_STORY_DATA env variables to ~/.bashrc and ~/.xprofile"

echo "" >> ~/.bashrc
echo "# Vintage Story Environment" >> ~/.bashrc
echo "# Vintage Story Environment" >> ~/.xprofile

if [ -d "/usr/share/vintagestory" ]; then
  echo "Found system Vintage Story installation in /usr/share/vintagestory"
  echo "export VINTAGE_STORY=/usr/share/vintagestory" >> ~/.bashrc
  echo "export VINTAGE_STORY=/usr/share/vintagestory" >> ~/.xprofile
else
  echo "System Vintage Story installtion not detected, using $HOME/.config/Vintagestory instead"
  echo "export VINTAGE_STORY=$HOME/.config/Vintagestory" >> ~/.bashrc
  echo "export VINTAGE_STORY=$HOME/.config/Vintagestory" >> ~/.xprofile
fi

echo "export VINTAGE_STORY_DATA=$HOME/.config/VintagestoryData" >> ~/.bashrc
echo "export VINTAGE_STORY_DATA=$HOME/.config/VintagestoryData" ~/.xprofile

echo "Sourcing modified ~/.bashrc file.."
source ~/.bashrc
echo "You may need to log out and log back iin for the ~/.xprofile to be loaded."

echo "Done!"