package grimoirecompiler.crying_portals;

import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import org.jspecify.annotations.Nullable;

/**
 * A Nether portal whose destination is the next portal sharing its frame signature,
 * as tracked by {@link CryingPortalRegistry}.
 */
public class CryingPortalBlock extends NetherPortalBlock {
    public static final MapCodec<NetherPortalBlock> CODEC = simpleCodec(CryingPortalBlock::new);

    public CryingPortalBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<NetherPortalBlock> codec() {
        return CODEC;
    }

    /** Removes Zombified Piglins spawning. */
    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    /** Same as vanilla, but validated against the Crying Portal frame. */
    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess scheduledTickAccess, BlockPos pos,
                                     Direction direction, BlockPos neighborPos, BlockState neighborState, RandomSource random) {
        Direction.Axis updateAxis = direction.getAxis();
        Direction.Axis portalAxis = state.getValue(AXIS);
        boolean outsidePortalPlane = portalAxis != updateAxis && updateAxis.isHorizontal();
        if (!outsidePortalPlane && !neighborState.is(this) && CryingPortalShape.findCompleteShape(level, pos, portalAxis).isEmpty()) {
            return Blocks.AIR.defaultBlockState();
        }
        return state;
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        CryingPortalRegistry.get(level.getServer()).removeAt(level.dimension(), pos);
    }

    @Override
    public @Nullable TeleportTransition getPortalDestination(ServerLevel level, Entity entity, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(this)) return null;
        Optional<CryingPortalShape> shape = CryingPortalShape.findCompleteShape(level, pos, state.getValue(AXIS));
        if (shape.isEmpty()) return null;

        // The live frame is the source of truth: (re)register the portal if the stored record is missing,
        // stale (lit before the registry existed, data lost) or out of date (frame edited in place)
        CryingPortalRegistry registry = CryingPortalRegistry.get(level.getServer());
        CryingPortalRegistry.Entry source = new CryingPortalRegistry.Entry(shape.get().getSignature(),
                CryingPortalRegistry.PortalRecord.of(level.dimension(), shape.get()));
        if (!registry.find(level.dimension(), pos).filter(source::equals).isPresent()) {
            registry.add(source.signature(), source.portal());
        }

        for (CryingPortalRegistry.PortalRecord candidate : registry.successors(source)) {
            ServerLevel targetLevel = level.getServer().getLevel(candidate.dimension());
            // Dimension not loaded right now (e.g. disabled datapack): skip it but keep it in the ring
            if (targetLevel == null) continue;
            if (isStillValid(targetLevel, candidate, source.signature())) {
                return CryingPortalShape.createTransition(entity, pos, targetLevel, candidate.bottomLeft(),
                        TeleportTransition.PLAY_PORTAL_SOUND.then(TeleportTransition.PLACE_PORTAL_TICKET));
            }
            // Destroyed or altered without us noticing (e.g. /setblock, other mods): drop it from the ring
            registry.remove(new CryingPortalRegistry.Entry(source.signature(), candidate));
        }

        if (entity instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.crying_portals.no_link"), true);
        }
        return null;
    }

    private static boolean isStillValid(ServerLevel level, CryingPortalRegistry.PortalRecord portal, String signature) {
        if (!level.getBlockState(portal.bottomLeft()).is(ModBlocks.CRYING_PORTAL)) return false;
        return CryingPortalShape.findCompleteShape(level, portal.bottomLeft(), portal.axis())
                .filter(shape -> shape.getBottomLeft().equals(portal.bottomLeft()) && signature.equals(shape.getSignature()))
                .isPresent();
    }
}
