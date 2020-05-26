#!/usr/bin/python3

import json
from pprint import pprint

fileList = {}

# NOTE: For some reason, Python is not expanding ~/

# Yeah, right. In your dreams!
#PREFIX = "~/.config/Vintagestory/assets/survival/"

# This folder is where I put my fixed ones in: http://www.fixjson.com/
PREFIX = "~/Documents/vsoredist/"

# Good for a list of ores
fileList["item-nugget"] = PREFIX + "itemtypes/resource/nugget.json"

# Good for unit counts of some ores, as well as rock types
fileList["item-ore-graded"] = PREFIX + "itemtypes/resource/ore-graded.json"

# Meh
fileList["item-ore-ungraded"] = PREFIX + "itemtypes/resource/ore-ungraded.json"

# Good for unit counts of some ores, as well as rock types
fileList["item-crystalized-ore"] = PREFIX + "itemtypes/resource/crystalizedore-graded.json"

# Rock types as well as meteoric iron and griding rocks for lime and salt
fileList["item-stone"] = PREFIX + "itemtypes/resource/stone.json"

# A bunch of ores and the rocks they are found in
fileList["block-looseores"] = PREFIX + "blocktypes/stone/looseores.json"

# Some gems and teh rocks they are found in
fileList["block-ore-gem"] = PREFIX + "blocktypes/stone/ore-gem.json"

# A lot of stuff about ores and the rocks they are in
fileList["block-ore-graded"] = PREFIX + "blocktypes/stone/ore-graded.json"

# A lot of other ores.
fileList["block-ore-ungraded"] = PREFIX + "blocktypes/stone/ore-ungraded.json"

jsonfiles = {}
jsondata = {}

oreList = []
rocks = []
ore = {}

for entry in fileList:
    f = open(fileList[entry])
    jsonfiles[entry] = f.read().replace("\r\n", "\n")
    jsondata[entry] = json.loads(jsonfiles[entry])
    f.close()

#for entry in jsonfiles:
    #print(jsonfiles[entry])
    #pprint(jsondata[entry])

def parseItemNugget(inputData):
    oreList = []
    oreTempProperties = {}
    for entry in inputData["combustiblePropsByType"]:
        oreName = entry.split("*-",1)[1]
        oreList.append(oreName)
    return oreList

# <type>-<grade>-<ore>-<rock>
def parseOreGraded(inputData):
    global ore, rocks
    for entry in inputData["allowedVariants"]:
        dat = entry.split("-")
        oreName = dat[2]
        rockType = dat[3]
        grade = dat[1]

        if (oreName not in ore):
            ore[oreName] = {}
        if (rockType not in ore[oreName]):
            ore[oreName][rockType] = []
        if (grade not in ore[oreName][rockType]):
            ore[oreName][rockType].append(grade)
        if (rockType not in rocks and rockType != "*"):
            rocks.append(rockType)

# <type>-<ore>-<rock>
def parseOreUngraded(inputData):
    global ore, rocks
    for entry in inputData["allowedVariants"]:
        dat = entry.split("-")
        oreName = dat[1]
        rockType = dat[2]
        if (oreName not in ore):
            ore[oreName] = {}
        if (rockType not in ore[oreName]):
            ore[oreName][rockType] = []
        if ("exists" not in ore[oreName][rockType]):
            ore[oreName][rockType].append("exists")
        if (rockType not in rocks and rockType != "*"):
            rocks.append(rockType)

def parseItemRocks(inputData):
    rocks = []
    for entry in inputData["attributesByType"]:
        if ("stone" in entry):
            rocks.append(entry.split("-",1)[1])
    return rocks

#
# Actual parser calls start here
#

if (True):
    for entry in parseItemNugget(jsondata["item-nugget"]):
        if entry not in oreList:
            oreList.append(entry)

if (True):
    parseOreGraded(jsondata["item-ore-graded"])

if (True):
    parseOreGraded(jsondata["item-crystalized-ore"])

if (True):
    for entry in parseItemRocks(jsondata["item-stone"]):
        if entry not in rocks:
            rocks.append(entry)

if (True):
    parseOreUngraded(jsondata["block-looseores"])

if (True):
    parseOreGraded(jsondata["block-ore-gem"])

if (True):
    parseOreGraded(jsondata["block-ore-graded"])

if (True):
    parseOreUngraded(jsondata["block-ore-ungraded"])

pprint(oreList)
pprint(rocks)
pprint(ore)
