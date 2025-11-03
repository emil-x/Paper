package io.papermc.paper.enchantment;

import io.papermc.paper.registry.PaperRegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.TypedKey;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.data.PaperEnchantmentRegistryEntry;
import io.papermc.paper.registry.data.util.Conversions;
import io.papermc.paper.registry.set.NamedRegistryKeySetImpl;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.minecraft.advancements.critereon.DamageSourcePredicate;
import net.minecraft.advancements.critereon.EntityFlagsPredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.TagPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.DamageImmunity;
import net.minecraft.world.item.enchantment.effects.ReplaceDisk;
import net.minecraft.world.item.enchantment.effects.condition.DamageSourceCondition;
import net.minecraft.world.level.GameEvent;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.providers.BlockStateProvider;
import net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.AllOfCondition;
import net.minecraft.world.level.storage.loot.predicates.InvertedLootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;

public final class MczoneEnchantments {

    private static final TypedKey<org.bukkit.enchantments.Enchantment> LAVA_WALKER_KEY =
        TypedKey.create(RegistryKey.ENCHANTMENT, Key.key("mczone:lava_walker"));

    private MczoneEnchantments() {
    }

    public static void bootstrap() {
        final Conversions conversions = BuiltInRegistries.STATIC_ACCESS_CONVERSIONS;
        final HolderLookup.RegistryLookup<Item> itemLookup = conversions.lookup().lookupOrThrow(Registries.ITEM);
        final HolderLookup.RegistryLookup<Enchantment> enchantmentLookup = conversions.lookup().lookupOrThrow(Registries.ENCHANTMENT);
        final HolderSet.Named<Item> footArmor = itemLookup.getOrThrow(ItemTags.FOOT_ARMOR_ENCHANTABLE);
        final HolderSet.Named<Enchantment> bootExclusive = enchantmentLookup.getOrThrow(EnchantmentTags.EXCLUSIVE_SET_BOOTS);

        PaperRegistryAccess.instance()
            .getWritableRegistry(RegistryKey.ENCHANTMENT)
            .createApiWritableRegistry(conversions)
            .registerWith(LAVA_WALKER_KEY, factory -> {
                final PaperEnchantmentRegistryEntry.PaperBuilder builder = (PaperEnchantmentRegistryEntry.PaperBuilder) factory.empty();

                builder.description(Component.text("Лаваход"));
                builder.supportedItems(new NamedRegistryKeySetImpl<>(
                    io.papermc.paper.registry.keys.tags.ItemTypeTagKeys.ENCHANTABLE_FOOT_ARMOR,
                    footArmor
                ));
                builder.anvilCost(4);
                builder.maxLevel(2);
                builder.weight(2);
                builder.minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(10, 10));
                builder.maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(25, 10));
                builder.activeSlots(List.of(org.bukkit.inventory.EquipmentSlotGroup.FEET));
                builder.exclusiveWith(new NamedRegistryKeySetImpl<>(
                    io.papermc.paper.registry.keys.tags.EnchantmentTagKeys.EXCLUSIVE_SET_BOOTS,
                    bootExclusive
                ));
                builder.effects(createEffects(footArmor, bootExclusive));
            });
    }

    private static DataComponentMap createEffects(final HolderSet<Item> supportedItems, final HolderSet<Enchantment> exclusiveSet) {
        final Enchantment.Builder builder = Enchantment.enchantment(
            Enchantment.definition(
                supportedItems,
                2,
                2,
                Enchantment.dynamicCost(10, 10),
                Enchantment.dynamicCost(25, 10),
                4,
                EquipmentSlotGroup.FEET
            )
        ).exclusiveWith(exclusiveSet).withEffect(
            EnchantmentEffectComponents.DAMAGE_IMMUNITY,
            DamageImmunity.INSTANCE,
            DamageSourceCondition.hasDamageSource(
                DamageSourcePredicate.Builder.damageType()
                    .tag(TagPredicate.is(DamageTypeTags.BURN_FROM_STEPPING))
                    .tag(TagPredicate.isNot(DamageTypeTags.BYPASSES_INVULNERABILITY))
            )
        ).withEffect(
            EnchantmentEffectComponents.LOCATION_CHANGED,
            new ReplaceDisk(
                new LevelBasedValue.Clamped(LevelBasedValue.perLevel(3.0F, 1.0F), 0.0F, 16.0F),
                LevelBasedValue.constant(1.0F),
                new Vec3i(0, -1, 0),
                Optional.of(
                    BlockPredicate.allOf(
                        BlockPredicate.matchesTag(new Vec3i(0, 1, 0), BlockTags.AIR),
                        BlockPredicate.matchesBlocks(Blocks.LAVA),
                        BlockPredicate.matchesFluids(Fluids.LAVA),
                        BlockPredicate.unobstructed()
                    )
                ),
                BlockStateProvider.simple(Blocks.OBSIDIAN),
                Optional.of(GameEvent.BLOCK_PLACE)
            ),
            AllOfCondition.allOf(
                LootItemEntityPropertyCondition.hasProperties(
                    LootContext.EntityTarget.THIS,
                    EntityPredicate.Builder.entity()
                        .flags(EntityFlagsPredicate.Builder.flags().setOnGround(true))
                ),
                InvertedLootItemCondition.invert(
                    LootItemEntityPropertyCondition.hasProperties(
                        LootContext.EntityTarget.THIS,
                        EntityPredicate.Builder.entity().vehicle(EntityPredicate.Builder.entity())
                    )
                )
            )
        );

        return builder.build().effects();
    }
}
