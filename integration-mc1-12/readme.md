# Minecraft 1.12

THE PLAYERS HAVE SPOKEN.

In a vote that has so far tallied 27-7, players would rather have Minecraft 1.14 support over 1.12 support.

I'm not going to remove this folder, because I leave it up to anyone who wants to port to 1.12, but the main development team will not be working towards a 1.12 release any longer.

Some notes for people who want to do 1.12 development:

* You will need to separate this Gradle project from the project root and use exactly Gradle 4.10.3 in order to have Kotlin support as well as Forge support for 1.12.
* When you set up builds for the project, on *every* run of the build tool, you need to run `gradlew clean`, otherwise you will get Kotlin coroutine errors every time you make a change. There is currently no known workaround, and upstream's fix is to upgrade Gradle.
* UMC *does* support 1.12, so you just target that version of UMC. It shouldn't be drastically different from the 1.14 version.

Good Luck!
