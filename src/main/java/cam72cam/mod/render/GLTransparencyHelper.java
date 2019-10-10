package cam72cam.mod.render;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GLContext;

public class GLTransparencyHelper {
    private final GLBoolTracker blend;

    public GLTransparencyHelper(float r, float g, float b, float a) {
        blend = new GLBoolTracker(GL11.GL_BLEND, true);

        GL11.glBlendFunc(GL11.GL_CONSTANT_ALPHA, GL11.GL_ONE);
        if (GLContext.getCapabilities().OpenGL14) {
            GL14.glBlendColor(r,g,b,a);
        }
    }

    public void restore() {
        if (GLContext.getCapabilities().OpenGL14) {
            GL14.glBlendColor(1, 1, 1, 1f);
        }
        blend.restore();
    }
}
