from PIL import Image
from pprint import pprint
from os import listdir
from os.path import isfile, join, expanduser

oreHexColors = {}

oreHexFile = open("ore_hex_color.csv", "r")
contents = oreHexFile.read().replace("\r", "").split("\n")
header = [x.strip() for x in contents[0].split(",")]

nameIndex = header.index("Name")
oreHexColorIndex = header.index("Ore Hex Color")

# Skips first header line in file
for line in contents[1:]:
	datum = line.split(",")
	# Check that all fields are "populated" (so that we don't get index out of bounds errors)
	if len(datum) != len(header):
		continue
	name = datum[nameIndex]
	oreHexColor = datum[oreHexColorIndex]
	oreHexColors[name] = oreHexColor

# Cool! We got all of the ore hex colors.

# Note - only works on Linux.
# TODO: Add Windows/Mac support
VINTAGE_STORY_STONE_PATH = expanduser("~/.config/Vintagestory/assets/survival/textures/block/stone/rock")
TEMPLATES_PATH = expanduser("template")

templateList = ["{}/{}".format(TEMPLATES_PATH, f) for f in listdir(TEMPLATES_PATH) if isfile(join(TEMPLATES_PATH, f)) and ".png" in f]
stoneTypes = ["{}/{}".format(VINTAGE_STORY_STONE_PATH, f) for f in listdir(VINTAGE_STORY_STONE_PATH) if isfile(join(VINTAGE_STORY_STONE_PATH, f))]

outputFolder = "output"

pprint(oreHexColors)
pprint(templateList)
pprint(stoneTypes)

for templateImageName in templateList:
	for oreName in oreHexColors:
		for stoneName in stoneTypes:
			with Image.open(templateImageName) as templateImage:
				with Image.open(stoneName) as stoneImage:
					outputFilename = "{}_{}_{}.png".format(templateImageName.rsplit("/",1)[1].split(".",1)[0], oreName, stoneName.rsplit("/",1)[1].split(".",1)[0])
					templateSplit = templateImage.split()
					oreColorImage = Image.new('RGBA', stoneImage.size, color="#{}".format(oreHexColors[oreName]))
					oreColorImage.putalpha(templateSplit[3])
					finalImage = Image.alpha_composite(stoneImage, oreColorImage)  # , 100)#, templateSplit[0])
					finalImage.save(outputFolder + "/" + outputFilename)
