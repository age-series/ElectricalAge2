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

# Give the server 120 seconds to start and generate a world file.
# If we start getting errors where it doesn't finish fast enough, might need to raise this value
sleep 120

echo "Killing session"
tmux kill-session

echo "Printing server latest log:"
cat ./run/logs/latest.log

echo "Checking successful server load (checking for loaded message)"
FINISH=`cat ./run/logs/latest.log | grep "Done" | grep "For help, type \"help\"" | wc -l`

if [ "$FINISH" == "1" ]; then
    echo "Server started successfully! :)"
else
    echo "Server failed to load :("
    exit 1
fi
