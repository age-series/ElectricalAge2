package cam72cam.mod.item;

public class Recipes {
    public static void register(ItemBase item, int width, Fuzzy... ingredients) {
        register(new ItemStack(item, 1), width, ingredients);
    }

    public static void register(ItemStack result, int width, Fuzzy... ingredients) {
        /*
        CommonEvents.Recipe.REGISTER.subscribe(() -> {
            CraftingHelper.ShapedPrimer primer = new CraftingHelper.ShapedPrimer();
            primer.width = width;
            primer.height = ingredients.length / width;
            primer.mirrored = false;
            primer.input = NonNullList.withSize(primer.width * primer.height, Ingredient.EMPTY);

            for (int i = 0; i < ingredients.length; i++) {
                if (ingredients[i] != null) {
                    primer.input.set(i, new OreIngredient(ingredients[i].toString()));
                }
            }
            ShapedOreRecipe sor = new ShapedOreRecipe(new ResourceLocation(ModCore.MODID, "recipes"), result.internal, primer);
            sor.setRegistryName(result.internal.getItem().getRegistryName());
            ForgeRegistries.RECIPES.register(sor);
        });
        */
    }
}