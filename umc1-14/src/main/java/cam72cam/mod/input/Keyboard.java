package cam72cam.mod.input;

import cam72cam.mod.entity.Player;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.net.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class Keyboard {
    private static Map<UUID, Vec3d> vecs = new HashMap<>();

    public static Vec3d getMovement(Player player) {
        return vecs.getOrDefault(player.getUUID(), Vec3d.ZERO);
    }

    public enum KeyCode {
        ESCAPE(GLFW.GLFW_KEY_ESCAPE),
        NUM1(GLFW.GLFW_KEY_1),
        NUM2(GLFW.GLFW_KEY_2),
        NUM3(GLFW.GLFW_KEY_3),
        NUM4(GLFW.GLFW_KEY_4),
        NUM5(GLFW.GLFW_KEY_5),
        NUM6(GLFW.GLFW_KEY_6),
        NUM7(GLFW.GLFW_KEY_7),
        NUM8(GLFW.GLFW_KEY_8),
        NUM9(GLFW.GLFW_KEY_9),
        NUM0(GLFW.GLFW_KEY_0),
        MINUS(GLFW.GLFW_KEY_MINUS),
        EQUALS(GLFW.GLFW_KEY_EQUAL),
        BACK(GLFW.GLFW_KEY_BACKSPACE),
        TAB(GLFW.GLFW_KEY_TAB),
        Q(GLFW.GLFW_KEY_Q),
        W(GLFW.GLFW_KEY_W),
        E(GLFW.GLFW_KEY_E),
        R(GLFW.GLFW_KEY_R),
        T(GLFW.GLFW_KEY_T),
        Y(GLFW.GLFW_KEY_Y),
        U(GLFW.GLFW_KEY_U),
        I(GLFW.GLFW_KEY_I),
        O(GLFW.GLFW_KEY_O),
        P(GLFW.GLFW_KEY_P),
        LBRACKET(GLFW.GLFW_KEY_LEFT_BRACKET),
        RBRACKET(GLFW.GLFW_KEY_RIGHT_BRACKET),
        RETURN(GLFW.GLFW_KEY_ENTER),
        LCONTROL(GLFW.GLFW_KEY_LEFT_CONTROL),
        A(GLFW.GLFW_KEY_A),
        S(GLFW.GLFW_KEY_S),
        D(GLFW.GLFW_KEY_D),
        F(GLFW.GLFW_KEY_F),
        G(GLFW.GLFW_KEY_G),
        H(GLFW.GLFW_KEY_H),
        J(GLFW.GLFW_KEY_J),
        K(GLFW.GLFW_KEY_K),
        L(GLFW.GLFW_KEY_L),
        SEMICOLON(GLFW.GLFW_KEY_SEMICOLON),
        APOSTROPHE(GLFW.GLFW_KEY_APOSTROPHE),
        GRAVE(GLFW.GLFW_KEY_GRAVE_ACCENT),
        LSHIFT(GLFW.GLFW_KEY_LEFT_SHIFT),
        BACKSLASH(GLFW.GLFW_KEY_BACKSLASH),
        Z(GLFW.GLFW_KEY_Z),
        X(GLFW.GLFW_KEY_X),
        C(GLFW.GLFW_KEY_C),
        V(GLFW.GLFW_KEY_V),
        B(GLFW.GLFW_KEY_B),
        N(GLFW.GLFW_KEY_N),
        M(GLFW.GLFW_KEY_M),
        COMMA(GLFW.GLFW_KEY_COMMA),
        PERIOD(GLFW.GLFW_KEY_PERIOD),
        SLASH(GLFW.GLFW_KEY_SLASH),
        RSHIFT(GLFW.GLFW_KEY_RIGHT_SHIFT),
        MULTIPLY(GLFW.GLFW_KEY_KP_MULTIPLY),
        LMENU(GLFW.GLFW_KEY_MENU),
        SPACE(GLFW.GLFW_KEY_SPACE),
        CAPITAL(GLFW.GLFW_KEY_CAPS_LOCK),
        F1(GLFW.GLFW_KEY_F1),
        F2(GLFW.GLFW_KEY_F2),
        F3(GLFW.GLFW_KEY_F3),
        F4(GLFW.GLFW_KEY_F4),
        F5(GLFW.GLFW_KEY_F5),
        F6(GLFW.GLFW_KEY_F6),
        F7(GLFW.GLFW_KEY_F7),
        F8(GLFW.GLFW_KEY_F8),
        F9(GLFW.GLFW_KEY_F9),
        F10(GLFW.GLFW_KEY_F10),
        NUMLOCK(GLFW.GLFW_KEY_NUM_LOCK),
        SCROLL(GLFW.GLFW_KEY_SCROLL_LOCK),
        NUMPAD7(GLFW.GLFW_KEY_KP_7),
        NUMPAD8(GLFW.GLFW_KEY_KP_8),
        NUMPAD9(GLFW.GLFW_KEY_KP_9),
        SUBTRACT(GLFW.GLFW_KEY_KP_SUBTRACT),
        NUMPAD4(GLFW.GLFW_KEY_KP_4),
        NUMPAD5(GLFW.GLFW_KEY_KP_5),
        NUMPAD6(GLFW.GLFW_KEY_KP_6),
        ADD(GLFW.GLFW_KEY_KP_ADD),
        NUMPAD1(GLFW.GLFW_KEY_KP_1),
        NUMPAD2(GLFW.GLFW_KEY_KP_2),
        NUMPAD3(GLFW.GLFW_KEY_KP_3),
        NUMPAD0(GLFW.GLFW_KEY_KP_0),
        DECIMAL(GLFW.GLFW_KEY_KP_DECIMAL),
        F11(GLFW.GLFW_KEY_F11),
        F12(GLFW.GLFW_KEY_F12),
        F13(GLFW.GLFW_KEY_F13),
        F14(GLFW.GLFW_KEY_F14),
        F15(GLFW.GLFW_KEY_F15),
        F16(GLFW.GLFW_KEY_F16),
        F17(GLFW.GLFW_KEY_F17),
        F18(GLFW.GLFW_KEY_F18),
        F19(GLFW.GLFW_KEY_F19),
        NUMPADEQUALS(GLFW.GLFW_KEY_KP_EQUAL),
        COLON(GLFW.GLFW_KEY_SEMICOLON),
        NUMPADENTER(GLFW.GLFW_KEY_KP_ENTER),
        RCONTROL(GLFW.GLFW_KEY_RIGHT_CONTROL),
        DIVIDE(GLFW.GLFW_KEY_KP_DIVIDE),
        PAUSE(GLFW.GLFW_KEY_PAUSE),
        HOME(GLFW.GLFW_KEY_HOME),
        UP(GLFW.GLFW_KEY_UP),
        LEFT(GLFW.GLFW_KEY_LEFT),
        RIGHT(GLFW.GLFW_KEY_RIGHT),
        END(GLFW.GLFW_KEY_END),
        DOWN(GLFW.GLFW_KEY_DOWN),
        INSERT(GLFW.GLFW_KEY_INSERT),
        DELETE(GLFW.GLFW_KEY_DELETE),
        LMETA(GLFW.GLFW_KEY_LEFT_SUPER),
        RMETA(GLFW.GLFW_KEY_RIGHT_SUPER);

        private final int code;

        KeyCode(int code) {
            this.code = code;
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerKey(String name, KeyCode keyCode, String category, Runnable handler) {
        KeyBinding key = new KeyBinding(name, keyCode.code, category);
        ClientRegistry.registerKeyBinding(key);
        ClientEvents.TICK.subscribe(() -> {
            if (key.isKeyDown()) {
                handler.run();
            }
        });
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerClientEvents() {
        ClientEvents.TICK.subscribe(() -> {
            ClientPlayerEntity player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            new MovementPacket(
                    player.getUniqueID(),
                    new Vec3d(player.moveStrafing, 0, player.moveForward).scale(player.isSprinting() ? 0.4 : 0.2)
            ).sendToServer();
        });
    }

    public static class MovementPacket extends Packet {
        public MovementPacket() {

        }

        public MovementPacket(UUID id, Vec3d move) {
            data.setUUID("id", id);
            data.setVec3d("move", move);
            vecs.put(data.getUUID("id"), data.getVec3d("move"));
        }

        @Override
        protected void handle() {
            vecs.put(data.getUUID("id"), data.getVec3d("move"));
        }
    }
}
