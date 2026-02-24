package grimoirecompiler.crying_portals.mixin;

import grimoirecompiler.crying_portals.CryingPortalShape;
import grimoirecompiler.crying_portals.CryingPortals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.PortalShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(BaseFireBlock.class)
public class BaseFireBlockMixin {

    @Inject(
            method = "onPlace",
            at = @At("HEAD"),
            cancellable = true
    )
    private void crying_portals$onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState blockState2, boolean bl, CallbackInfo info) {
        CryingPortals.LOGGER.info("ON FIRE !");
        if (!blockState2.is(blockState.getBlock())) {
            Optional<CryingPortalShape> optional = CryingPortalShape.findEmptyPortalShape(level, blockPos, Direction.Axis.X);
            if (optional.isPresent()) {
                ((CryingPortalShape) optional.get()).createPortalBlocks(level);
                return;
            }
        }
    }
}