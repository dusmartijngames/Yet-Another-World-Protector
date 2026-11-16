package de.z0rdak.yawp.mixin.flag.mobgrief;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.z0rdak.yawp.api.events.flag.FlagCheckRequest;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static de.z0rdak.yawp.api.FlagEvaluator.processCheck;
import static de.z0rdak.yawp.core.flag.RegionFlag.DRAGON_BLOCK_PROT;
import static de.z0rdak.yawp.core.flag.RegionFlag.MOB_GRIEFING;

@Mixin(EnderDragon.class)
public abstract class EnderDragonMixin {

    // Wrap each removal so denying one block does not cancel the rest of the wall scan.
    @WrapOperation(method = "checkWalls", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z"))
    private boolean onDragonDestroyBlocks(ServerLevel level, BlockPos pos, boolean moving, Operation<Boolean> original) {
        FlagCheckRequest checkEvent = new FlagCheckRequest(pos, DRAGON_BLOCK_PROT, level.dimension());
        if (Services.FLAG_EVENT_DISPATCHER.post(checkEvent)) {
            return original.call(level, pos, moving);
        }
        FlagState dragonProtection = processCheck(checkEvent);

        checkEvent = new FlagCheckRequest(pos, MOB_GRIEFING, level.dimension());
        if (Services.FLAG_EVENT_DISPATCHER.post(checkEvent)) {
            return original.call(level, pos, moving);
        }
        FlagState mobGriefing = processCheck(checkEvent);

        return dragonProtection != FlagState.DENIED && mobGriefing != FlagState.DENIED
                && original.call(level, pos, moving);
    }
}
