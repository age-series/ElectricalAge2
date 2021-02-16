# Usage

Following are some instructions that have not been tried that should hopefully set ip your development environment.

Good luck.

## Setting up the environment

In general, these instructions are roughly the same for Windows, Mac, and Linux.

### Tools Needed

You need:
* Intellij IDEA (Community Edition is sufficient) [[link](https://www.jetbrains.com/idea/download/)]
    * Optionally, instead install the Jetbrains Toolbox, which you can use to install the software and will also update the software for you. [[link](https://www.jetbrains.com/toolbox-app/)]
* a GIT tool:
    * Windows: [[link](https://git-scm.com/downloads)]
    * Mac: `brew install git`
    * .deb based: `apt install git`
    * .rpm based: `yum install git` (`dnf install git` for Fedora)
    * Arch Linux based: `pacman -Syu git`
* Java 8 SDK. There are a few options:
    * Oracle Java 8 SDK (paid) [[link](https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html)]
    * AdoptOpen JDK 8 (LTS) HotSpot SDK [[link](https://adoptopenjdk.net/?variant=openjdk8&jvmVariant=hotspot)]
    * IBM SDK 8 [[link](https://developer.ibm.com/javasdk/downloads/sdk8/)]
    * Amazon Corretto 8 [[link](https://docs.aws.amazon.com/corretto/latest/corretto-8-ug/what-is-corretto-8.html)]
    * .deb based: `apt install openjdk-8-jdk`
    * .rpm based: `yum instal java-1.8.0-openjdk` (`dnf install java-1.8.0-openjdk` for Fedora)
    * Arch Linux based: `pacman -Syu jdk8-openjdk`
    
IDEA will also install Java 11 or so, but that *will not work* for Eln2 development right now. You must download the JDK

### Clone the repository

First, clone the repository down in whatever method works best for you. Winodws users will want to use Git Bash for these commands.

For most people, use this command. It will create a folder with all of the project info in it.

`git clone https://github.com/eln2/eln2.git`

If you have a GitHub account with SSH keys on it, use this instead:

`git clone git@github.com:eln2/eln2.git`

Once you have downloaded the repository, configure Git:

```sh
git config --global user.name Your Name (or GitHub alias)
git config --global user.email Your Email
```

If you're on Windows, you may type `exit` or close the terminal window.

### Start and configure IDEA

IDEA is pretty simple.

1. Start the application, then say "Open or Import".
2. Find the `build.gradle.kts` file in the root of the repository, then click next.
3. Select all defaults on following screens.

## Add build configurations, and run

From the main IDEA window, you can configure different tasks to run with Gradle.

1. In the upper right, click `Edit Configurations...`
2. Inside this menu, on the top left, click the `+` sign
3. Select Gradle
4. Ignoring the name field, click the folder icon with the blue square next to the text box for Gradle Project
5. Depending on the task you want to run, select the correct project namespace (see below)
6. Enter the task name (see below)
7. Optionally, enter a name for this build/run configuration

Different build configurations:

| Project Namespace | Task Name | Task purpose |
| --- | --- | --- |
| eln2:core | test | Tests the Eln2 Core |
| eln2:core | build | Builds the Eln2 Core, running tests |
| eln2:apps | build | Builds the Eln2 Applications (requires eln2:core automatically) |
| eln2 | build | Builds and runs tests for all of Eln2 |
| eln2 | bundle | Builds and runs tests for all of Eln2 |
| eln2:integration-mc-1-16 | runClient | Runs a minecraft client in a development environment (no username, cannot connect to servers) |
| eln2:integration-mc-1-16 | runServer | Runs a minecraft server in a development environment |
| eln2:integration-mc-1-16 | publish | Supposedly, builds a usable Minecraft mod. You will need to install Bookshelf, Forge, and some other tidbits... |

## Contributing back

TODO: [Better] Instructions here.

Mostly the following:

1. `git checkout -b feature/<your new feature>`
2. Verify that you have changed what you want by running `git status`
3. `git add .`
4. `git commit`
5. Push the code to upstream
    * If you have Eln2 repository access
        1. `git push origin feature/<your new feature>`
    * If you do not have access:
        1. Fork the repo on GitHub
        2. Push the code
            * If you used the repo links above to clone, do:
                1. `git remote add fork <your remote url`
                2. `git push fork master`
            * Otherwise, do:
                1. `git push origin master` ("origin" will be your fork)
6. Make a pull request. If you are confident that you are done with writing the code, and it meets contribution guidelines in [CONTRIBUTING.md](CONTRIBUTING.md), then make it as a standard pull request. Otherwise, select the down arrow and then `Draft PR` and then click Create Pull Request.
7. If you have further changes, start with step 2 and continue to step 5. It will update the PR with changes.
8. Wait for someone to accept or reject your PR. We get email notifications when PR's are created. We may make suggestions, in which you fix your code and come back to us with those changes.
