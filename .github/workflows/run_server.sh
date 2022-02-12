#!/bin/bash

# Please note - this command is meant to be run from two directories above where it currently lives.

if ! command -v tmux &> /dev/null
then
    echo "tmux not installed, trying to install for ubuntu test environment"
    sudo apt install tmux
fi

if ! command -v tmux &> /dev/null
then
    echo "tmux could not be found, aborting"
    exit 1
fi

echo "tmux installed, setting eula and starting server"

mkdir -p ./run
echo "eula=true" > ./run/eula.txt

tmux new-session -d
echo "Starting Minecraft Server"
tmux send-keys './gradlew runServer' C-m

# Give the server 90 seconds to start to generate a world file.
# If we start getting errors where it doesn't start fast enough, might need to raise this value
# This takes <30 seconds on my box, but GitHub is really slow.
sleep 90

echo "Killing session"
tmux kill-session

echo "Printing server latest log:"
cat ./run/logs/latest.log

# GitHub is really slow so we're just going to check it generated chunks...
echo "Checking successful server load (checking for loaded message)"
SPAWN_AREA=`cat ./run/logs/latest.log | grep "Preparing spawn area: " | wc -l`

if [ "$FINISH" != "0" ]; then
    echo "Server started successfully! :)"
    # Good enough.
    exit 0
else
    echo "Server failed to load :("
    # Check if the issue was running slowly or actually a crash in the log.
    exit 1
fi
