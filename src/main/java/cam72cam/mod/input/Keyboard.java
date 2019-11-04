package cam72cam.mod.input;

import cam72cam.mod.entity.Player;
import cam72cam.mod.event.ClientEvents;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.net.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.*;

public class Keyboard {
    private static Map<UUID, Vec3d> vecs = new HashMap<>();

    public static Vec3d getMovement(Player player) {
        return vecs.getOrDefault(player.getUUID(), Vec3d.ZERO);
    }

    public enum KeyCode {
        ESCAPE(org.lwjgl.input.Keyboard.KEY_ESCAPE),
        NUM1(org.lwjgl.input.Keyboard.KEY_1),
        NUM2(org.lwjgl.input.Keyboard.KEY_2),
        NUM3(org.lwjgl.input.Keyboard.KEY_3),
        NUM4(org.lwjgl.input.Keyboard.KEY_4),
        NUM5(org.lwjgl.input.Keyboard.KEY_5),
        NUM6(org.lwjgl.input.Keyboard.KEY_6),
        NUM7(org.lwjgl.input.Keyboard.KEY_7),
        NUM8(org.lwjgl.input.Keyboard.KEY_8),
        NUM9(org.lwjgl.input.Keyboard.KEY_9),
        NUM0(org.lwjgl.input.Keyboard.KEY_0),
        MINUS(org.lwjgl.input.Keyboard.KEY_MINUS),
        EQUALS(org.lwjgl.input.Keyboard.KEY_EQUALS),
        BACK(org.lwjgl.input.Keyboard.KEY_BACK),
        TAB(org.lwjgl.input.Keyboard.KEY_TAB),
        Q(org.lwjgl.input.Keyboard.KEY_Q),
        W(org.lwjgl.input.Keyboard.KEY_W),
        E(org.lwjgl.input.Keyboard.KEY_E),
        R(org.lwjgl.input.Keyboard.KEY_R),
        T(org.lwjgl.input.Keyboard.KEY_T),
        Y(org.lwjgl.input.Keyboard.KEY_Y),
        U(org.lwjgl.input.Keyboard.KEY_U),
        I(org.lwjgl.input.Keyboard.KEY_I),
        O(org.lwjgl.input.Keyboard.KEY_O),
        P(org.lwjgl.input.Keyboard.KEY_P),
        LBRACKET(org.lwjgl.input.Keyboard.KEY_LBRACKET),
        RBRACKET(org.lwjgl.input.Keyboard.KEY_RBRACKET),
        RETURN(org.lwjgl.input.Keyboard.KEY_RETURN),
        LCONTROL(org.lwjgl.input.Keyboard.KEY_LCONTROL),
        A(org.lwjgl.input.Keyboard.KEY_A),
        S(org.lwjgl.input.Keyboard.KEY_S),
        D(org.lwjgl.input.Keyboard.KEY_D),
        F(org.lwjgl.input.Keyboard.KEY_F),
        G(org.lwjgl.input.Keyboard.KEY_G),
        H(org.lwjgl.input.Keyboard.KEY_H),
        J(org.lwjgl.input.Keyboard.KEY_J),
        K(org.lwjgl.input.Keyboard.KEY_K),
        L(org.lwjgl.input.Keyboard.KEY_L),
        SEMICOLON(org.lwjgl.input.Keyboard.KEY_SEMICOLON),
        APOSTROPHE(org.lwjgl.input.Keyboard.KEY_APOSTROPHE),
        GRAVE(org.lwjgl.input.Keyboard.KEY_GRAVE),
        LSHIFT(org.lwjgl.input.Keyboard.KEY_LSHIFT),
        BACKSLASH(org.lwjgl.input.Keyboard.KEY_BACKSLASH),
        Z(org.lwjgl.input.Keyboard.KEY_Z),
        X(org.lwjgl.input.Keyboard.KEY_X),
        C(org.lwjgl.input.Keyboard.KEY_C),
        V(org.lwjgl.input.Keyboard.KEY_V),
        B(org.lwjgl.input.Keyboard.KEY_B),
        N(org.lwjgl.input.Keyboard.KEY_N),
        M(org.lwjgl.input.Keyboard.KEY_M),
        COMMA(org.lwjgl.input.Keyboard.KEY_COMMA),
        PERIOD(org.lwjgl.input.Keyboard.KEY_PERIOD),
        SLASH(org.lwjgl.input.Keyboard.KEY_SLASH),
        RSHIFT(org.lwjgl.input.Keyboard.KEY_RSHIFT),
        MULTIPLY(org.lwjgl.input.Keyboard.KEY_MULTIPLY),
        LMENU(org.lwjgl.input.Keyboard.KEY_LMENU),
        SPACE(org.lwjgl.input.Keyboard.KEY_SPACE),
        CAPITAL(org.lwjgl.input.Keyboard.KEY_CAPITAL),
        F1(org.lwjgl.input.Keyboard.KEY_F1),
        F2(org.lwjgl.input.Keyboard.KEY_F2),
        F3(org.lwjgl.input.Keyboard.KEY_F3),
        F4(org.lwjgl.input.Keyboard.KEY_F4),
        F5(org.lwjgl.input.Keyboard.KEY_F5),
        F6(org.lwjgl.input.Keyboard.KEY_F6),
        F7(org.lwjgl.input.Keyboard.KEY_F7),
        F8(org.lwjgl.input.Keyboard.KEY_F8),
        F9(org.lwjgl.input.Keyboard.KEY_F9),
        F10(org.lwjgl.input.Keyboard.KEY_F10),
        NUMLOCK(org.lwjgl.input.Keyboard.KEY_NUMLOCK),
        SCROLL(org.lwjgl.input.Keyboard.KEY_SCROLL),
        NUMPAD7(org.lwjgl.input.Keyboard.KEY_NUMPAD7),
        NUMPAD8(org.lwjgl.input.Keyboard.KEY_NUMPAD8),
        NUMPAD9(org.lwjgl.input.Keyboard.KEY_NUMPAD9),
        SUBTRACT(org.lwjgl.input.Keyboard.KEY_SUBTRACT),
        NUMPAD4(org.lwjgl.input.Keyboard.KEY_NUMPAD4),
        NUMPAD5(org.lwjgl.input.Keyboard.KEY_NUMPAD5),
        NUMPAD6(org.lwjgl.input.Keyboard.KEY_NUMPAD6),
        ADD(org.lwjgl.input.Keyboard.KEY_ADD),
        NUMPAD1(org.lwjgl.input.Keyboard.KEY_NUMPAD1),
        NUMPAD2(org.lwjgl.input.Keyboard.KEY_NUMPAD2),
        NUMPAD3(org.lwjgl.input.Keyboard.KEY_NUMPAD3),
        NUMPAD0(org.lwjgl.input.Keyboard.KEY_NUMPAD0),
        DECIMAL(org.lwjgl.input.Keyboard.KEY_DECIMAL),
        F11(org.lwjgl.input.Keyboard.KEY_F11),
        F12(org.lwjgl.input.Keyboard.KEY_F12),
        F13(org.lwjgl.input.Keyboard.KEY_F13),
        F14(org.lwjgl.input.Keyboard.KEY_F14),
        F15(org.lwjgl.input.Keyboard.KEY_F15),
        F16(org.lwjgl.input.Keyboard.KEY_F16),
        F17(org.lwjgl.input.Keyboard.KEY_F17),
        F18(org.lwjgl.input.Keyboard.KEY_F18),
        F19(org.lwjgl.input.Keyboard.KEY_F19),
        NUMPADEQUALS(org.lwjgl.input.Keyboard.KEY_NUMPADEQUALS),
        COLON(org.lwjgl.input.Keyboard.KEY_COLON),
        NUMPADENTER(org.lwjgl.input.Keyboard.KEY_NUMPADENTER),
        RCONTROL(org.lwjgl.input.Keyboard.KEY_RCONTROL),
        DIVIDE(org.lwjgl.input.Keyboard.KEY_DIVIDE),
        PAUSE(org.lwjgl.input.Keyboard.KEY_PAUSE),
        HOME(org.lwjgl.input.Keyboard.KEY_HOME),
        UP(org.lwjgl.input.Keyboard.KEY_UP),
        LEFT(org.lwjgl.input.Keyboard.KEY_LEFT),
        RIGHT(org.lwjgl.input.Keyboard.KEY_RIGHT),
        END(org.lwjgl.input.Keyboard.KEY_END),
        DOWN(org.lwjgl.input.Keyboard.KEY_DOWN),
        INSERT(org.lwjgl.input.Keyboard.KEY_INSERT),
        DELETE(org.lwjgl.input.Keyboard.KEY_DELETE),
        LMETA(org.lwjgl.input.Keyboard.KEY_LMETA),
        RMETA(org.lwjgl.input.Keyboard.KEY_RMETA);

        private final int code;

        KeyCode(int code) {
            this.code = code;
        }
    }

    @SideOnly(Side.CLIENT)
    public static void registerKey(String name, KeyCode keyCode, String category, Runnable handler) {
        KeyBinding key = new KeyBinding(name, keyCode.code, category);
        ClientRegistry.registerKeyBinding(key);
        ClientEvents.TICK.subscribe(() -> {
            if (key.isKeyDown()) {
                handler.run();
            }
        });
    }

    @SideOnly(Side.CLIENT)
    public static void registerClientEvents() {
        ClientEvents.TICK.subscribe(() -> {
            EntityPlayerSP player = Minecraft.getMinecraft().player;
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
