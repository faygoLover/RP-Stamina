package faygolover.rpstamina;

import com.mojang.logging.LogUtils;
import faygolover.rpstamina.attribute.StaminaAttributes;
import faygolover.rpstamina.capability.PlayerStamina;
import faygolover.rpstamina.data.DataLoaders;
import faygolover.rpstamina.network.NetworkHandler;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RpStamina.MODID)
public class RpStamina {
    public static final String MODID = "rpstamina";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RpStamina() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        StaminaAttributes.ATTRIBUTES.register(modBus);
        modBus.addListener(StaminaAttributes::onAttributeModification);
        modBus.addListener(this::registerCapabilities);
        modBus.addListener(this::commonSetup);

        // SERVER-конфиг (как в rpperks): serverconfig/rpstamina-server.toml в папке мира
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, Config.SPEC);
        // CLIENT-конфиг: положение и внешний вид полоски (config/rpstamina-client.toml)
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerStamina.class);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(NetworkHandler::register);
        event.enqueueWork(DataLoaders::ensureCustomDir);
        event.enqueueWork(() -> {
            faygolover.rpcore.api.RpCoreAPI.registerModule(MODID, "выносливость: траты, восстановление, предметы");
            // Розетка выносливости RP Core: другие моды спрашивают выносливость и вешают модификаторы через неё.
            faygolover.rpcore.api.RpCoreAPI.registerStamina(new faygolover.rpstamina.integration.CoreStamina());
            faygolover.rpstamina.command.StaminaCommand.describe();
        });
        LOGGER.info("RP Stamina loaded.");
    }
}
