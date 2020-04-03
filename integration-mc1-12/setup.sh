branch=$1

MODID="eln2"
NAME="Electrical Age 2"
VERSION="0.1.0"

CYAN="$(echo -ne "\x1b[1;36m")"
YELLOW="$(echo -ne "\x1b[33m")"
RESET="$(echo -ne "\x1b[m")"

echo "$CYAN * Setting up UniversalModCore on branch $YELLOW$branch$CYAN ...$RESET"

if [ -z "$branch" ]; then
	branch="forge_1.12.2"
fi

gitPfx="https://github.com/"

rm -rf ./UniversalModCore

git clone --branch $branch ${gitPfx}TeamOpenIndustry/UniversalModCore.git

./UniversalModCore/template/setup.sh $branch "$MODID" "$NAME" "$VERSION" org.eln2.mc.ElnMain

sed -i -e 's/\r$//' build.gradle  # "dos2unix"
DOLLAR='$'  # XXX note that vars inside the here-string are expanded
patch -p1 -u build.gradle <<EOT
--- a/build.gradle	2020-01-11 19:56:16.533106798 -0500
+++ b/build.gradle	2020-01-11 20:05:42.698548351 -0500
@@ -4,12 +4,14 @@
 }
 
 buildscript {
+    ext.kotlin_version = '1.3.61'
     repositories {
         jcenter()
         maven { url = "http://files.minecraftforge.net/maven" }
     }
 	dependencies {
         classpath 'net.minecraftforge.gradle:ForgeGradle:2.3-SNAPSHOT'
+        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${DOLLAR}kotlin_version"
 	}
 }
 
@@ -21,12 +23,13 @@
 }
 
 apply plugin: 'net.minecraftforge.gradle.forge'
+apply plugin: 'kotlin'
 //Only edit below this line, the above code adds and enables the necessary things for Forge to be setup.
 
 
 version = "0.1.0_1.12.2"
 group = "cam72cam.universalmodcoremod" // http://maven.apache.org/guides/mini/guide-naming-conventions.html
-archivesBaseName = "Electrical Age 2"
+archivesBaseName = "$MODID"
 
 sourceCompatibility = targetCompatibility = '1.8' // Need this here so eclipse task generates correctly.
 compileJava {
@@ -56,7 +59,12 @@
 }
 
 dependencies {
-	shade 'inventory-tweaks:InventoryTweaks:1.63:api'
+	shade('inventory-tweaks:InventoryTweaks:1.63:api') {
+		because "needed in UniversalModCore"
+	}
+
+	implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlin_version"
+	implementation 'org.apache.commons:commons-math3:3.6.1'
 }
 
 jar {
EOT

sed -i -e 's/2\.14/4.10.3/' gradle/wrapper/gradle-wrapper.properties

cat<<EOT

$CYAN * $NAME $VERSION is set up.$RESET

You can build the JAR by running
${YELLOW}./gradlew setupDecompWorkspace$RESET
followed by
${YELLOW}./gradlew build$RESET
The output will be found at
${YELLOW}build/libs/$MODID-${CYAN}version$YELLOW.jar$RESET
(for the appropriate ${CYAN}version$RESET).

EOT
