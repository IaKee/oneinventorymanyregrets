package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveFoodDataFieldsAccess;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FoodData.class)
public interface FoodDataMixin extends HiveFoodDataFieldsAccess {
    @Override
    @Accessor("exhaustionLevel")
    float oimr$getExhaustionLevel();

    @Override
    @Accessor("exhaustionLevel")
    void oimr$setExhaustionLevel(float exhaustionLevel);

    @Override
    @Accessor("tickTimer")
    int oimr$getTickTimer();

    @Override
    @Accessor("tickTimer")
    void oimr$setTickTimer(int tickTimer);
}
