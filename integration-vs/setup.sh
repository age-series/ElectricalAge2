#!/usr/bin/env bash

echo "Installing VINTAGE_STORY and VINTAGE_STORY_DATA env variables to ~/.bashrc"

echo "" >> ~/.bashrc
echo "# Vintage Story Environment" >> ~/.bashrc

if [ -d "/usr/share/vintagestory" ]; then
  echo "Found system Vintage Story installation in /usr/share/vintagestory"
  echo "export VINTAGE_STORY=/usr/share/vintagestory" >> ~/.bashrc
else
  echo "System Vintage Story installtion not detected, using $HOME/.config/Vintagestory instead"
  echo "export VINTAGE_STORY=$HOME/.config/Vintagestory" >> ~/.bashrc
fi

echo "export VINTAGE_STORY_DATA=$HOME/.config/VintagestoryData" >> ~/.bashrc

echo "Sourcing modified ~/.bashrc file.."
source ~/.bashrc

echo "Done!"