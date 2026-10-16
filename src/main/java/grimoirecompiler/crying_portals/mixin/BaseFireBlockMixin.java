package grimoirecompiler.crying_portals.mixin;

import grimoirecompiler.crying_portals.CryingPortalRegistry;
import grimoirecompiler.crying_portals.CryingPortalShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(BaseFireBlock.class)
public class BaseFireBlockMixin {

    /**
     * Lights a Crying Portal when fire is placed inside a frame containing Crying Obsidian, in any dimension.
     * Frames made only of Obsidian fall through to the vanilla Nether portal logic.
     */
    @Inject(
            method = "onPlace",
            at = @At("HEAD"),
            cancellable = true
    )
    private void crying_portals$onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl, CallbackInfo info) {
        if (!blockState2.is(blockState.getBlock())) {
            Optional<CryingPortalShape> optional = CryingPortalShape.findEmptyPortalShape(level, blockPos, Direction.Axis.X);
            if (optional.isPresent()) {
                CryingPortalShape shape = optional.get();
                shape.createPortalBlocks(level);
                if (level instanceof ServerLevel serverLevel) {
                    CryingPortalRegistry.get(serverLevel.getServer())
                            .add(shape.getSignature(), CryingPortalRegistry.PortalRecord.of(serverLevel.dimension(), shape));
                }
                info.cancel();
            }
        }
    }
}
