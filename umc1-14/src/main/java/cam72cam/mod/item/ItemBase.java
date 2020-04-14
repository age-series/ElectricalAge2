package cam72cam.mod.item;

import cam72cam.mod.ModCore;
import cam72cam.mod.entity.Entity;
import cam72cam.mod.entity.Player;
import cam72cam.mod.event.CommonEvents;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.resource.Identifier;
import cam72cam.mod.text.TextUtil;
import cam72cam.mod.util.Facing;
import cam72cam.mod.util.Hand;
import cam72cam.mod.world.World;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemUseContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ActionResultType;
import net.minecraft.util.NonNullList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ItemBase {
    public final Item internal;
    private final CreativeTab[] creativeTabs;
    private final ResourceLocation identifier;

    public ItemBase(String modID, String name, int stackSize, CreativeTab... tabs) {
        identifier = new ResourceLocation(modID, name);

        internal = new ItemInternal(new Item.Properties().maxStackSize(stackSize).group(tabs[0].internal)); // .setTEISR()
        internal.setRegistryName(identifier);
        this.creativeTabs = tabs;

        CommonEvents.Item.REGISTER.subscribe(() -> ForgeRegistries.ITEMS.register(internal));
    }

    public List<ItemStack> getItemVariants(CreativeTab creativeTab) {
        List<ItemStack> res = new ArrayList<>();
        if (creativeTab == null || creativeTab.internal == internal.getGroup()) {
            res.add(new ItemStack(internal, 1));
        }
        return res;
    }

    /* Overrides */

    public List<String> getTooltip(ItemStack itemStack) {
        return Collections.emptyList();
    }

    public ClickResult onClickBlock(Player player, World world, Vec3i vec3i, Hand from, Facing from1, Vec3d vec3d) {
        return ClickResult.PASS;
    }

    public void onClickAir(Player player, World world, Hand hand) {

    }

    public boolean isValidArmor(ItemStack itemStack, ArmorSlot from, Entity entity) {
        return false;
    }

    public String getCustomName(ItemStack stack) {
        return null;
    }

    /* Name Hacks */

    protected final void applyCustomName(ItemStack stack) {
    }

    public Identifier getRegistryName() {
        return new Identifier(internal.getRegistryName());
    }

    private class ItemInternal extends Item {
        public ItemInternal(Properties p_i48487_1_) {
            super(p_i48487_1_);
        }

        @Override
        public void fillItemGroup(ItemGroup tab, NonNullList<net.minecraft.item.ItemStack> items) {
            CreativeTab myTab = tab != ItemGroup.SEARCH ? new CreativeTab(tab) : null;
            if (ModCore.hasResources) {
                items.addAll(getItemVariants(myTab).stream().map((ItemStack stack) -> stack.internal).collect(Collectors.toList()));
            }
        }

        @Override
        public String getTranslationKey(net.minecraft.item.ItemStack stack) {
            String cn = getCustomName(new ItemStack(stack));
            if (cn != null) {
                return cn;
            }
            return "item." + identifier + ".name";
        }

        @Override
        @OnlyIn(Dist.CLIENT)
        public final void addInformation(net.minecraft.item.ItemStack stack, @Nullable net.minecraft.world.World worldIn, List<ITextComponent> tooltip, ITooltipFlag flagIn) {
            super.addInformation(stack, worldIn, tooltip, flagIn);
            if (ModCore.hasResources) {
                applyCustomName(new ItemStack(stack));
                tooltip.addAll(ItemBase.this.getTooltip(new ItemStack(stack)).stream().map(StringTextComponent::new).collect(Collectors.toList()));
            }
        }

        public ITextComponent getDisplayName(net.minecraft.item.ItemStack stack) {
            return new StringTextComponent(TextUtil.translate(getTranslationKey(stack)));
        }

        @Override
        public ActionResultType onItemUse(ItemUseContext context) {
            return ItemBase.this.onClickBlock(new Player(context.getPlayer()), World.get(context.getWorld()), new Vec3i(context.getPos()), Hand.from(context.getHand()), Facing.from(context.getFace()), new Vec3d(context.getHitVec())).internal;
        }

        @Override
        public ActionResult<net.minecraft.item.ItemStack> onItemRightClick(net.minecraft.world.World world, PlayerEntity player, net.minecraft.util.Hand hand) {
            onClickAir(new Player(player), World.get(world), Hand.from(hand));
            return super.onItemRightClick(world, player, hand);
        }
    }
}
