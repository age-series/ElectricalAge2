package cam72cam.mod.util;

import cam72cam.mod.block.BlockEntity;
import cam72cam.mod.block.tile.TileEntity;
import cam72cam.mod.item.ItemStack;
import cam72cam.mod.math.Vec3d;
import cam72cam.mod.math.Vec3i;
import cam72cam.mod.world.World;
import net.minecraft.nbt.CompoundNBT;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class TagCompound {
    public final CompoundNBT internal;

    public TagCompound(CompoundNBT data) {
        this.internal = data;
    }

    public TagCompound() {
        this(new CompoundNBT());
    }

    public boolean hasKey(String key) {
        return internal.contains(key);
    }

    public boolean getBoolean(String key) {
        return internal.getBoolean(key);
    }

    public void setBoolean(String key, boolean value) {
        internal.putBoolean(key, value);
    }

    public byte getByte(String key) {
        return internal.getByte(key);
    }

    public void setByte(String key, byte value) {
        internal.putByte(key, value);
    }

    public int getInteger(String key) {
        return internal.getInt(key);
    }

    public void setInteger(String key, int value) {
        internal.putInt(key, value);
    }

    public long getLong(String key) {
        return internal.getLong(key);
    }

    public void setLong(String key, long value) {
        internal.putLong(key, value);
    }

    public float getFloat(String key) {
        return internal.getFloat(key);
    }

    public void setFloat(String key, float value) {
        internal.putFloat(key, value);
    }

    public double getDouble(String key) {
        return internal.getDouble(key);
    }

    public void setDouble(String key, double value) {
        internal.putDouble(key, value);
    }

    public String getString(String key) {
        if (internal.contains(key)) {
            return internal.getString(key);
        }
        return null;
    }

    public void setString(String key, String value) {
        if (value == null) {
            internal.remove(key);
        } else {
            internal.putString(key, value);
        }
    }

    public UUID getUUID(String key) {
        if (!internal.contains(key)) {
            return null;
        }
        return UUID.fromString(getString(key));
    }

    public void setUUID(String key, UUID value) {
        internal.remove(key);
        if (value != null) {
            setString(key, value.toString());
        }
    }

    public Vec3i getVec3i(String key) {
        if (internal.getTagId(key) == 4) {
            return new Vec3i(internal.getLong(key));
        }

        CompoundNBT tag = internal.getCompound(key);
        return new Vec3i(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"));
    }

    public void setVec3i(String key, Vec3i pos) {
        CompoundNBT tag = new CompoundNBT();
        tag.putInt("X", pos.x);
        tag.putInt("Y", pos.y);
        tag.putInt("Z", pos.z);
        internal.put(key, tag);
    }

    public Vec3d getVec3d(String key) {
        CompoundNBT nbt = internal.getCompound(key);
        return new Vec3d(nbt.getDouble("x"), nbt.getDouble("y"), nbt.getDouble("z"));
    }

    public void setVec3d(String key, Vec3d value) {
        CompoundNBT nbt = new CompoundNBT();
        if (value != null) {
            nbt.putDouble("x", value.x);
            nbt.putDouble("y", value.y);
            nbt.putDouble("z", value.z);
        }
        internal.put(key, nbt);
    }

    public cam72cam.mod.entity.Entity getEntity(String key, World world) {
        return getEntity(key, world, cam72cam.mod.entity.Entity.class);
    }

    public <T extends cam72cam.mod.entity.Entity> T getEntity(String key, World world, Class<T> cls) {
        CompoundNBT data = internal.getCompound(key);
        UUID id = data.getUniqueId("id");
        int dim = data.getInt("world");
        world = World.get(dim, world.isClient);
        if (world == null) {
            return null;
        }
        return world.getEntity(id, cls);
    }

    public void setEntity(String key, cam72cam.mod.entity.Entity entity) {
        CompoundNBT data = new CompoundNBT();
        data.putUniqueId("id", entity.internal.getUniqueID());
        data.putInt("world", entity.getWorld().getId());
        internal.put(key, data);
    }

    public <T extends Enum> T getEnum(String key, Class<T> cls) {
        return cls.getEnumConstants()[internal.getInt(key)];
    }

    public void setEnum(String key, Enum value) {
        internal.putInt(key, value.ordinal());
    }

    public void setEnumList(String key, List<? extends Enum> items) {
        internal.putIntArray(key, items.stream().map(Enum::ordinal).mapToInt(i -> i).toArray());
    }

    public <T extends Enum> List<T> getEnumList(String key, Class<T> cls) {
        return Arrays.stream(internal.getIntArray(key)).mapToObj((int i) -> cls.getEnumConstants()[i]).collect(Collectors.toList());
    }

    public TagCompound get(String key) {
        return new TagCompound(internal.getCompound(key));
    }

    public void set(String key, TagCompound value) {
        internal.put(key, value.internal);
    }

    public void remove(String key) {
        internal.remove(key);
    }

    public <T> List<T> getList(String key, Function<TagCompound, T> decoder) {
        List<T> list = new ArrayList<>();
        CompoundNBT data = internal.getCompound(key);
        for (int i = 0; i < data.getInt("count"); i++) {
            list.add(decoder.apply(new TagCompound(data.getCompound(i + ""))));
        }
        return list;
    }

    public <T> void setList(String key, List<T> list, Function<T, TagCompound> encoder) {
        CompoundNBT data = new CompoundNBT();
        data.putInt("count", list.size());
        for (int i = 0; i < list.size(); i++) {
            data.put(i + "", encoder.apply(list.get(i)).internal);
        }
        internal.put(key, data);
    }

    public <K, V> Map<K, V> getMap(String key, Function<String, K> keyFn, Function<TagCompound, V> valFn) {
        Map<K, V> map = new HashMap<>();
        CompoundNBT data = internal.getCompound(key);
        for (String item : data.keySet()) {
            map.put(keyFn.apply(item), valFn.apply(new TagCompound(data.getCompound(item))));
        }
        return map;
    }

    public <K, V> void setMap(String key, Map<K, V> map, Function<K, String> keyFn, Function<V, TagCompound> valFn) {
        CompoundNBT data = new CompoundNBT();

        for (K item : map.keySet()) {
            data.put(keyFn.apply(item), valFn.apply(map.get(item)).internal);
        }

        internal.put(key, data);
    }

    public ItemStack getStack(String key) {
        return new ItemStack(new TagCompound(internal.getCompound(key)));
    }

    public void setStack(String key, ItemStack stack) {
        internal.put(key, stack.toTag().internal);
    }

    public String toString() {
        return internal.toString();
    }

    public void setWorld(String key, World world) {
        setInteger(key, world.getId());
    }

    public World getWorld(String key, boolean isClient) {
        return World.get(getInteger(key), isClient);
    }

    public <T extends BlockEntity> void setTile(String key, T preview) {
        TagCompound ted = new TagCompound();
        ted.setWorld("world", preview.world);

        TagCompound data = new TagCompound();
        preview.internal.write(data.internal);
        ted.set("data", data);

        set(key, ted);
    }

    public <T extends BlockEntity> T getTile(String key, boolean isClient) {
        TagCompound ted = get(key);
        World world = ted.getWorld("world", isClient);

        if (world == null) {
            return null;
        }

        if (!ted.hasKey("data")) {
            return null;
        }

        net.minecraft.tileentity.TileEntity te = net.minecraft.tileentity.TileEntity.create(ted.get("data").internal);
        te.setWorld(world.internal);
        assert te instanceof TileEntity;
        return (T) ((TileEntity) te).instance();
    }
}
