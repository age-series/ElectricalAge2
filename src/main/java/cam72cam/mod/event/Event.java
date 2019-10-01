package cam72cam.mod.event;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

public class Event<T> {
    private Set<T> callbacks = new LinkedHashSet<>();
    public void subscribe(T callback) {
        callbacks.add(callback);
    }
    void execute(Consumer<T> handler) {
        callbacks.forEach(handler);
    }

    boolean executeCancellable(Function<T, Boolean> handler) {
        for (T callback : callbacks) {
            if (!handler.apply(callback)) {
                return false;
            }
        }
        return true;
    }
}
