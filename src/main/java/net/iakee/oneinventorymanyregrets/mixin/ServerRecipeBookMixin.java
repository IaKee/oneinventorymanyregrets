package net.iakee.oneinventorymanyregrets.mixin;

import net.iakee.oneinventorymanyregrets.hivemind.HiveMindManager;
import net.iakee.oneinventorymanyregrets.hivemind.HiveServerRecipeBookAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerRecipeBook;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Mixin(ServerRecipeBook.class)
public abstract class ServerRecipeBookMixin implements HiveServerRecipeBookAccess {
    @Shadow
    @Final
    protected Set<ResourceKey<Recipe<?>>> known;

    @Shadow
    @Final
    protected Set<ResourceKey<Recipe<?>>> highlight;

    @Shadow
    public abstract void sendInitialRecipeBook(ServerPlayer player);

    @Inject(method = "addRecipes", at = @At("RETURN"))
    private void oimr$onAddRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > 0) {
            HiveMindManager.onRecipesAdded(player, recipes);
        }
    }

    @Inject(method = "removeRecipes", at = @At("RETURN"))
    private void oimr$onRemoveRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player, CallbackInfoReturnable<Integer> cir) {
        if (cir.getReturnValue() > 0) {
            HiveMindManager.onRecipesRemoved(player, recipes);
        }
    }

    @Override
    public Set<ResourceKey<Recipe<?>>> oimr$exportKnown() {
        return new LinkedHashSet<>(this.known);
    }

    @Override
    public Set<ResourceKey<Recipe<?>>> oimr$exportHighlight() {
        return new LinkedHashSet<>(this.highlight);
    }

    @Override
    public void oimr$syncFromHive(
        Collection<ResourceKey<Recipe<?>>> knownRecipes,
        Collection<ResourceKey<Recipe<?>>> highlightRecipes,
        ServerPlayer player
    ) {
        this.known.clear();
        this.highlight.clear();
        this.known.addAll(knownRecipes);
        this.highlight.addAll(highlightRecipes);
        this.sendInitialRecipeBook(player);
    }
}
