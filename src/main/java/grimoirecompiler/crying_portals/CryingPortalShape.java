package grimoirecompiler.crying_portals;

import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;

/**
 * Adapted from vanilla {@link PortalShape}: the frame accepts Obsidian and Crying Obsidian,
 * and the shape carries the frame's {@link PortalSignature}.
 */
public class CryingPortalShape {
    private static final int MIN_WIDTH = 2;
    private static final int MIN_HEIGHT = 3;
    private static final BlockBehaviour.StatePredicate FRAME = (blockState, blockGetter, blockPos) -> blockState.is(Blocks.OBSIDIAN) || blockState.is(Blocks.CRYING_OBSIDIAN);
    private final Direction.Axis axis;
    private final Direction rightDir;
    private final int numPortalBlocks;
    private final BlockPos bottomLeft;
    private final int height;
    private final int width;
    private final @Nullable String signature;
    private final boolean crying;

    private CryingPortalShape(Direction.Axis axis, int numPortalBlocks, Direction rightDir, BlockPos bottomLeft, int width, int height) {
        this.axis = axis;
        this.numPortalBlocks = numPortalBlocks;
        this.rightDir = rightDir;
        this.bottomLeft = bottomLeft;
        this.width = width;
        this.height = height;
        this.signature = null;
        this.crying = false;
    }

    private CryingPortalShape(BlockGetter blockGetter, Direction.Axis axis, int numPortalBlocks, Direction rightDir, BlockPos bottomLeft, int width, int height) {
        this.axis            = axis;
        this.numPortalBlocks = numPortalBlocks;
        this.rightDir        = rightDir;
        this.bottomLeft      = bottomLeft;
        this.width           = width;
        this.height          = height;

        /*
            Reads which frame blocks (corners excluded) are Crying Obsidian (true) or Obsidian (false).
            Base and top are read left to right, the side columns bottom to top.
         */
        boolean[] baseSequence  = new boolean[width];
        boolean[] topSequence   = new boolean[width];
        boolean[] leftSequence  = new boolean[height];
        boolean[] rightSequence = new boolean[height];

        BlockPos blockPos = bottomLeft.below();
        for (int x = 0; x < width; x++, blockPos = blockPos.relative(rightDir)) {
            baseSequence[x] = blockGetter.getBlockState(blockPos).is(Blocks.CRYING_OBSIDIAN);
            topSequence[x]  = blockGetter.getBlockState(blockPos.relative(Direction.UP, height + 1)).is(Blocks.CRYING_OBSIDIAN);
        }

        blockPos = bottomLeft.relative(rightDir, -1);
        for (int y = 0; y < height; y++, blockPos = blockPos.relative(Direction.UP)) {
            leftSequence[y]  = blockGetter.getBlockState(blockPos).is(Blocks.CRYING_OBSIDIAN);
            rightSequence[y] = blockGetter.getBlockState(blockPos.relative(rightDir, width + 1)).is(Blocks.CRYING_OBSIDIAN);
        }

        this.signature = PortalSignature.compute(baseSequence, topSequence, leftSequence, rightSequence);
        this.crying = PortalSignature.hasCrying(baseSequence, topSequence, leftSequence, rightSequence);
    }

    public static Optional<CryingPortalShape> findEmptyPortalShape(LevelAccessor levelAccessor, BlockPos blockPos, Direction.Axis axis) {
        return findPortalShape(levelAccessor, blockPos, (portalShape) -> portalShape.isValid() && portalShape.isCrying() && portalShape.numPortalBlocks == 0, axis);
    }

    public static Optional<CryingPortalShape> findPortalShape(LevelAccessor levelAccessor, BlockPos blockPos, Predicate<CryingPortalShape> predicate, Direction.Axis axis) {
        Optional<CryingPortalShape> optional = Optional.of(findAnyShape(levelAccessor, blockPos, axis)).filter(predicate);
        if (optional.isPresent()) {
            return optional;
        } else {
            Direction.Axis axis2 = axis == Axis.X ? Axis.Z : Axis.X;
            return Optional.of(findAnyShape(levelAccessor, blockPos, axis2)).filter(predicate);
        }
    }

    /** The complete, lit Crying Portal containing {@code blockPos} along {@code axis}, if any. */
    public static Optional<CryingPortalShape> findCompleteShape(BlockGetter blockGetter, BlockPos blockPos, Direction.Axis axis) {
        return Optional.of(findAnyShape(blockGetter, blockPos, axis)).filter(shape -> shape.isComplete() && shape.isCrying());
    }

    public static CryingPortalShape findAnyShape(BlockGetter blockGetter, BlockPos blockPos, Direction.Axis axis) {
        Direction direction = axis == Axis.X ? Direction.WEST : Direction.SOUTH;
        BlockPos blockPos2 = calculateBottomLeft(blockGetter, direction, blockPos);
        if (blockPos2 == null) {
            return new CryingPortalShape(axis, 0, direction, blockPos, 0, 0);
        } else {
            int i = calculateWidth(blockGetter, blockPos2, direction);
            if (i == 0) {
                return new CryingPortalShape(axis, 0, direction, blockPos2, 0, 0);
            } else {
                MutableInt mutableInt = new MutableInt();
                int j = calculateHeight(blockGetter, blockPos2, direction, i, mutableInt);
                return new CryingPortalShape(blockGetter, axis, mutableInt.intValue(), direction, blockPos2, i, j);
            }
        }
    }

    private static @Nullable BlockPos calculateBottomLeft(BlockGetter blockGetter, Direction direction, BlockPos blockPos) {
        for(int i = Math.max(blockGetter.getMinY(), blockPos.getY() - 21); blockPos.getY() > i && isEmpty(blockGetter.getBlockState(blockPos.below())); blockPos = blockPos.below()) {
        }

        Direction direction2 = direction.getOpposite();
        int j = getDistanceUntilEdgeAboveFrame(blockGetter, blockPos, direction2) - 1;
        return j < 0 ? null : blockPos.relative(direction2, j);
    }

    private static int calculateWidth(BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
        int i = getDistanceUntilEdgeAboveFrame(blockGetter, blockPos, direction);
        return i >= MIN_WIDTH && i <= PortalShape.MAX_WIDTH ? i : 0;
    }

    private static int getDistanceUntilEdgeAboveFrame(BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for(int i = 0; i <= PortalShape.MAX_WIDTH; ++i) {
            mutableBlockPos.set(blockPos).move(direction, i);
            BlockState blockState = blockGetter.getBlockState(mutableBlockPos);
            if (!isEmpty(blockState)) {
                if (FRAME.test(blockState, blockGetter, mutableBlockPos)) {
                    return i;
                }
                break;
            }

            BlockState blockState2 = blockGetter.getBlockState(mutableBlockPos.move(Direction.DOWN));
            if (!FRAME.test(blockState2, blockGetter, mutableBlockPos)) {
                break;
            }
        }

        return 0;
    }

    private static int calculateHeight(BlockGetter blockGetter, BlockPos blockPos, Direction direction, int i, MutableInt mutableInt) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
        int j = getDistanceUntilTop(blockGetter, blockPos, direction, mutableBlockPos, i, mutableInt);
        return j >= MIN_HEIGHT && j <= PortalShape.MAX_HEIGHT && hasTopFrame(blockGetter, blockPos, direction, mutableBlockPos, i, j) ? j : 0;
    }

    private static boolean hasTopFrame(BlockGetter blockGetter, BlockPos blockPos, Direction direction, BlockPos.MutableBlockPos mutableBlockPos, int i, int j) {
        for(int k = 0; k < i; ++k) {
            BlockPos.MutableBlockPos mutableBlockPos2 = mutableBlockPos.set(blockPos).move(Direction.UP, j).move(direction, k);
            if (!FRAME.test(blockGetter.getBlockState(mutableBlockPos2), blockGetter, mutableBlockPos2)) {
                return false;
            }
        }

        return true;
    }

    private static int getDistanceUntilTop(BlockGetter blockGetter, BlockPos blockPos, Direction direction, BlockPos.MutableBlockPos mutableBlockPos, int i, MutableInt mutableInt) {
        for(int j = 0; j < PortalShape.MAX_HEIGHT; ++j) {
            mutableBlockPos.set(blockPos).move(Direction.UP, j).move(direction, -1);
            if (!FRAME.test(blockGetter.getBlockState(mutableBlockPos), blockGetter, mutableBlockPos)) {
                return j;
            }

            mutableBlockPos.set(blockPos).move(Direction.UP, j).move(direction, i);
            if (!FRAME.test(blockGetter.getBlockState(mutableBlockPos), blockGetter, mutableBlockPos)) {
                return j;
            }

            for(int k = 0; k < i; ++k) {
                mutableBlockPos.set(blockPos).move(Direction.UP, j).move(direction, k);
                BlockState blockState = blockGetter.getBlockState(mutableBlockPos);
                if (!isEmpty(blockState)) {
                    return j;
                }

                if (blockState.is(ModBlocks.CRYING_PORTAL)) {
                    mutableInt.increment();
                }
            }
        }

        return PortalShape.MAX_HEIGHT;
    }

    private static boolean isEmpty(BlockState blockState) {
        return blockState.isAir() || blockState.is(BlockTags.FIRE) || blockState.is(ModBlocks.CRYING_PORTAL);
    }

    public boolean isValid() {
        return this.width >= MIN_WIDTH && this.width <= PortalShape.MAX_WIDTH && this.height >= MIN_HEIGHT && this.height <= PortalShape.MAX_HEIGHT;
    }

    public void createPortalBlocks(LevelAccessor levelAccessor) {
        BlockState blockState = ModBlocks.CRYING_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);
        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1)).forEach((blockPos) -> levelAccessor.setBlock(blockPos, blockState, 18));
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }

    /** True when at least one signature block (corners excluded) is Crying Obsidian. */
    public boolean isCrying() {
        return this.crying;
    }

    public @Nullable String getSignature() {
        return this.signature;
    }

    public BlockPos getBottomLeft() {
        return this.bottomLeft;
    }

    public Direction.Axis getAxis() {
        return this.axis;
    }

    public int getWidth() {
        return this.width;
    }

    public int getHeight() {
        return this.height;
    }

    /**
     * Port of vanilla's private {@code NetherPortalBlock.getDimensionTransitionFromExit}: keeps the entity's
     * relative position inside the source portal and maps it onto the destination portal.
     */
    public static TeleportTransition createTransition(Entity entity, BlockPos sourcePos, ServerLevel targetLevel, BlockPos targetPos, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        BlockState sourceState = entity.level().getBlockState(sourcePos);
        Direction.Axis sourceAxis;
        Vec3 relative;
        if (sourceState.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            sourceAxis = sourceState.getValue(BlockStateProperties.HORIZONTAL_AXIS);
            BlockUtil.FoundRectangle sourceRect = BlockUtil.getLargestRectangleAround(sourcePos, sourceAxis, PortalShape.MAX_WIDTH, Axis.Y, PortalShape.MAX_HEIGHT, pos -> entity.level().getBlockState(pos) == sourceState);
            relative = entity.getRelativePortalPosition(sourceAxis, sourceRect);
        } else {
            sourceAxis = Axis.X;
            relative = new Vec3(0.5, 0.0, 0.0);
        }

        BlockState targetState = targetLevel.getBlockState(targetPos);
        Direction.Axis targetAxis = targetState.getOptionalValue(BlockStateProperties.HORIZONTAL_AXIS).orElse(Axis.X);
        BlockUtil.FoundRectangle targetRect = BlockUtil.getLargestRectangleAround(targetPos, targetAxis, PortalShape.MAX_WIDTH, Axis.Y, PortalShape.MAX_HEIGHT, pos -> targetLevel.getBlockState(pos) == targetState);

        BlockPos minCorner = targetRect.minCorner;
        EntityDimensions dimensions = entity.getDimensions(entity.getPose());
        int yRot = sourceAxis == targetAxis ? 0 : 90;
        double along = dimensions.width() / 2.0 + (targetRect.axis1Size - dimensions.width()) * relative.x();
        double up = (targetRect.axis2Size - dimensions.height()) * relative.y();
        double across = 0.5 + relative.z();
        boolean alongX = targetAxis == Axis.X;
        Vec3 position = new Vec3(minCorner.getX() + (alongX ? along : across), minCorner.getY() + up, minCorner.getZ() + (alongX ? across : along));
        Vec3 freePosition = PortalShape.findCollisionFreePosition(position, targetLevel, entity, dimensions);
        return new TeleportTransition(targetLevel, freePosition, Vec3.ZERO, yRot, 0.0F, Relative.union(Relative.DELTA, Relative.ROTATION), postTeleportTransition);
    }
}
