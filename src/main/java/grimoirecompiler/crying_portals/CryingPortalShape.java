//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//
package grimoirecompiler.crying_portals;

import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.BlockUtil;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jspecify.annotations.Nullable;

public class CryingPortalShape{
    private static final int MIN_WIDTH = 2;
    private static final int MIN_HEIGHT = 3;
    private static final BlockBehaviour.StatePredicate FRAME = (blockState, blockGetter, blockPos) -> blockState.is(Blocks.OBSIDIAN) || blockState.is(Blocks.CRYING_OBSIDIAN);
    private static final float SAFE_TRAVEL_MAX_ENTITY_XY = 4.0F;
    private static final double SAFE_TRAVEL_MAX_VERTICAL_DELTA = (double)1.0F;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private final int numPortalBlocks;
    private final BlockPos bottomLeft;
    private final int height;
    private final int width;

    private CryingPortalShape(Direction.Axis axis, int i, Direction direction, BlockPos blockPos, int j, int k) {
        this.axis = axis;
        this.numPortalBlocks = i;
        this.rightDir = direction;
        this.bottomLeft = blockPos;
        this.width = j;
        this.height = k;
    }

    private CryingPortalShape(BlockGetter blockGetter, Direction.Axis axis, int i, Direction direction, BlockPos blockPos, int j, int k) {
        this.axis             = axis;
        this.numPortalBlocks  = i;
        this.rightDir         = direction;
        this.bottomLeft       = blockPos;
        this.width            = j;
        this.height           = k;

        /*
            Makes a bit sequence out of the sequence of Crying Obsidians (1) vs Obsidians(0)
            To handle axis reflections it assumes the Higher Order sequence as the right portal sequence
         */
        boolean[] baseSequence  = new boolean[width];
        boolean[] topSequence   = new boolean[width];
        boolean[] frontSequence = new boolean[height];
        boolean[] backSequence  = new boolean[height];

        for(j=0, blockPos = blockPos.below(); j<width; j++, blockPos = blockPos.relative(direction)){
            baseSequence[j] = blockGetter.getBlockState(blockPos).is(Blocks.CRYING_OBSIDIAN);
            topSequence[j]  = blockGetter.getBlockState(blockPos.relative(Direction.UP, height+1)).is(Blocks.CRYING_OBSIDIAN);
        }

        blockPos = bottomLeft.relative(direction,-1);
        for(k=0; k<height; k++, blockPos = blockPos.relative(Direction.UP)){
            backSequence[k]   = blockGetter.getBlockState(blockPos).is(Blocks.CRYING_OBSIDIAN);
            frontSequence[k]  = blockGetter.getBlockState(blockPos.relative(direction, width+1)).is(Blocks.CRYING_OBSIDIAN);
        }

        Boolean mirrored = null;
        Optional<Boolean> inOrder = isHighOrder(baseSequence);
        if (inOrder.isEmpty()) {
            for(int x=0; x<frontSequence.length; x++){
                if(frontSequence[x] != backSequence[x]){
                    mirrored = Boolean.valueOf(frontSequence[x]);
                    break;
                }
            }
            if (mirrored == null){
                inOrder = isHighOrder(topSequence);
                mirrored = inOrder.isPresent() && !inOrder.get();
            }
        } else
            mirrored = inOrder.get();

        boolean fullSequence[] = new boolean[2*(height*width)];
        if(mirrored){
            invert(baseSequence);
            invert(frontSequence);
            System.arraycopy(baseSequence , 0, fullSequence, 0, width);
            System.arraycopy(backSequence , 0, fullSequence, width, height);
            System.arraycopy(topSequence  , 0, fullSequence, width + height, width);
            System.arraycopy(frontSequence, 0, fullSequence, 2*width + height, height);
        }else{
            invert(topSequence);
            invert(backSequence);
            System.arraycopy(baseSequence , 0, fullSequence, 0, width);
            System.arraycopy(frontSequence, 0, fullSequence, width, height);
            System.arraycopy(topSequence  , 0, fullSequence, width + height, width);
            System.arraycopy(backSequence , 0, fullSequence, 2*width + height, height);
        }

        byte[] byteArray = booleansToBytes(fullSequence);

        String hexString = HexFormat.of().formatHex(byteArray);
        CryingPortals.LOGGER.info("Portal HEX: {}", hexString);
    }

    public static void invert(boolean array[]){
        boolean temp;
        for(int i=0, j=array.length-1; i<array.length/2; i++, j--) {
            temp = array[i];
            array[i] = array[j];
            array[j] = temp;
        }
    }

    public static Optional<Boolean> isHighOrder(boolean code[]){
        for(int i=0; i<code.length/2; i++)
            if (code[i] != code[code.length-1-i])
                return Optional.of(Boolean.valueOf(code[i]));
        return Optional.empty();
    }

    public static byte[] booleansToBytes(boolean[] booleans) {
        byte[] bytes = new byte[(int) Math.ceil(booleans.length / 8.0)];
        for (int i = 0; i < booleans.length; i++) {
            if (booleans[i]) {
                bytes[i / 8] |= (1 << (i % 8));
            }
        }
        return bytes;
    }

    public static Optional<CryingPortalShape> findEmptyPortalShape(LevelAccessor levelAccessor, BlockPos blockPos, Direction.Axis axis) {
        CryingPortals.LOGGER.info("Checking Crying Portal");
        return findPortalShape(levelAccessor, blockPos, (portalShape) -> portalShape.isValid() && portalShape.numPortalBlocks == 0, axis);
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
        return i >= 2 && i <= PortalShape.MAX_WIDTH ? i : 0;
    }

    private static int getDistanceUntilEdgeAboveFrame(BlockGetter blockGetter, BlockPos blockPos, Direction direction) {
        BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();

        for(int i = 0; i <= PortalShape.MAX_HEIGHT; ++i) {
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

                if (blockState.is(Blocks.NETHER_PORTAL)) {
                    mutableInt.increment();
                }
            }
        }

        return 21;
    }

    private static boolean isEmpty(BlockState blockState) {
        return blockState.isAir() || blockState.is(BlockTags.FIRE) || blockState.is(Blocks.NETHER_PORTAL);
    }

    public boolean isValid() {
        return this.width >= MIN_WIDTH && this.width <= PortalShape.MAX_HEIGHT && this.height >= MIN_HEIGHT && this.height <= PortalShape.MAX_HEIGHT;
    }

    public void createPortalBlocks(LevelAccessor levelAccessor) {
        BlockState blockState = (BlockState)Blocks.NETHER_PORTAL.defaultBlockState().setValue(NetherPortalBlock.AXIS, this.axis);
        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1)).forEach((blockPos) -> levelAccessor.setBlock(blockPos, blockState, 18));
    }

    public boolean isComplete() {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }

    public static Vec3 getRelativePosition(BlockUtil.FoundRectangle foundRectangle, Direction.Axis axis, Vec3 vec3, EntityDimensions entityDimensions) {
        double d = (double)foundRectangle.axis1Size - (double)entityDimensions.width();
        double e = (double)foundRectangle.axis2Size - (double)entityDimensions.height();
        BlockPos blockPos = foundRectangle.minCorner;
        double g;
        if (d > (double)0.0F) {
            double f = (double)blockPos.get(axis) + (double)entityDimensions.width() / (double)2.0F;
            g = Mth.clamp(Mth.inverseLerp(vec3.get(axis) - f, (double)0.0F, d), (double)0.0F, (double)1.0F);
        } else {
            g = (double)0.5F;
        }

        double f;
        if (e > (double)0.0F) {
            Direction.Axis axis2 = Axis.Y;
            f = Mth.clamp(Mth.inverseLerp(vec3.get(axis2) - (double)blockPos.get(axis2), (double)0.0F, e), (double)0.0F, (double)1.0F);
        } else {
            f = (double)0.0F;
        }

        Direction.Axis axis2 = axis == Axis.X ? Axis.Z : Axis.X;
        double h = vec3.get(axis2) - ((double)blockPos.get(axis2) + (double)0.5F);
        return new Vec3(g, f, h);
    }

    public static Vec3 findCollisionFreePosition(Vec3 vec3, ServerLevel serverLevel, Entity entity, EntityDimensions entityDimensions) {
        if (!(entityDimensions.width() > 4.0F) && !(entityDimensions.height() > 4.0F)) {
            double d = (double)entityDimensions.height() / (double)2.0F;
            Vec3 vec32 = vec3.add((double)0.0F, d, (double)0.0F);
            VoxelShape voxelShape = Shapes.create(AABB.ofSize(vec32, (double)entityDimensions.width(), (double)0.0F, (double)entityDimensions.width()).expandTowards((double)0.0F, (double)1.0F, (double)0.0F).inflate(1.0E-6));
            Optional<Vec3> optional = serverLevel.findFreePosition(entity, voxelShape, vec32, (double)entityDimensions.width(), (double)entityDimensions.height(), (double)entityDimensions.width());
            Optional<Vec3> optional2 = optional.map((vec3x) -> vec3x.subtract((double)0.0F, d, (double)0.0F));
            return (Vec3)optional2.orElse(vec3);
        } else {
            return vec3;
        }
    }
}
