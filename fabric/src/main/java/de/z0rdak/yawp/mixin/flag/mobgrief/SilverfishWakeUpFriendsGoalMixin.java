package de.z0rdak.yawp.mixin.flag.mobgrief;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import de.z0rdak.yawp.api.events.flag.FlagCheckRequest;
import de.z0rdak.yawp.core.flag.FlagState;
import de.z0rdak.yawp.platform.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static de.z0rdak.yawp.api.FlagEvaluator.processCheck;
import static de.z0rdak.yawp.core.flag.RegionFlag.MOB_GRIEFING;
import static de.z0rdak.yawp.handler.HandlerUtil.isServerSide;

@Mixin(targets = "net.minecraft.world.entity.monster.Silverfish$SilverfishWakeUpFriendsGoal")
public abstract class SilverfishWakeUpFriendsGoalMixin {

    // Keep vanilla's search, random stopping and gamerule handling; guard only the block being destroyed.
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;)Z"))
    private boolean onSilverFishDestroyBlock(Level level, BlockPos pos, boolean drop, Entity source, Operation<Boolean> original) {
        if (isServerSide(level)) {
            FlagCheckRequest checkEvent = new FlagCheckRequest(pos, MOB_GRIEFING, level.dimension());
            if (!Services.FLAG_EVENT_DISPATCHER.post(checkEvent) && processCheck(checkEvent) == FlagState.DENIED) {
                return false;
            }
        }
        return original.call(level, pos, drop, source);
    }
}
