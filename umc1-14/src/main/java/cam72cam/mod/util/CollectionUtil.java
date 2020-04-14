package cam72cam.mod.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CollectionUtil {
    public static <T> List<T> listOf(T ... elements) {
       return new ArrayList<>(Arrays.asList(elements));
    }
}
