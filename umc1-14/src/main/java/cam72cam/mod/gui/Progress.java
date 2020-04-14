package cam72cam.mod.gui;

import net.minecraftforge.fml.StartupMessageManager;

public class Progress {
    public static Bar push(String name, int steps) {
        return new Bar(name, steps);
    }

    public static void pop(Bar bar) {
    }

    public static void pop() {
    }

    public static class Bar {
        private final String name;
        private final int steps;
        private int at;

        public Bar(String name, int steps) {
            this.name = name;
            this.steps = steps;
            this.at = 0;
            StartupMessageManager.addModMessage(name + " 0%");
        }

        public void step(String name) {
            at += 1;
            StartupMessageManager.addModMessage(this.name + " " + (at*100/steps) + "% : " + name);
        }
    }
}
