#!/usr/bin/env python3

# Original code from https://github.com/McJtyMods/addmodblock
# Heavily modified to support Kotlin

import argparse
import os

#################################################################################
# Configuration section. Modify this to suit your mod. Run this python script
# from within the root of your mod project. Directories in this configuration
# are relative to that root

# This is the string that represents a reference to your modid. You can also
# use a string here but it is usually better to have some constant somewhere
MODID_REF = 'org.eln2.MODID'

# This is the actual modid as used in json for example
MODID = 'eln2'

# The root package of your mod
ROOT_PACKAGE = 'org.eln2'

# The relative path to the mod root (where your main mod file is located)
SOURCE_ROOT = 'src/main/kotlin/org/eln2'
# The relative path to the root of your asset resources
ASSET_RESOURCE_ROOT = 'src/main/resources/assets/eln2'
# The relative path to the root of your data resources
DATA_RESOURCE_ROOT = 'src/main/resources/data/eln2'

# Package where you want to generate code. These can be the same in case you
# want to generate all in the same package but you can also separate it
PACKAGE_BLOCKS = 'blocks'
PACKAGE_TILES = 'blocks'
PACKAGE_CONTAINERS = 'blocks'
PACKAGE_SCREENS = 'blocks'

#################################################################################
# Template section. You can modify these if you want to personalize how code
# is generated. ${xxx} are input parameters. $U{xxx} will generate an uppercase
# version of the input. $L{xxx} a lowercase version. The following parameters
# are given to the templates:
#      - modid_ref (contains the value of MODID_REF above)
#      - modid (contains the value of MODID above)
#      - name (contains the name of the block to generate (same as the parameter given to this script)
#      - package (contains the package where the code will be geneated)
# Lines enclosed with ?{xxx and ?}xxx
# are conditionally generated depending on input parameters

TEMPLATE_BLOCK = '''
package ${package};

import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.material.Material
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.entity.player.ServerPlayerEntity
import net.minecraft.inventory.container.Container
import net.minecraft.inventory.container.INamedContainerProvider
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.BlockRayTraceResult
import net.minecraft.util.text.ITextComponent
import net.minecraft.util.text.TranslationTextComponent
import net.minecraft.world.IBlockReader
import net.minecraft.world.World
import net.minecraftforge.fml.network.NetworkHooks

class ${name}Block : Block(Properties.create(Material.IRON)) {

    ?{tile
    override fun hasTileEntity(BlockState state): Boolean {
        return true
    }

    override fun createTileEntity(BlockState state, IBlockReader world): TileEntity? {
        return ${name}Tile()
    }
    ?}tile

    ?{gui
    override fun onBlockActivated(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockRayTraceResult result): Boolean {
        if (!world.isRemote) {
            NetworkHooks.openGui((ServerPlayerEntity) player, INamedContainerProvider() {
                override fun getDisplayName(): ITextComponent {
                    return TranslationTextComponent("title.from.langfile") // Put your own title description here
                }

                override fun createMenu(int i, PlayerInventory playerInventory, PlayerEntity playerEntity): Container? {
                    return ${name}Container(i, world, pos, playerInventory, playerEntity)
                }
            }, pos)
            return true
        }
        return super.onBlockActivated(state, world, pos, player, hand, result)
    }
    ?}gui
}

/*
====== Code to move to OreBlocks.kt ======

val $U{name} = ${name}Block()

*/
'''

TEMPLATE_TILE = '''
package ${package}

import net.minecraft.nbt.CompoundNBT
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.Direction
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.util.INBTSerializable
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.ItemStackHandler

class ${name}Tile : TileEntity($U{name}_TILE) {

    ?{gui
    val handler: LazyOptional<IItemHandler> = LazyOptional.of(this::createHandler)

    override fun read(tag: CompoundNBT) {
        val invTag = tag.getCompound("inv")
        handler.ifPresent(h -> ((INBTSerializable<CompoundNBT>) h).deserializeNBT(invTag))
    }

    override fun write(tag: CompoundNBT): CompoundNBT {
        handler.ifPresent(h -> {
            CompoundNBT compound = ((INBTSerializable<CompoundNBT>) h).serializeNBT()
            tag.put("inv", compound)
        })
        return super.write(tag)
    }

    private fun createHandler(): IItemHandler {
        return ItemStackHandler(${name}Container.COUNT) {
            override fun onContentsChanged(slot: Int) {
                markDirty()
            }
        }
    }

    override fun getCapability<T>(cap: Capability<T>, side: Direction?): LazyOptional<T> {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {}
            return handler.cast()
        }
        return super.getCapability(cap, side)
    }
    ?}gui
}

/*
====== Code to move to your objectholder class ======

@ObjectHolder(${modid_ref}+":$L{name}")
public static TileEntityType<${name}Tile> $U{name}_TILE;

====== Code to move to your registration event class ======

@SubscribeEvent
public static void onTileRegister(final RegistryEvent.Register<TileEntityType<?>> e) {
    e.getRegistry().register(TileEntityType.Builder.create(${name}Tile::new, $U{name}BLOCK).build(null).setRegistryName("$L{name}"));
}

*/
'''

TEMPLATE_CONTAINER = '''
package ${package}

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IWorldPosCallable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.SlotItemHandler;

public class ${name}Container extends Container {

    public static final int COUNT = 1;      // Change for a different number of slots in this container

    private TileEntity tileEntity;
    private PlayerEntity playerEntity;

    public ${name}Container(int windowId, World world, BlockPos pos, PlayerInventory playerInventory, PlayerEntity player) {
        super($U{name}_CONTAINER, windowId);
        tileEntity = world.getTileEntity(pos);
        this.playerEntity = player;

        tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(h -> {
            // Add more slots here if needed
            addSlot(new SlotItemHandler(h, 0, 64, 24));
        });
        layoutPlayerInventorySlots(playerInventory, 10, 70);
    }

    @Override
    public boolean canInteractWith(PlayerEntity playerIn) {
        return isWithinUsableDistance(IWorldPosCallable.of(tileEntity.getWorld(), tileEntity.getPos()), playerEntity, $U{name});
    }

    private void layoutPlayerInventorySlots(PlayerInventory playerInventory, int leftCol, int topRow) {
        // Player inventory
        int index = 9;
        int y = topRow;
        for (int j = 0; j < 3; j++) {
            int x = leftCol;
            for (int i = 0; i < 9; i++) {
                addSlot(new Slot(playerInventory, index++, x, y));
                x += 18;
            }
            y += 18;
        }

        // Hotbar
        topRow += 58;
        index = 0;
        int x = leftCol;
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInventory, index++, x, topRow));
            x += 18;
        }
    }

    @Override
    public ItemStack transferStackInSlot(PlayerEntity playerIn, int index) {
        // @todo provide a proper implementation here depending on what you need!
        return ItemStack.EMPTY;
    }
}

/*
====== Code to move to your objectholder class ======

@ObjectHolder(${modid_ref}+":$L{name}")
public static ContainerType<${name}Container> $U{name}_CONTAINER;

====== Code to move to your registration event class ======

@SubscribeEvent
public static void onContainerRegister(final RegistryEvent.Register<ContainerType<?>> e) {
    e.getRegistry().register(IForgeContainerType.create((windowId, inv, data) -> {
        BlockPos pos = data.readBlockPos();
        World clientWorld = DistExecutor.runForDist(() -> () -> Minecraft.getInstance().world, () -> () -> null);
        PlayerEntity clientPlayer = DistExecutor.runForDist(() -> () -> Minecraft.getInstance().player, () -> () -> null);
        return new ${name}Container(windowId, clientWorld, pos, inv, clientPlayer);
    }).setRegistryName("$L{name}"));
}

*/

'''

TEMPLATE_SCREEN = '''
package ${package};

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;

public class ${name}Screen extends ContainerScreen<${name}Container> {

    private ResourceLocation GUI = new ResourceLocation(${modid_ref}, "textures/gui/gui.png");  // Put your own gui image here

    public ${name}Screen(${name}Container container, PlayerInventory inv, ITextComponent name) {
        super(container, inv, name);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.renderBackground();
        super.render(mouseX, mouseY, partialTicks);
        this.renderHoveredToolTip(mouseX, mouseY);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        // Draw whatever extra information you want here
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GlStateManager.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        this.minecraft.getTextureManager().bindTexture(GUI);
        int relX = (this.width - this.xSize) / 2;
        int relY = (this.height - this.ySize) / 2;
        this.blit(relX, relY, 0, 0, this.xSize, this.ySize);
    }
}

/*
====== Code to move to your client initialization ======

        ScreenManager.registerFactory($U{name}_CONTAINER, ${name}Screen::new);

*/
'''

TEMPLATE_BLOCKSTATE_JSON = '''
{
    "variants": {
        "": { "model": "${modid}:block/$L{name}" }
    }
}
'''

TEMPLATE_BLOCKMODEL_JSON = '''
{
    "parent": "block/cube_all",
    "textures": {
        "all": "${modid}:block/$L{name}"
    }
}
'''

TEMPLATE_ITEMMODEL_JSON = '''
{
  "parent": "${modid}:block/$L{name}"
}
'''

TEMPLATE_LOOTTABLE_JSON = '''
{
  "type": "minecraft:block",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        {
          "type": "minecraft:item",
          "name": "${modid}:$L{name}"
        }
      ],
      "conditions": [
        {
          "condition": "minecraft:survives_explosion"
        }
      ]
    }
  ]
}
'''

TEMPLATE_RECIPE_JSON = '''
{
  "type": "minecraft:crafting_shaped",
  "pattern": [
    "ccc",
    "ccc",
    "ccc"
  ],
  "key": {
    "c": {
      "item": "minecraft:clay"
    }
  },
  "result": {
    "item": "${modid}:$L{name}"
  }
}'''


#################################################################################

def generate(template, inputs, conditionals):
    for cond_name, conditional in conditionals.items():
        lines = template.splitlines()
        gen = True
        newlines = []
        for line in lines:
            if line.strip() == '?{' + cond_name:
                gen = conditional
            elif line.strip() == '?}' + cond_name:
                gen = True
            elif gen:
                newlines.append(line)

        template = '\n'.join(newlines)

    for inp, val in inputs.items():
        template = template.replace('${' + inp + '}', val)
        template = template.replace('$U{' + inp + '}', val.upper())
        template = template.replace('$L{' + inp + '}', val.lower())

    return template.strip()


def add_templated_code(package, name, suffix, force, conditionals, template,
                       filetype=None):
    if filetype is None:
        raise ValueError('Filetype must be set')

    path = SOURCE_ROOT
    for p in package.split('.'):
        path = os.path.join(path, p)

    os.makedirs(path, exist_ok=True)
    filename = f'{name}{suffix}.{filetype}'
    path = os.path.join(path, filename)

    if (not force) and os.path.exists(path):
        print(f'File {filename!r} already exists. Not generated')
    else:
        print(f'Generated {filename!r}')
        f = open(path, 'w')

        f.write(
            generate(
                template,
                {
                    'package': f'{ROOT_PACKAGE}.{package}',
                    'modid_ref': MODID_REF,
                    'modid': MODID,
                    'name': name
                },
                conditionals
            )
        )

        f.close()


def add_templated_java(*args):
    return add_templated_code(*args, filetype='java')


def add_templated_kt(*args):
    return add_templated_code(*args, filetype='kt')


def add_templated_json(path, package, name, force, conditionals, template):
    for p in package.split('.'):
        path = os.path.join(path, p)
    os.makedirs(path, exist_ok=True)
    json_name = name.lower() + '.json'
    path = os.path.join(path, json_name)

    if (not force) and os.path.exists(path):
        print(f'File {json_name!r} already exists. Not generated')
    else:
        print(f'Generated {json_name!r}')
        f = open(path, 'w')

        f.write(
            generate(
                template,
                {
                    'package': f"{ROOT_PACKAGE}.{package}",
                    'modid_ref': MODID_REF,
                    'modid': MODID,
                    'name': name
                },
                conditionals
            )
        )

        f.close()


def add_block(name, force, gui, tile, no_json):
    conditionals = {'gui': gui, 'tile': gui or tile}
    add_templated_kt(PACKAGE_BLOCKS, name, 'Block', force, conditionals,
                     TEMPLATE_BLOCK)
    if gui or tile:
        add_templated_kt(PACKAGE_TILES, name, 'Tile', force, conditionals,
                         TEMPLATE_TILE)
    if gui:
        add_templated_java(PACKAGE_CONTAINERS, name, 'Container', force,
                           conditionals, TEMPLATE_CONTAINER)
        add_templated_java(PACKAGE_SCREENS, name, 'Screen', force, conditionals,
                           TEMPLATE_SCREEN)
    if not no_json:
        add_templated_json(ASSET_RESOURCE_ROOT, 'blockstates', name, force,
                           conditionals, TEMPLATE_BLOCKSTATE_JSON)
        add_templated_json(ASSET_RESOURCE_ROOT, 'models.block', name, force,
                           conditionals, TEMPLATE_BLOCKMODEL_JSON)
        add_templated_json(ASSET_RESOURCE_ROOT, 'models.item', name, force,
                           conditionals, TEMPLATE_ITEMMODEL_JSON)
        add_templated_json(DATA_RESOURCE_ROOT, 'loot_tables.blocks', name,
                           force, conditionals, TEMPLATE_LOOTTABLE_JSON)
        add_templated_json(DATA_RESOURCE_ROOT, 'recipes', name, force,
                           conditionals, TEMPLATE_RECIPE_JSON)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description='Make a block')
    parser.add_argument('name', help='CamelCase name of the block to add')
    parser.add_argument('--force',
                        help='Overwrite files even if they exist (be careful!)',
                        action='store_true')
    parser.add_argument('--tile',
                        help='Generate additional code for a tileentity',
                        action='store_true')
    parser.add_argument('--gui',
                        help='Generate additional code for container and gui (implies tile!)',
                        action='store_true')
    parser.add_argument('--nojson', help='Prevent generating json',
                        action='store_true')
    args = parser.parse_args()

    print(f'Adding block {args.name}')
    add_block(args.name, args.force, args.gui, args.tile, args.nojson)
