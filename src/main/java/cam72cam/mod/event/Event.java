package cam72cam.mod.event;

import java.util.LinkedHashSet;
import java.util.Set;

public class Event<T> {
    Set<T> callbacks = new LinkedHashSet<>();
    public void register(T callback) {
        callbacks.add(callback);
    }
}
