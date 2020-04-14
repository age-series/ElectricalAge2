package cam72cam.mod.fluid;

import cam72cam.mod.item.ItemStack;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;

import java.util.function.Consumer;

public interface ITank {
    static ITank getTank(ItemStack inputCopy, Consumer<ItemStack> onUpdate) {
        LazyOptional<IFluidHandlerItem> handler = FluidUtil.getFluidHandler(inputCopy.internal);
        if (!handler.isPresent()) {
            return null;
        }
        IFluidHandlerItem internal = handler.orElse(null);
        return new ITank() {
            @Override
            public FluidStack getContents() {
                return new FluidStack(internal.getFluidInTank(0));
            }

            @Override
            public int getCapacity() {
                return internal.getTankCapacity(0);
            }

            @Override
            public boolean allows(Fluid fluid) {
                return internal.isFluidValid(0, new net.minecraftforge.fluids.FluidStack(fluid.internal, 1));
            }

            @Override
            public int fill(FluidStack fluidStack, boolean simulate) {
                IFluidHandlerItem temp = FluidUtil.getFluidHandler(inputCopy.copy().internal).orElse(null);
                temp.fill(fluidStack.internal, IFluidHandler.FluidAction.EXECUTE);
                onUpdate.accept(new ItemStack(temp.getContainer()));

                return internal.fill(fluidStack.internal, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            }

            @Override
            public FluidStack drain(FluidStack fluidStack, boolean simulate) {
                IFluidHandlerItem temp = FluidUtil.getFluidHandler(inputCopy.copy().internal).orElse(null);
                temp.drain(fluidStack.internal, IFluidHandler.FluidAction.EXECUTE);
                onUpdate.accept(new ItemStack(temp.getContainer()));

                return new FluidStack(internal.drain(fluidStack.internal, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE));
            }
        };
    }

    static ITank getTank(IFluidHandler internal) {
        return new ITank() {
            @Override
            public FluidStack getContents() {
                return new FluidStack(internal.getFluidInTank(0));
            }

            @Override
            public int getCapacity() {
                return internal.getTankCapacity(0);
            }

            @Override
            public boolean allows(Fluid fluid) {
                return internal.isFluidValid(0, new net.minecraftforge.fluids.FluidStack(fluid.internal, 1));
            }

            @Override
            public int fill(FluidStack fluidStack, boolean simulate) {
                return internal.fill(fluidStack.internal, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
            }

            @Override
            public FluidStack drain(FluidStack fluidStack, boolean simulate) {
                return new FluidStack(internal.drain(fluidStack.internal, simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE));
            }
        };
    }

    FluidStack getContents();

    int getCapacity();

    boolean allows(Fluid fluid);

    int fill(FluidStack fluidStack, boolean simulate);

    FluidStack drain(FluidStack fluidStack, boolean simulate);

}
