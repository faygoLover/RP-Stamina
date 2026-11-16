# RP Stamina — указатель кода

Создано `python _family/tools/mapgen.py`, версия мода 1.5.2-1.20.1-forge.
**Руками не править**: пересоздаётся скриптом. Смысл, потоки работы и рецепты — в `docs/MAP.md`.
Разбор по шаблонам, без компиляции: необычно оформленная регистрация может отсутствовать в списках.

Всего: 28 файлов, 2832 строк Java.

## 1. Пакеты

| Пакет | Файлов | Строк |
|---|---:|---:|
| `faygolover.rpstamina` | 3 | 289 |
| `faygolover.rpstamina.api` | 3 | 114 |
| `faygolover.rpstamina.attribute` | 1 | 59 |
| `faygolover.rpstamina.capability` | 2 | 193 |
| `faygolover.rpstamina.client` | 4 | 232 |
| `faygolover.rpstamina.command` | 1 | 162 |
| `faygolover.rpstamina.core` | 2 | 750 |
| `faygolover.rpstamina.data` | 4 | 506 |
| `faygolover.rpstamina.events` | 3 | 201 |
| `faygolover.rpstamina.gametest` | 1 | 164 |
| `faygolover.rpstamina.integration` | 1 | 56 |
| `faygolover.rpstamina.mixin` | 1 | 26 |
| `faygolover.rpstamina.network` | 2 | 80 |

## 2. Классы

### `faygolover.rpstamina`
- `ClientConfig`, 76 — Клиентский конфиг: полоска выносливости.
- `Config`, 168 — Серверный конфиг механики.
- `RpStamina`, 45 — *без описания*

### `faygolover.rpstamina.api`
- `StaminaAPI`, 50 — Публичный API для других модов (например, rpperks).
- `StaminaConsumeEvent`, 36 — Игровая шина Forge.
- `StaminaRegenEvent`, 28 — Игровая шина Forge.

### `faygolover.rpstamina.attribute`
- `StaminaAttributes`, 59 — Атрибуты для интеграции с перками (rpperks и др.).

### `faygolover.rpstamina.capability`
- `PlayerStamina`, 141 — Состояние выносливости игрока (только сервер).
- `StaminaProvider`, 52 — Данные выносливости игрока: capability и доступ к ней.

### `faygolover.rpstamina.client`
- `ClientEvents`, 32 — Клиентская логика (шина Forge): продвижение полоски между пакетами.
- `ClientModEvents`, 21 — Клиентские события мод-шины: регистрация оверлея.
- `ClientStamina`, 58 — Клиентская копия состояния.
- `StaminaHud`, 121 — Минималистичная полоска выносливости.

### `faygolover.rpstamina.command`
- `StaminaCommand`, 162 — /rpstamina get|set|add|refill|reload (нужны права оператора); отладочные export_defaults и debug — только если в конфиге RP Core включены отладочные команды (commands.debug_commands).

### `faygolover.rpstamina.core`
- `Source` (enum), 40 — Источники трат (id попадает в StaminaConsumeEvent).
- `StaminaManager`, 710 — Вся серверная логика выносливости.

### `faygolover.rpstamina.data`
- `BlockMatcher` (record), 15 — Совпадение блока: по id или по тегу (#namespace:tag).
- `DataLoaders`, 372 — Загрузка данных: data/&lt;ns&gt;/rpstamina/&lt;категория&gt;/*.json из мода и датапаков, плюс слой «своих» файлов в config/rpstamina_custom/&lt;категория&gt;/*.json (перекрывает одноимённые записи rpstamina:&lt;имя&gt; и добавляет…
- `ItemMatcher` (record), 23 — Совпадение предмета: по id или по тегу (#namespace:tag), опционально с частичным сравнением NBT.
- `StaminaData`, 96 — Загруженные данные датапака (только сервер).

### `faygolover.rpstamina.events`
- `ActionEvents`, 147 — Игровые события, из которых складывается механика выносливости (шина Forge).
- `LifecycleEvents`, 27 — События мод-шины: перечитывание конфига на работающем сервере.
- `TimeEvents`, 27 — Время RP (RP Core, /rptime).

### `faygolover.rpstamina.gametest`
- `StaminaEdgeTests`, 164 — Края состояния выносливости: запись и чтение, смерть, возврат из Края, перезаход.

### `faygolover.rpstamina.integration`
- `CoreStamina`, 56 — Розетка выносливости RP Core: через неё другие моды семейства спрашивают выносливость и вешают свои модификаторы на атрибуты Stamina, не ища их по имени.

### `faygolover.rpstamina.mixin`
- `LocalPlayerMixin`, 26 — Можно ли бежать (ревью 11.10, п.

### `faygolover.rpstamina.network`
- `NetworkHandler`, 26 — *без описания*
- `SyncPacket`, 54 — Сервер → клиент.

Классов без описания: 2 из 28.

## 3. Подписки на события

| Событие | Кто слушает |
|---|---|
| `AddReloadListenerEvent` | `DataLoaders.onAddListeners` |
| `AttackEntityEvent` | `ActionEvents.onAttack` |
| `BlockEvent.BreakEvent` | `ActionEvents.onBlockBreak` |
| `ClientPlayerNetworkEvent.LoggingOut` | `ClientEvents.onLogout` |
| `EntityAttributeModificationEvent` | `StaminaAttributes.onAttributeModification` |
| `FMLCommonSetupEvent` | `RpStamina.commonSetup` |
| `LivingEntityUseItemEvent.Start` | `ActionEvents.onUseStart` |
| `LivingEntityUseItemEvent.Tick` | `ActionEvents.onUseTick` |
| `LivingEquipmentChangeEvent` | `ActionEvents.onEquipmentChange` |
| `LivingEvent.LivingJumpEvent` | `ActionEvents.onJump` |
| `LivingHurtEvent` | `ActionEvents.onHurt` |
| `ModConfigEvent.Reloading` | `LifecycleEvents.onConfigReload` |
| `PlayerEvent.BreakSpeed` | `ActionEvents.onBreakSpeed` |
| `RegisterCommandsEvent` | `StaminaCommand.onRegister` |
| `RpTimeSkipEvent` | `TimeEvents.onTimeSkip` |
| `ShieldBlockEvent` | `ActionEvents.onShieldBlock` |
| `TickEvent.ClientTickEvent` | `ClientEvents.onClientTick` |
| `TickEvent.PlayerTickEvent` | `ActionEvents.onPlayerTick` |
| `faygolover.rpcore.api.event.RpConsumeEvent` | `ActionEvents.onConsume` |
| `net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent` | `ClientModEvents.onClientSetup` |

## 4. Сеть

Канал: `faygolover/rpstamina/network/NetworkHandler.java`, имя `main`, версия протокола `1`.
- `SyncPacket` (направление не указано в регистрации)

Классы с обработчиками пакетов (1): `SyncPacket`.

## 5. Команды

- `faygolover/rpstamina/command/StaminaCommand.java`: `rpstamina` `get` `set` `add` `refill` `reload` `export_defaults` `debug`

## 6. Данные

- capability: `StaminaProvider`, ключ `rpstamina:stamina`
- свои файлы на диске: `DataLoaders`

## 7. Конфиги

- server: `Config`, вызовов define 3
- client: `ClientConfig`, вызовов define 10

## 8. Что берёт из RP Core и что ему даёт

Методы `RpCoreAPI` (сколько вызовов): `Posture` 1, `debugCommands` 2, `exertion` 1, `hasTrait` 2, `nutrition` 1, `nutritionHandles` 2, `posture` 1, `postureSleeping` 1, `practice` 1, `profile` 1, `registerCommand` 7, `registerModule` 1, `registerStamina` 1, `staminaRestored` 1.

Реализует розетки: StaminaProvider ← `CoreStamina`.

События Core: `RpConsumeEvent`, `RpTimeSkipEvent`.

## 9. Связи с другими модами

Проверки `isLoaded`: `rpperks` (StaminaManager); `rpstamina` (StaminaAPI).

Пакеты других модов семейства в коде: `rpcore` — 9 файлов.

Миксины (1):
- `LocalPlayerMixin` (client) → LocalPlayer.class

## 10. Зарегистрированные id

- `faygolover/rpstamina/attribute/StaminaAttributes.java` (1): `max_stamina`
