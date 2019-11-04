package cam72cam.mod.render;

import cam72cam.mod.event.ClientEvents;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.IntBuffer;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class GLTexture {
    private static LinkedBlockingQueue queue = new LinkedBlockingQueue<>(1);
    private static ExecutorService saveImage = new ThreadPoolExecutor(5, 5, 60, TimeUnit.SECONDS, queue);
    private static ExecutorService readImage = Executors.newFixedThreadPool(1);
    private static Map<String, GLTexture> textures = new HashMap<>();
    private final File texLoc;
    private final int cacheSeconds;
    private final int width;
    private final int height;
    private int glTexID;
    private long lastUsed;
    private IntBuffer pixels;
    private TextureState state;
    private RuntimeException internalError;

    private enum TextureState {
        NEW,
        WRITING,
        READING,
        READ,
        ALLOCATED,
        UNALLOCATED,
        ERROR
    }

    static {
        ClientEvents.TICK.subscribe(() -> {
            for (GLTexture texture : textures.values()) {
                if (texture.state == TextureState.ALLOCATED && System.currentTimeMillis() - texture.lastUsed > texture.cacheSeconds * 1000) {
                    texture.dealloc();
                }
            }
        });
    }

    public GLTexture(String name, BufferedImage image, int cacheSeconds, boolean upload) {

        File cacheDir = Paths.get(Loader.instance().getConfigDir().getParentFile().getPath(), "cache", "universalmodcore").toFile();
        cacheDir.mkdirs();

        this.texLoc = new File(cacheDir, name);
        this.cacheSeconds = cacheSeconds;
        this.width = image.getWidth();
        this.height = image.getHeight();

        transition(TextureState.NEW);


        transition(TextureState.WRITING);
        if (upload) {
            try {
                ImageIO.write(image, "png", texLoc);
            } catch (IOException e) {
                internalError = new RuntimeException(e);
                transition(TextureState.ERROR);
                throw internalError;
            }
            transition(TextureState.UNALLOCATED);

            this.pixels = imageToPixels(image);
            transition(TextureState.READ);
            tryUpload();
        } else {
            while (queue.size() != 0) {
                try {
                    Thread.sleep(1000);
                    System.out.println("Waiting for free write slot...");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            saveImage.submit(() -> {
                try {
                    ImageIO.write(image, "png", texLoc);
                    transition(TextureState.UNALLOCATED);
                } catch (IOException e) {
                    internalError = new RuntimeException("Unable to save image " + texLoc, e);
                    transition(TextureState.ERROR);
                    throw internalError;
                }
            });
        }

        textures.put(texLoc.toString(), this);
    }

    private void transition(TextureState state) {
        this.state = state;
        //System.out.println(state.name() + " " + texLoc);
    }

    private IntBuffer imageToPixels(BufferedImage image) {
        // Will dump out inside a loading thread if prematurely free'd
        assert state == TextureState.READ;

        int[] pixels = new int[image.getWidth() * image.getHeight()];
        image.getRGB(0, 0, image.getWidth(), image.getHeight(), pixels, 0, image.getWidth());
        IntBuffer buffer = BufferUtils.createByteBuffer(image.getWidth() * image.getHeight() * 4).asIntBuffer();
        buffer.put(pixels);
        buffer.flip();
        return buffer;
    }

    private int uploadTexture() {
        this.lastUsed = System.currentTimeMillis();
        int textureID = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureID);
        TextureUtil.allocateTexture(textureID, width, height);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, width, height, GL12.GL_BGRA, GL11.GL_UNSIGNED_BYTE, pixels);
        pixels = null;
        transition(TextureState.ALLOCATED);
        return textureID;
    }

    public boolean isLoaded() {
        return state == TextureState.ALLOCATED;
    }

    public boolean tryUpload() {
        switch (this.state) {
            case NEW:
            case WRITING:
            case READING:
                return false;
            case READ:
                this.glTexID = uploadTexture();
                return true;
            case ALLOCATED:
                return true;
            case UNALLOCATED:
                transition(TextureState.READING);
                readImage.submit(() -> {
                    try {
                        this.pixels = imageToPixels(ImageIO.read(texLoc));
                        transition(TextureState.READ);
                    } catch (Exception e) {
                        transition(TextureState.ERROR);
                        internalError = new RuntimeException(texLoc.toString(), e);
                        throw internalError;
                    }
                });
                return false;
            case ERROR:
                throw internalError;
        }

        throw new RuntimeException(this.state.toString());
    }

    public int bind(boolean force) {
        lastUsed = System.currentTimeMillis();
        int currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        if (force) {
            // Wait up to 1 second for texture to load
            // Should be fine for the icons we use this with
            for (int i = 0; i < 100; i++) {
                if (tryUpload()) {
                    break;
                }
                try {
                    Thread.sleep((long) 10);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        if (!tryUpload()) {
            return -1;
        }
        if (glTexID == currentTexture) {
            return -1; //NOP
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, glTexID);
        return currentTexture;
    }

    public void freeGL() {
        textures.remove(this.texLoc.toString());

        switch (state) {
            case ALLOCATED:
                dealloc();
            default:
                transition(TextureState.UNALLOCATED);
        }
    }

    public void dealloc() {
        if (this.state == TextureState.ALLOCATED) {
            GL11.glDeleteTextures(this.glTexID);
            transition(TextureState.UNALLOCATED);
        }
    }

    public String info() {
        return this.texLoc.toString();
    }
}
