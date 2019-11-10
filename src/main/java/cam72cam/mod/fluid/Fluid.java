package cam72cam.mod.fluid;

import cam72cam.mod.resource.Identifier;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;

public class Fluid {
    public static final int BUCKET_VOLUME = 1000;
    private static Map<String, Fluid> registryCache = new HashMap<>();
    public static final Fluid WATER = getFluid("water");
    public static final Fluid LAVA = getFluid("lava");
    public final String ident;
    public final net.minecraft.fluid.Fluid internal;


    private Fluid(String ident, net.minecraft.fluid.Fluid fluid) {
        this.ident = ident;
        this.internal = fluid;
    }

    public static Fluid getFluid(String type) {
        type = new Identifier(type).toString();
        if (!registryCache.containsKey(type)) {
            net.minecraft.fluid.Fluid fluid = ForgeRegistries.FLUIDS.getValue(new ResourceLocation(type));
            if (fluid == null) {
                return null;
            }
            registryCache.put(type, new Fluid(type, fluid));
        }
        return registryCache.get(type);
    }

    public static Fluid getFluid(net.minecraft.fluid.Fluid fluid) {
        return getFluid(fluid.getRegistryName().toString());
    }

    public int getDensity() {
        return internal.getAttributes().getDensity();
    }

    public String toString() {
        return ident + " : " + internal.toString() + " : " + super.toString();
    }
}
