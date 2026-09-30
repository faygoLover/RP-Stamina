package faygolover.rpstamina.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import faygolover.rpstamina.RpStamina;
import faygolover.rpstamina.capability.PlayerStamina;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Загрузка данных: data/&lt;ns&gt;/rpstamina/&lt;категория&gt;/*.json из мода и датапаков,
 * плюс слой «своих» файлов в config/rpstamina_custom/&lt;категория&gt;/*.json (перекрывает
 * одноимённые записи rpstamina:&lt;имя&gt; и добавляет новые). Слой конфига перечитывается командой /stamina reload.
 */
@Mod.EventBusSubscriber(modid = RpStamina.MODID)
public final class DataLoaders {
    public enum Category {
        CONSUMABLES("consumables"), GROUPS("groups"), USE_COSTS("use_costs"), WEAPONS("weapons"), BLOCKS("blocks");

        public final String folder;

        Category(String folder) {
            this.folder = folder;
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /** Слой датапаков (мод + датапаки), сохраняется для повторной сборки и экспорта. */
    private static final Map<Category, Map<ResourceLocation, JsonElement>> RAW = new EnumMap<>(Category.class);

    private DataLoaders() {}

    // ------------------------------------------------------------------ регистрация

    @SubscribeEvent
    public static void onAddListeners(AddReloadListenerEvent event) {
        for (Category c : Category.values()) event.addListener(new Loader(c));
    }

    private static final class Loader extends SimpleJsonResourceReloadListener {
        private final Category category;

        Loader(Category category) {
            super(GSON, "rpstamina/" + category.folder);
            this.category = category;
        }

        @Override
        protected void apply(Map<ResourceLocation, JsonElement> map, ResourceManager manager, ProfilerFiller profiler) {
            RAW.put(category, new LinkedHashMap<>(map));
            rebuild(category);
        }
    }

    // ------------------------------------------------------------------ папка «своих» файлов

    public static Path customDir() {
        return FMLPaths.CONFIGDIR.get().resolve("rpstamina_custom");
    }

    /** Создаёт папку со структурой и README, если её ещё нет. Существующие файлы не трогает. */
    public static void ensureCustomDir() {
        try {
            Path root = customDir();
            for (Category c : Category.values()) Files.createDirectories(root.resolve(c.folder));
            Path readme = root.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, README, StandardCharsets.UTF_8);
            }
            Path example = root.resolve("consumables").resolve("_example_energy_drink.json.txt");
            if (!Files.exists(example)) {
                Files.writeString(example, EXAMPLE, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            RpStamina.LOGGER.warn("[rpstamina] cannot create custom data folder: {}", e.toString());
        }
    }

    /** Перечитать слой конфига поверх уже загруженных данных датапака. */
    public static void reloadCustom() {
        ensureCustomDir();
        for (Category c : Category.values()) rebuild(c);
    }

    /** Записывает в папку конфига копии данных по умолчанию (если файла ещё нет). Возвращает число записанных. */
    public static int exportDefaults() {
        ensureCustomDir();
        int count = 0;
        for (Category c : Category.values()) {
            Map<ResourceLocation, JsonElement> raw = RAW.get(c);
            if (raw == null) continue;
            for (Map.Entry<ResourceLocation, JsonElement> e : raw.entrySet()) {
                if (!RpStamina.MODID.equals(e.getKey().getNamespace())) continue;
                Path file = customDir().resolve(c.folder).resolve(e.getKey().getPath().replace('/', '_') + ".json");
                if (Files.exists(file)) continue;
                try {
                    Files.writeString(file, GSON.toJson(e.getValue()), StandardCharsets.UTF_8);
                    count++;
                } catch (IOException ex) {
                    RpStamina.LOGGER.warn("[rpstamina] cannot write {}: {}", file, ex.toString());
                }
            }
        }
        return count;
    }

    private static Map<ResourceLocation, JsonElement> readCustom(Category category) {
        Map<ResourceLocation, JsonElement> result = new LinkedHashMap<>();
        Path dir = customDir().resolve(category.folder);
        if (!Files.isDirectory(dir)) return result;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                String name = fileName.substring(0, fileName.length() - ".json".length());
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    result.put(new ResourceLocation(RpStamina.MODID, name), JsonParser.parseReader(reader));
                } catch (Exception ex) {
                    RpStamina.LOGGER.warn("[rpstamina] skipped custom file {}: {}", file, ex.toString());
                }
            }
        } catch (IOException e) {
            RpStamina.LOGGER.warn("[rpstamina] cannot read {}: {}", dir, e.toString());
        }
        return result;
    }

    // ------------------------------------------------------------------ сборка

    private static void rebuild(Category category) {
        Map<ResourceLocation, JsonElement> merged = new LinkedHashMap<>(RAW.getOrDefault(category, Map.of()));
        merged.putAll(readCustom(category));
        switch (category) {
            case CONSUMABLES -> StaminaData.setConsumables(parseConsumables(merged));
            case GROUPS -> StaminaData.setGroups(parseGroups(merged));
            case USE_COSTS -> StaminaData.setUseCosts(parseUseCosts(merged));
            case WEAPONS -> StaminaData.setWeapons(parseWeapons(merged));
            case BLOCKS -> StaminaData.setBlocks(parseBlocks(merged));
        }
    }

    private static void each(Map<ResourceLocation, JsonElement> map, BiConsumer<ResourceLocation, JsonObject> fn) {
        map.forEach((id, element) -> {
            try {
                if (element.isJsonArray()) {
                    element.getAsJsonArray().forEach(x -> fn.accept(id, x.getAsJsonObject()));
                } else {
                    fn.accept(id, element.getAsJsonObject());
                }
            } catch (Exception ex) {
                RpStamina.LOGGER.warn("[rpstamina] bad data entry {}: {}", id, ex.toString());
            }
        });
    }

    private static List<StaminaData.Consumable> parseConsumables(Map<ResourceLocation, JsonElement> map) {
        List<StaminaData.Consumable> list = new ArrayList<>();
        each(map, (id, o) -> {
            ItemMatcher m = itemMatcher(id, o);
            if (m == null) return;
            PlayerStamina.Crash crash = null;
            if (o.has("crash") && o.get("crash").isJsonObject()) {
                JsonObject c = o.getAsJsonObject("crash");
                crash = new PlayerStamina.Crash(
                        GsonHelper.getAsInt(c, "duration_ticks", 600),
                        GsonHelper.getAsFloat(c, "regen_multiplier", 0.5f),
                        GsonHelper.getAsInt(c, "delay_ticks", 0));
            }
            String group = o.has("group") ? GsonHelper.getAsString(o, "group") : null;
            list.add(new StaminaData.Consumable(m,
                    GsonHelper.getAsFloat(o, "instant", 0f),
                    Math.max(1, GsonHelper.getAsInt(o, "instant_ticks", 200)),
                    GsonHelper.getAsFloat(o, "passive_per_tick", 0f),
                    GsonHelper.getAsInt(o, "duration_ticks", 0),
                    group,
                    GsonHelper.getAsInt(o, "cooldown_ticks", 0),
                    crash));
        });
        // Записи с NBT проверяются раньше общих (например, вода раньше любого зелья)
        list.sort(Comparator.comparingInt(c -> c.matcher().nbt() == null ? 1 : 0));
        return list;
    }

    private static Map<String, StaminaData.Group> parseGroups(Map<ResourceLocation, JsonElement> map) {
        Map<String, StaminaData.Group> result = new HashMap<>();
        each(map, (id, o) -> result.put(id.getPath(), new StaminaData.Group(
                GsonHelper.getAsFloat(o, "gain", 0.5f),
                GsonHelper.getAsFloat(o, "decay_per_tick", 0.0003f),
                GsonHelper.getAsFloat(o, "max", 4f))));
        return result;
    }

    private static List<StaminaData.UseCost> parseUseCosts(Map<ResourceLocation, JsonElement> map) {
        List<StaminaData.UseCost> list = new ArrayList<>();
        each(map, (id, o) -> {
            ItemMatcher m = itemMatcher(id, o);
            if (m == null) return;
            list.add(new StaminaData.UseCost(m,
                    GsonHelper.getAsFloat(o, "on_start", 0f),
                    GsonHelper.getAsFloat(o, "per_tick", 0f),
                    GsonHelper.getAsInt(o, "hold_after_ticks", Integer.MAX_VALUE),
                    GsonHelper.getAsFloat(o, "hold_per_tick", 0f)));
        });
        list.sort(Comparator.comparingInt(c -> c.matcher().nbt() == null ? 1 : 0));
        return list;
    }

    private static List<StaminaData.Weapon> parseWeapons(Map<ResourceLocation, JsonElement> map) {
        List<StaminaData.Weapon> list = new ArrayList<>();
        each(map, (id, o) -> {
            ItemMatcher m = itemMatcher(id, o);
            if (m == null) return;
            list.add(new StaminaData.Weapon(m,
                    GsonHelper.getAsFloat(o, "hit_multiplier", 1f),
                    GsonHelper.getAsBoolean(o, "ignore", false)));
        });
        list.sort(Comparator.comparingInt(c -> c.matcher().nbt() == null ? 1 : 0));
        return list;
    }

    private static List<StaminaData.BlockDef> parseBlocks(Map<ResourceLocation, JsonElement> map) {
        List<StaminaData.BlockDef> list = new ArrayList<>();
        each(map, (id, o) -> {
            BlockMatcher m = blockMatcher(id, o);
            if (m == null) return;
            list.add(new StaminaData.BlockDef(m, GsonHelper.getAsFloat(o, "multiplier", 1f)));
        });
        return list;
    }

    // ------------------------------------------------------------------ матчеры

    /** null — запись пропускается (предмета нет, например, соответствующий мод не установлен). */
    @Nullable
    private static ItemMatcher itemMatcher(ResourceLocation entryId, JsonObject o) {
        String s = GsonHelper.getAsString(o, "item");
        CompoundTag nbt = null;
        if (o.has("nbt")) {
            JsonElement n = o.get("nbt");
            String text = n.isJsonPrimitive() ? n.getAsString() : n.toString();
            try {
                nbt = TagParser.parseTag(text);
            } catch (CommandSyntaxException e) {
                RpStamina.LOGGER.warn("[rpstamina] {}: bad nbt: {}", entryId, e.getMessage());
                return null;
            }
        }
        if (s.startsWith("#")) {
            return new ItemMatcher(null, TagKey.<Item>create(Registries.ITEM, new ResourceLocation(s.substring(1))), nbt);
        }
        ResourceLocation rl = new ResourceLocation(s);
        if (!ForgeRegistries.ITEMS.containsKey(rl)) {
            RpStamina.LOGGER.debug("[rpstamina] {}: item {} not found, entry skipped", entryId, rl);
            return null;
        }
        return new ItemMatcher(ForgeRegistries.ITEMS.getValue(rl), null, nbt);
    }

    @Nullable
    private static BlockMatcher blockMatcher(ResourceLocation entryId, JsonObject o) {
        String s = GsonHelper.getAsString(o, "block");
        if (s.startsWith("#")) {
            return new BlockMatcher(null, TagKey.<Block>create(Registries.BLOCK, new ResourceLocation(s.substring(1))));
        }
        ResourceLocation rl = new ResourceLocation(s);
        if (!ForgeRegistries.BLOCKS.containsKey(rl)) {
            RpStamina.LOGGER.debug("[rpstamina] {}: block {} not found, entry skipped", entryId, rl);
            return null;
        }
        return new BlockMatcher(ForgeRegistries.BLOCKS.getValue(rl), null);
    }

    // ------------------------------------------------------------------ тексты

    private static final String README = """
            RP Stamina — свои данные (config/rpstamina_custom)
            ==================================================

            Файлы отсюда перекрывают данные мода (одноимённые записи rpstamina:<имя>) и добавляют новые.
            Обычные датапаки тоже работают: data/<namespace>/rpstamina/<категория>/<имя>.json

            Категории (подпапки):
              consumables/  восстанавливающие предметы
              groups/       группы толерантности (кофеин, сахар...)
              use_costs/    траты при использовании предметов (лук, арбалет, трезубец...)
              weapons/      множители ударов для оружия (или "ignore": true)
              blocks/       множители трат на добычу блоков

            Команды (нужны права оператора):
              /stamina export_defaults  — записать сюда копии данных по умолчанию (существующие файлы не трогает)
              /stamina reload           — перечитать эту папку без перезапуска
              (изменения в самом моде/датапаках подхватываются обычным /reload)

            Формат consumables (одна запись на файл; можно и массив записей):
            {
              "item": "minecraft:potion",            // id предмета или "#namespace:тег"
              "nbt": { "Potion": "minecraft:water" }, // необязательно: частичное совпадение NBT
              "instant": 120,                         // мгновенное восстановление (растягивается на instant_ticks)
              "instant_ticks": 200,                   // по умолчанию 200 (10 секунд)
              "passive_per_tick": 0.03,               // добавка к регенерации за тик на время действия
              "duration_ticks": 400,                  // длительность passive_per_tick
              "group": "water",                       // группа толерантности (файл groups/water.json)
              "cooldown_ticks": 0,                    // необязательный жёсткий откат предмета
              "crash": {                              // необязательный откат после действия предмета
                "duration_ticks": 600, "regen_multiplier": 0.5, "delay_ticks": 100
              }
            }

            groups/<имя>.json:  { "gain": 0.6, "decay_per_tick": 0.0002, "max": 4.0 }
              Каждое использование предмета группы повышает толерантность на gain (до max);
              эффективность = 1 / (1 + толерантность); толерантность сама спадает на decay_per_tick.

            use_costs:  { "item": "minecraft:bow", "on_start": 4, "per_tick": 0.3, "hold_after_ticks": 40, "hold_per_tick": 1.2 }
            weapons:    { "item": "#minecraft:axes", "hit_multiplier": 1.3 }
                        { "item": "modid:some_gun", "ignore": true }
            blocks:     { "block": "#minecraft:logs", "multiplier": 1.2 }

            Предметы, которых нет в игре (мод не установлен), пропускаются без ошибки.
            """;

    private static final String EXAMPLE = """
            {
              "item": "examplemod:energy_drink",
              "instant": 250,
              "instant_ticks": 200,
              "passive_per_tick": 0.08,
              "duration_ticks": 900,
              "group": "caffeine",
              "crash": { "duration_ticks": 1200, "regen_multiplier": 0.5, "delay_ticks": 100 }
            }
            """;
}
