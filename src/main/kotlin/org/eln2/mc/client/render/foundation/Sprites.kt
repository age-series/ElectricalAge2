package org.eln2.mc.client.render.foundation

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.InventoryMenu
import net.minecraftforge.client.event.TextureStitchEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.ageseries.libage.data.MutableSetMapMultiMap
import org.eln2.mc.resource

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
object Sprites {
    private val spritesByAtlas = MutableSetMapMultiMap<ResourceLocation, ResourceLocation>()
    private val registeredAtlases = HashSet<ResourceLocation>()

    @SubscribeEvent @JvmStatic
    fun onTextureAtlasStitchPre(event: TextureStitchEvent.Pre) {
        if(registeredAtlases.add(event.atlas.location())) {
            spritesByAtlas[event.atlas.location()].forEach {
                event.addSprite(it)
            }
        }
    }

    fun register(atlas: ResourceLocation, texture: ResourceLocation) : Lazy<TextureAtlasSprite> {
        require(!registeredAtlases.contains(atlas)) { "Registered $atlas $texture too late!" }

        val set = spritesByAtlas[atlas]
        require(set.add(texture)) { "Duplicate add $atlas $texture" }

        return lazy {
            Minecraft.getInstance()
                .modelManager
                .getAtlas(atlas)
                .getSprite(texture)
        }
    }

    fun registerInBlocks(texture: ResourceLocation) = register(InventoryMenu.BLOCK_ATLAS, texture)

    fun registerInBlocks(texture: String) = registerInBlocks(resource(texture))

    val NEUTRAL_CABLE = registerInBlocks("cable/neutral_cable")
    val COPPER_CABLE = registerInBlocks("cable/copper_cable")
}
