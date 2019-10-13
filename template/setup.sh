BRANCH="$1"
MODID="$2"
MODNAME="$3"
MODVERSION="$4"
MODCLASS="$5"

rm -rf src/main/java/cam72cam/$MODID/Mod.java
rm -rf *gradle*

cp UniversalModCore/template/src/main/java/cam72cam/mod/Mod.java src/main/java/cam72cam/$MODID/Mod.java
cp -r UniversalModCore/template/*gradle* .

sed -i build.gradle -e "s/#MODID#/$MODID/" -e "s/#MODNAME#/$MODNAME/" -e "s/#MODVERSION#/$MODVERSION/"
sed -i src/main/java/cam72cam/$MODID/Mod.java -e "s/#MODID#/$MODID/" -e "s/#MODNAME#/$MODNAME/" -e "s/#MODVERSION#/$MODVERSION/" -e "s/#MODCLASS#/$MODCLASS/"

cat<<EOT
It's recommended you now run the following for intellij setup:
	./gradlew clean
	./gradlew cleanIdea
	./gradlew cleanIdeaWorkspace
	./gradlew setupDevWorkspace
	./gradlew classes
	./gradlew idea
	./gradlew genIntellijRuns


If you wish to use eclipse, simply replace idea with eclipse in the above commands
EOT
