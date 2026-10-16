package grimoirecompiler.crying_portals;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * Server-wide store of every lit Crying Portal, grouped by signature.
 * Each group is a circular list in insertion order: a portal leads to the next one, the last leads to the first.
 */
public class CryingPortalRegistry extends SavedData {
    public record PortalRecord(ResourceKey<Level> dimension, BlockPos bottomLeft, Direction.Axis axis, int width, int height) {
        public static final Codec<PortalRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(PortalRecord::dimension),
                BlockPos.CODEC.fieldOf("bottom_left").forGetter(PortalRecord::bottomLeft),
                Direction.Axis.CODEC.fieldOf("axis").forGetter(PortalRecord::axis),
                Codec.INT.fieldOf("width").forGetter(PortalRecord::width),
                Codec.INT.fieldOf("height").forGetter(PortalRecord::height)
        ).apply(instance, PortalRecord::new));

        public static PortalRecord of(ResourceKey<Level> dimension, CryingPortalShape shape) {
            return new PortalRecord(dimension, shape.getBottomLeft(), shape.getAxis(), shape.getWidth(), shape.getHeight());
        }

        public boolean contains(ResourceKey<Level> dimension, BlockPos pos) {
            if (!this.dimension.equals(dimension)) return false;
            int dy = pos.getY() - bottomLeft.getY();
            if (dy < 0 || dy >= height) return false;
            // Portal blocks extend from bottomLeft towards WEST (X axis) or SOUTH (Z axis), see CryingPortalShape
            if (axis == Direction.Axis.X) {
                int dx = bottomLeft.getX() - pos.getX();
                return pos.getZ() == bottomLeft.getZ() && dx >= 0 && dx < width;
            } else {
                int dz = pos.getZ() - bottomLeft.getZ();
                return pos.getX() == bottomLeft.getX() && dz >= 0 && dz < width;
            }
        }

        /** The blocks this portal occupies, see {@link #contains}. */
        public BoundingBox bounds() {
            BlockPos top = bottomLeft.above(height - 1);
            BlockPos far = axis == Direction.Axis.X ? top.west(width - 1) : top.south(width - 1);
            return BoundingBox.fromCorners(bottomLeft, far);
        }

        public boolean overlaps(PortalRecord other) {
            return dimension.equals(other.dimension) && bounds().intersects(other.bounds());
        }
    }

    public record Entry(String signature, PortalRecord portal) {}

    private static final Codec<CryingPortalRegistry> CODEC = Codec.unboundedMap(Codec.STRING, PortalRecord.CODEC.listOf())
            .xmap(CryingPortalRegistry::new, registry -> registry.portals);

    public static final SavedDataType<CryingPortalRegistry> TYPE =
            new SavedDataType<>(CryingPortals.MOD_ID, CryingPortalRegistry::new, CODEC, null);

    private final Map<String, List<PortalRecord>> portals = new HashMap<>();

    public CryingPortalRegistry() {}

    private CryingPortalRegistry(Map<String, List<PortalRecord>> portals) {
        portals.forEach((signature, list) -> this.portals.put(signature, new ArrayList<>(list)));
    }

    public static CryingPortalRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /** Appends the portal to its signature's ring: the previous last portal now leads here, and this one leads to the first. */
    public void add(String signature, PortalRecord portal) {
        // Drop any stale record occupying the same blocks (e.g. a portal removed without a block update)
        portals.values().forEach(list -> list.removeIf(portal::overlaps));
        portals.values().removeIf(List::isEmpty);
        portals.computeIfAbsent(signature, s -> new ArrayList<>()).add(portal);
        setDirty();
        CryingPortals.LOGGER.debug("Registered Crying Portal {} at {} in {}", signature, portal.bottomLeft(), portal.dimension());
    }

    public void remove(Entry entry) {
        List<PortalRecord> list = portals.get(entry.signature());
        if (list != null && list.remove(entry.portal())) {
            if (list.isEmpty()) portals.remove(entry.signature());
            setDirty();
        }
    }

    /** Removes the portal containing {@code pos}, if any. */
    public void removeAt(ResourceKey<Level> dimension, BlockPos pos) {
        find(dimension, pos).ifPresent(this::remove);
    }

    public Optional<Entry> find(ResourceKey<Level> dimension, BlockPos pos) {
        for (Map.Entry<String, List<PortalRecord>> group : portals.entrySet())
            for (PortalRecord portal : group.getValue())
                if (portal.contains(dimension, pos))
                    return Optional.of(new Entry(group.getKey(), portal));
        return Optional.empty();
    }

    /** The portals that follow {@code entry} in its ring, in travel order, excluding {@code entry} itself. */
    public List<PortalRecord> successors(Entry entry) {
        List<PortalRecord> list = portals.getOrDefault(entry.signature(), List.of());
        int index = list.indexOf(entry.portal());
        List<PortalRecord> result = new ArrayList<>();
        if (index < 0) return result;
        for (int i = 1; i < list.size(); i++)
            result.add(list.get((index + i) % list.size()));
        return result;
    }
}
