package faygolover.rpstamina.integration;

import faygolover.rpcore.api.StaminaProvider;
import faygolover.rpstamina.api.StaminaAPI;
import faygolover.rpstamina.attribute.StaminaAttributes;
import faygolover.rpstamina.core.StaminaManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.player.Player;

/**
 * Розетка выносливости RP Core: через неё другие моды семейства спрашивают выносливость и вешают свои
 * модификаторы на атрибуты Stamina, не ища их по имени. Вся механика — здесь, в RP Stamina.
 */
public final class CoreStamina implements StaminaProvider {
    @Override
    public float stamina(Player player) {
        return player instanceof ServerPlayer sp ? StaminaAPI.get(sp) : 0f;
    }

    @Override
    public float maxStamina(Player player) {
        return player instanceof ServerPlayer sp ? StaminaManager.max(sp) : 0f;
    }

    @Override
    public boolean exhausted(Player player) {
        return player instanceof ServerPlayer sp && StaminaAPI.isExhausted(sp);
    }

    @Override
    public void spend(Player player, float amount, ResourceLocation source) {
        if (player instanceof ServerPlayer sp) StaminaAPI.consume(sp, amount, source);
    }

    @Override
    public void restore(Player player, float amount, ResourceLocation source) {
        if (amount > 0f && player instanceof ServerPlayer sp) StaminaAPI.add(sp, amount);
    }

    @Override
    public Attribute attribute(Stat stat) {
        return switch (stat) {
            case MAX -> StaminaAttributes.MAX_STAMINA.get();
            case REGEN -> StaminaAttributes.REGEN_MULTIPLIER.get();
            case COST -> StaminaAttributes.COST_MULTIPLIER.get();
            case MOVE_COST -> StaminaAttributes.MOVE_COST_MULTIPLIER.get();
            case SWIM_COST -> StaminaAttributes.SWIM_COST_MULTIPLIER.get();
            case MINE_COST -> StaminaAttributes.MINE_COST_MULTIPLIER.get();
            case MELEE_COST -> StaminaAttributes.MELEE_COST_MULTIPLIER.get();
            case ARMOR_BURDEN -> StaminaAttributes.ARMOR_BURDEN.get();
        };
    }
}
