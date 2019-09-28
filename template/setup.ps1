param (
		[string]$BRANCH,
		[string]$MODID,
		[string]$MODNAME,
		[string]$MODVERSION,
		[string]$MODCLASS
)

if(Test-Path -Path src/main/java/cam72cam/$MODID/Mod.java ){
		Remove-Item -Recurse -Force src/main/java/cam72cam/$MODID/Mod.java
}
Remove-Item -Recurse -Force *gradle*

Copy-Item -Path UniversalModCore/template/src/main/java/cam72cam/mod/Mod.java -Destination src/main/java/cam72cam/$MODID/
Copy-Item -Path UniversalModCore/template/*gradle* -Destination $PWD -Recurse

(Get-Content build.gradle) -replace "#MODID#", "$MODID" | Set-Content build.gradle
(Get-Content build.gradle) -replace "#MODNAME#", "$MODNAME" | Set-Content build.gradle
(Get-Content build.gradle) -replace "#MODVERSION#", "$MODVERSION" | Set-Content build.gradle
(Get-Content src/main/java/cam72cam/$MODID/Mod.java) -replace "#MODID#", "$MODID" | Set-Content src/main/java/cam72cam/$MODID/Mod.java
(Get-Content src/main/java/cam72cam/$MODID/Mod.java) -replace "#MODNAME#", "$MODNAME" | Set-Content src/main/java/cam72cam/$MODID/Mod.java
(Get-Content src/main/java/cam72cam/$MODID/Mod.java) -replace "#MODVERSION#", "$MODVERSION" | Set-Content src/main/java/cam72cam/$MODID/Mod.java
(Get-Content src/main/java/cam72cam/$MODID/Mod.java) -replace "#MODCLASS#", "$MODCLASS" | Set-Content src/main/java/cam72cam/$MODID/Mod.java

Write-Host @"
It's recommended you now run the following for intellij setup:
	./gradlew.bat clean
	./gradlew.bat cleanIdea
	./gradlew.bat classes
	./gradlew.bat idea

If you wish to use eclipse, simply replace idea with eclipse in the above commands
"@
