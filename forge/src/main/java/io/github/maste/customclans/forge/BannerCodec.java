package io.github.maste.customclans.forge;

import io.github.maste.customclans.models.ClanBannerData;
import java.util.ArrayList;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;

final class BannerCodec {
    record Design(String material, String patternsJson) {}

    static Design capture(ItemStack stack, MinecraftServer server) {
        if (!(stack.getItem() instanceof BannerItem)) return null;
        var registry = server.registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
        var entries = new ArrayList<String>();
        CompoundTag block = stack.getTagElement("BlockEntityTag");
        if (block != null) for (var raw : block.getList("Patterns", 10)) {
            CompoundTag entry = (CompoundTag) raw;
            var pattern = registry.stream().filter(p -> p.getHashname().equals(entry.getString("Pattern"))).findFirst();
            if (pattern.isPresent()) {
                String key = registry.getKey(pattern.get()).toString();
                String color = DyeColor.byId(entry.getInt("Color")).getName();
                entries.add("{\"pattern\":\"" + key + "\",\"color\":\"" + color + "\"}");
            }
        }
        return new Design(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), "[" + String.join(",", entries) + "]");
    }

    static ItemStack restore(ClanBannerData data, MinecraftServer server) {
        var item = BuiltInRegistries.ITEM.get(new ResourceLocation(data.materialId().toLowerCase(Locale.ROOT)));
        if (!(item instanceof BannerItem)) throw new IllegalArgumentException("The saved banner material is unavailable on this server.");
        var stack = new ItemStack(item);
        var registry = server.registryAccess().registryOrThrow(Registries.BANNER_PATTERN);
        var patterns = new ListTag();
        for (var spec : data.patterns()) {
            var pattern = registry.get(new ResourceLocation(spec.patternId().toLowerCase(Locale.ROOT)));
            DyeColor color = DyeColor.byName(spec.colorId().toLowerCase(Locale.ROOT), null);
            if (pattern == null || color == null) throw new IllegalArgumentException("A saved banner pattern is unavailable on this server.");
            var entry = new CompoundTag();
            entry.putString("Pattern", pattern.getHashname());
            entry.putInt("Color", color.getId());
            patterns.add(entry);
        }
        stack.getOrCreateTagElement("BlockEntityTag").put("Patterns", patterns);
        return stack;
    }
    private BannerCodec() {}
}
