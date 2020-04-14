import { readFile } from 'fs';
import { join, resolve as pathResolve } from 'path';
import { exit } from 'process';

import { CIMinecraftForgeServerTest } from 'forge-server-test';

const buildLibsDirectory = pathResolve('integration-mc1-14', 'build', 'libs');
const serverDirectory = pathResolve('integration-mc1-14', 'run');

// Minecraft and Forge versions
let minecraftVersion = null;
let forgeVersion = null;
// Path to mod .jar file
let modFilepath = null;

// Get the Minecraft, Forge and mod versions from build.gradle
function getVersions() {
    return new Promise((resolve, reject) => {
        readFile(pathResolve('integration-mc1-14', 'build.gradle'), 'UTF-8', (err, data) => {
            if (err != null) {
                reject(err);
                return;
            }

            let modVersion = null;

            data
                .split('\n')
                .forEach((line, i) => {
                    if (line.startsWith('def')) {
                        const varName = line.split(' ')[1];
                        const varValue = line.split('\'')[1];

                        if (varName === 'mcVersion') {
                            minecraftVersion = varValue;
                        } else if (varName === 'forgeVersion') {
                            forgeVersion = varValue;
                        } else if (varName === 'modVersion') {
                            modVersion = varValue;
                        }
                    }
                });

            if (minecraftVersion == null || forgeVersion == null) {
                reject('Could not find Minecraft and/or Forge version');
                return;
            }

            if (modVersion == null) {
                reject('Could not find mod version');
                return;
            }

            modFilepath = join(buildLibsDirectory, `ModName-mc${minecraftVersion}-${modVersion}.jar`);
            resolve();
        });
    });
}

// Create instance of ci-minecraft-forge-server-test
const tester = new CIMinecraftForgeServerTest();
getVersions()
    .then(() => {
        tester
            .setVersions(minecraftVersion, forgeVersion) // Set versions
            .acceptEULA()                                // Accept Minecraft's EULA
            .useServerProperties()                       // Use ci-minecraft-forge-server-test's server.properties file
            .setServerDirectory(serverDirectory)         // Set the directory for the server
            .addLocalMod(modFilepath)                    // Add the mod
            .addCommand('forge tps');                    // Add command to run

        return tester.installForge()                     // Install Forge
            .then(() => tester.runServer());             // Run the server
    })
    .catch(err => {
        console.error(err);
        exit(1);
    });
