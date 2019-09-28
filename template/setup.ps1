param (
		[string]$BRANCH,
		[string]$MODID,
		[string]$MODNAME,
		[string]$MODVERSION,
		[string]$MODCLASS
)

git clone git@github.com:TeamOpenIndustry/UniversalModCore.git --branch $BRANCH
git clone git@github.com:TeamOpenIndustry/TrackAPI.git --branch $BRANCH

(Get-Content build.gradle) -replace '#MODID#', '$MODID' | Set-Content build.gradle
(Get-Content build.gradle) -replace '#MODNAME#', '$MODNAME' | Set-Content build.gradle
(Get-Content build.gradle) -replace '#MODVERSION#', '$MODVERSION' | Set-Content build.gradle
New-Item -ItemType directory src/main/java/cam72cam/mod/$MODID/
Move-Item src/main/java/cam72cam/mod/Mod.java src/main/java/cam72cam/mod/$MODID/Mod.java

(Get-Content src/main/java/cam72cam/mod/$MODID/Mod.java) -replace '#MODID#', '$MODID' | Set-Content src/main/java/cam72cam/mod/$MODID/Mod.java
(Get-Content src/main/java/cam72cam/mod/$MODID/Mod.java) -replace '#MODNAME#', '$MODNAME' | Set-Content src/main/java/cam72cam/mod/$MODID/Mod.java
(Get-Content src/main/java/cam72cam/mod/$MODID/Mod.java) -replace '#MODVERSION#', '$MODVERSION' | Set-Content src/main/java/cam72cam/mod/$MODID/Mod.java

Remove-Item -Force -Recurse .git
./gradlew.bat idea
