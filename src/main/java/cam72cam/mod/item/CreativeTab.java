package cam72cam.mod.item;

import cam72cam.mod.text.TextUtil;
import net.minecraft.item.ItemGroup;

import java.util.function.Supplier;

public class CreativeTab {
    public ItemGroup internal;

    public CreativeTab(String label, Supplier<ItemStack> stack) {
        internal = new ItemGroup(label) {
            @Override
            public net.minecraft.item.ItemStack createIcon(){
                return stack.get().internal;
            }
            @Override
            public String getTranslationKey() {
                return TextUtil.translate(super.getTranslationKey());
            }
        };
    }

    public CreativeTab(ItemGroup tab) {
        this.internal = tab;
    }

    public boolean equals(CreativeTab tab) {
        return tab.internal == this.internal;
    }
}
