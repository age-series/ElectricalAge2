package cam72cam.mod.entity.custom;

public interface ITickable {
    ITickable NOP = () -> {

    };

    static ITickable get(Object o) {
        if (o instanceof ITickable) {
            return (ITickable) o;
        }
        return NOP;
    }

    void onTick();
}
