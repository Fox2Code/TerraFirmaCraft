package net.dries007.tfc.mixin.world.gen.feature.template;

import java.util.List;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.gen.feature.template.PlacementSettings;
import net.minecraft.world.gen.feature.template.Template;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Template.class)
public interface TemplateAccessor
{
    /**
     * Used by {@link net.dries007.tfc.util.Helpers#addTemplateToWorldForTreeGen(Template, PlacementSettings, IWorld, BlockPos)} for a optimized implementation
     */
    @Accessor("palettes")
    List<Template.Palette> accessor$getPalettes();
}
