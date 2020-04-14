package cam72cam.mod.item;

import net.minecraft.inventory.EquipmentSlotType;

public enum ArmorSlot {
    MAINHAND(EquipmentSlotType.MAINHAND),
    OFFHAND(EquipmentSlotType.OFFHAND),
    FEET(EquipmentSlotType.FEET),
    LEGS(EquipmentSlotType.LEGS),
    CHEST(EquipmentSlotType.CHEST),
    HEAD(EquipmentSlotType.HEAD);
    public final EquipmentSlotType internal;

    ArmorSlot(EquipmentSlotType slot) {
        this.internal = slot;
    }

    public static ArmorSlot from(EquipmentSlotType armorType) {
        switch (armorType) {
            case MAINHAND:
                return MAINHAND;
            case OFFHAND:
                return OFFHAND;
            case FEET:
                return FEET;
            case LEGS:
                return LEGS;
            case CHEST:
                return CHEST;
            case HEAD:
                return HEAD;
            default:
                return null;
        }
    }
}
