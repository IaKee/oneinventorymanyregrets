package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveFoxAccess;
import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.fox.Fox;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.stream.Stream;

@Mixin(Fox.class)
public abstract class FoxMixin implements HiveFoxAccess {
    @Invoker("getTrustedEntities")
    protected abstract Stream<EntityReference<LivingEntity>> oimr$invokeGetTrustedEntities();

    @Invoker("addTrustedEntity")
    protected abstract void oimr$invokeAddTrustedEntity(LivingEntity entity);

    @Override
    public Stream<EntityReference<LivingEntity>> oimr$getTrustedEntities() {
        return this.oimr$invokeGetTrustedEntities();
    }

    @Override
    public void oimr$addTrustedEntity(LivingEntity entity) {
        this.oimr$invokeAddTrustedEntity(entity);
    }

    @ModifyVariable(method = "setTarget", at = @At("HEAD"), argsOnly = true)
    private @Nullable LivingEntity oimr$preventFriendlyFoxTarget(@Nullable LivingEntity target) {
        if (target != null && HiveMindManager.shouldPreventPetAttack((Mob) (Object) this, target)) {
            return null;
        }
        return target;
    }
}
