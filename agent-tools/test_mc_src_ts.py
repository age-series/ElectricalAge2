#!/usr/bin/env python3
"""
test_mc_src_ts.py - Tests for mc-src-ts.py (tree-sitter-based source reader).

Run from the project root (ElectricalAge2 directory).
"""

import subprocess
import sys

TOOL = "python agent-tools/mc-src-ts.py"

T = {
    "BlockEntity": "net.minecraft.world.level.block.entity.BlockEntity",
    "VoxelShape": "net.minecraft.world.phys.shapes.VoxelShape",
    "BlockBehaviour": "net.minecraft.world.level.block.state.BlockBehaviour",
    "GameRenderer": "net.minecraft.client.renderer.GameRenderer",
    "LevelRenderer": "net.minecraft.client.renderer.LevelRenderer",
}

tests_run = 0
tests_passed = 0
tests_failed = []


def run(cmd, desc, expect_failure=False):
    global tests_run, tests_passed, tests_failed
    tests_run += 1
    try:
        result = subprocess.run(
            cmd, capture_output=True, text=True, shell=True, timeout=30
        )
        rc = result.returncode
        stdout = result.stdout

        if expect_failure:
            if rc != 0:
                print("  PASS: %s (expected failure, rc=%d)" % (desc, rc))
                tests_passed += 1
            else:
                print("  FAIL: %s - expected failure but got rc=0" % desc)
                tests_failed.append(desc)
        else:
            if rc == 0 and stdout.strip():
                lines = stdout.strip().splitlines()
                print("  PASS: %s (rc=%d, %d lines)" % (desc, rc, len(lines)))
                tests_passed += 1
            elif rc == 0 and not stdout.strip():
                print("  FAIL: %s - rc=0 but no output" % desc)
                tests_failed.append(desc)
            else:
                stderr = result.stderr
                print("  FAIL: %s - rc=%d" % (desc, rc))
                if stderr.strip():
                    for s in stderr.strip().splitlines()[:3]:
                        print("    err: %s" % s)
                tests_failed.append(desc)
    except subprocess.TimeoutExpired:
        print("  FAIL: %s - TIMEOUT (>30s)" % desc)
        tests_failed.append(desc)
    except Exception as e:
        print("  FAIL: %s - %s" % (desc, e))
        tests_failed.append(desc)


def section(name):
    print("\n" + "=" * 60)
    print("  " + name)
    print("=" * 60)


# === 1. Basic commands ===
section("1. Basic commands")

run("%s read %s --lines 1-10" % (TOOL, T["BlockEntity"]),
    "read: first 10 lines of BlockEntity")

run("%s read %s --lines 38-42" % (TOOL, T["BlockEntity"]),
    "read: lines 38-42 (constructor)")

run('%s grep "getLevel" --class BlockEntity -F --max 5' % TOOL,
    "grep: getLevel in BlockEntity (-F, --max 5)")

run('%s grep "public|private|protected" --class VoxelShape --max 10' % TOOL,
    "grep: visibility keywords in VoxelShape (regex)")

run("%s list net.minecraft.world.level.block.entity" % TOOL,
    "list: package block.entity")

run("%s find BlockEntity" % TOOL,
    "find: classes containing BlockEntity")

run("%s method %s getBlockPos --lines 1-3" % (TOOL, T["BlockEntity"]),
    "method: BlockEntity.getBlockPos")

run("%s method %s getProjectionMatrix --lines 1-5" % (TOOL, T["GameRenderer"]),
    "method: GameRenderer.getProjectionMatrix")

# Method with javadoc
run("%s method %s getFaceShape" % (TOOL, T["VoxelShape"]),
    "method: VoxelShape.getFaceShape (should include javadoc)")

run("%s read net.minecraft.NonExistentClass" % TOOL,
    "read: non-existent class (should fail)", expect_failure=True)

run("%s method %s nonExistentMethod" % (TOOL, T["BlockEntity"]),
    "method: non-existent method (should fail)", expect_failure=True)

# === 2. glob: default listing ===
section("2. glob: default listing")

run("%s glob %s" % (TOOL, T["BlockEntity"]),
    "glob: BlockEntity default")

run("%s glob %s" % (TOOL, T["VoxelShape"]),
    "glob: VoxelShape default")

run("%s glob %s" % (TOOL, T["LevelRenderer"]),
    "glob: LevelRenderer default (tree-sitter, no regex fallback)")

# === 3. glob: category filters ===
section("3. glob: category filters")

run("%s glob %s --methods" % (TOOL, T["BlockEntity"]),
    "glob: --methods only")

run("%s glob %s --fields" % (TOOL, T["BlockEntity"]),
    "glob: --fields only")

run("%s glob %s --ctors" % (TOOL, T["BlockEntity"]),
    "glob: --ctors only")

run("%s glob %s --inner-types" % (TOOL, T["BlockEntity"]),
    "glob: --inner-types only")

# === 4. glob: visibility filters ===
section("4. glob: visibility filters")

run("%s glob %s --public --methods" % (TOOL, T["BlockBehaviour"]),
    "glob: BlockBehaviour --public --methods")

run("%s glob %s --public --protected --methods" % (TOOL, T["BlockBehaviour"]),
    "glob: BlockBehaviour --public --protected --methods")

run("%s glob %s --private --fields" % (TOOL, T["BlockEntity"]),
    "glob: BlockEntity --private --fields")

run("%s glob %s --package --methods" % (TOOL, T["BlockEntity"]),
    "glob: BlockEntity --package --methods")

run("%s glob %s --public --private --protected --package" % (TOOL, T["BlockEntity"]),
    "glob: BlockEntity all visibilities")

# === 5. glob: static filters ===
section("5. glob: static filters")

run("%s glob %s --fields --public --static" % (TOOL, T["LevelRenderer"]),
    "glob: LevelRenderer --static --fields")

run("%s glob %s --fields --public --no-static" % (TOOL, T["LevelRenderer"]),
    "glob: LevelRenderer --no-static --fields")

# === 6. glob: filter and lines ===
section("6. glob: filter and lines")

run("%s glob %s --methods --filter get" % (TOOL, T["BlockEntity"]),
    "glob: --filter 'get'")

run("%s glob %s --methods --filter BlockPos" % (TOOL, T["BlockEntity"]),
    "glob: --filter 'BlockPos'")

run("%s glob %s --methods --filter canOcclude" % (TOOL, T["BlockBehaviour"]),
    "glob: --filter 'canOcclude'")

run("%s glob %s --methods --filter isAir" % (TOOL, T["BlockBehaviour"]),
    "glob: --filter 'isAir'")

# === 7. glob: GameRenderer ===
section("7. glob: GameRenderer (javalang had issues)")

run("%s glob %s" % (TOOL, T["GameRenderer"]),
    "glob: GameRenderer default")

run("%s glob %s --methods --public --filter getProjection" % (TOOL, T["GameRenderer"]),
    "glob: GameRenderer --methods --filter 'getProjection'")

run("%s glob %s --fields --public" % (TOOL, T["GameRenderer"]),
    "glob: GameRenderer --fields --public")

run("%s glob %s --ctors --public" % (TOOL, T["GameRenderer"]),
    "glob: GameRenderer --ctors --public")

# === 8. glob: error cases ===
section("8. glob: error cases")

run("%s glob net.minecraft.NonExistentClass" % TOOL,
    "glob: non-existent class (should fail)", expect_failure=True)

# === Summary ===
print("\n" + "=" * 60)
print("  RESULTS: %d/%d passed" % (tests_passed, tests_run))
if tests_failed:
    print("  FAILED:")
    for f in tests_failed:
        print("    - " + f)
print("=" * 60)

sys.exit(0 if tests_passed == tests_run else 1)
