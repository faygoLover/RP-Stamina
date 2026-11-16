# RP Stamina — карта

Версия на момент написания: 1.5.2 (16.11.2026). Факты по классам, событиям, пакетам — `docs/INDEX.md`.

## 1. Что делает и чего не делает

Выносливость игрока: траты на бег, прыжки, плавание, лазание, копание, удары, щит и использование предметов;
медленное плавное восстановление (быстрее сидя, медленнее голодным); измотанность на нуле; предметы, которые
возвращают выносливость, с привыканием (толерантность), стимуляторы и откат после них; вес брони; возраст.
Полоска — элемент общего HUD RP Core.

За этим — не сюда:
- усталость мышц и тренировки — RP Stamina Sports (аддон, слушает `StaminaConsumeEvent`);
- расход калорий от нагрузки — мод питания (получает `ExertionEvent` через `RpCoreAPI.exertion`);
- кофеин, алкоголь, вода в предметах — общая база `Consumables` RP Core;
- другим модам ходить сюда напрямую не нужно: розетка `RpCoreAPI.stamina()`.

## 2. Как течёт работа

**Трата.** событие Forge (`events/ActionEvents`: прыжок, удар, блок сломан, щит, использование предмета; бег,
плавание, лазание — в тике) → `core/StaminaManager.consume(игрок, данные, Source, база)` → множители категории
(атрибут `cost_multiplier` и др.) и ноши (вес брони) → событие `api/StaminaConsumeEvent` (можно отменить или
изменить) → `spend` → на нуле: «Второе дыхание» (черта) или измотанность (`enterExhausted`) →
`RpCoreAPI.exertion` (расход калорий) и `practice` (навык).

**Тик.** `ActionEvents.onPlayerTick` → `StaminaManager.tickStart` / `tickEnd`: непрерывные траты, восстановление
(`tickRegen`: поза сидя или сон — быстрее, голод — медленнее), предметы и стимуляторы (`tickItems`),
толерантность (`tickTolerance`), возраст (`applyAge`), замедление измотанного (`applySlow`) → `sync`.

**Клиенту.** `StaminaManager.sync` шлёт `network/SyncPacket` не каждый тик, а когда значение ушло от того, что
клиент предсказал сам (скорость изменения + редкая подстройка) → `client/ClientStamina` → `client/StaminaHud`.
Можно ли бежать — `mixin/LocalPlayerMixin` по клиентской копии.

**Предмет съеден.** `RpConsumeEvent` (RP Core: доля предмета) → `ActionEvents.onConsume` →
`StaminaManager.onConsumeItem`: правило из датапака (`data/StaminaData`), привыкание, стимулятор и его откат.

**Прокрутка времени.** `RpTimeSkipEvent` → `events/TimeEvents` → `StaminaManager.elapse`.

**Данные игрока.** `capability/StaminaProvider.register()` описывает `PlayerStamina` для RP Core (`PlayerStore`):
Core сохраняет, копирует при возрождении (`copyFrom(old, сброс)`: при смерти сброс, если `reset_on_death`), на
входе и после возрождения зовёт `StaminaManager.applyBaseMax`. На фальшивых игроков не вешается.

**Данные механики.** `data/DataLoaders` читает `data/<ns>/rpstamina/<категория>/*.json` из мода и датапаков плюс
слой своих файлов в `config/rpstamina_custom/` → `data/StaminaData`. Перечитывается на `/reload` и `/rpstamina reload`.

## 3. Карта пакетов

| Пакет | За что отвечает | С чего начать читать |
|---|---|---|
| `rpstamina` | вход мода, серверный и клиентский конфиг | `RpStamina`, `Config`, `ClientConfig` |
| `core` | вся серверная механика и перечень источников трат | `StaminaManager` (разделы помечены), `Source` |
| `capability` | состояние игрока и его описание для RP Core | `PlayerStamina`, `StaminaProvider` |
| `events` | подписки: действия игрока, перечитывание конфига, время RP | `ActionEvents` |
| `data` | правила из датапака: блоки, предметы, оружие, цены использования, группы | `DataLoaders`, `StaminaData`; сопоставление — `ItemMatcher`, `BlockMatcher` |
| `api` | вход для других модов и события трат и восстановления | `StaminaAPI`, `StaminaConsumeEvent`, `StaminaRegenEvent` |
| `attribute` | атрибуты `rpstamina:max_stamina`, множители восстановления и трат | `StaminaAttributes` |
| `integration` | розетка выносливости RP Core | `CoreStamina` |
| `command` | `/rpstamina` | `StaminaCommand` |
| `network` | один пакет клиенту | `SyncPacket` |
| `client` | клиентская копия и полоска | `ClientStamina`, `StaminaHud` |
| `mixin` | запрет бега на нуле на клиенте | `LocalPlayerMixin` |
| `gametest` | игровые тесты состояния | `StaminaEdgeTests` |

## 4. Хочу добавить… — рецепты

| Что | Где | Образец | Не забыть |
|---|---|---|---|
| новый источник траты | значение в `core/Source` (категория, учёт брони, непрерывная или разовая) + вызов `StaminaManager.consume` из события в `events/ActionEvents` + число в `Config` | `SHIELD_BLOCK` → `ActionEvents.onShieldBlock` | id источника уходит в `StaminaConsumeEvent` — его читают другие моды, не переименовывать |
| предмет, который возвращает выносливость, стимулятор | JSON в `data/rpstamina/rpstamina/consumables/` | любой файл оттуда | кода не нужно; на живом сервере — свой слой `config/rpstamina_custom/` |
| цену копания блока, удара оружием, использования предмета | JSON в `blocks/`, `weapons/`, `use_costs/`, общие наборы — `groups/` | файлы там же | сопоставление по id или `#тегу` |
| новую категорию данных | `data/DataLoaders` (загрузчик) + поле в `data/StaminaData` | `use_costs` | `export_defaults` (отладочная команда) |
| настройку механики | `Config` (обычный `ForgeConfigSpec`, помощники `d`, `i`, `b`) | `quick_rest_sit_multiplier` | описание по-русски |
| модификатор от другого мода | не здесь: `RpCoreAPI.stamina()` → `StaminaProvider` (розетка), атрибуты `StaminaAttributes` | `integration/CoreStamina` | — |
| новое поле состояния | `capability/PlayerStamina`: поле, `save`, `load`, `copyFrom` | `secondWindUntil` | тест `StaminaEdgeTests`; см. находку о несохраняемых полях в §9 |
| команду | `command/StaminaCommand` | `refill` | `RpCoreAPI.registerCommand` в `RpStamina.commonSetup` |

## 5. Данные

| Что | Где хранится | Ключ | Что при смерти | Как попадает клиенту |
|---|---|---|---|---|
| выносливость игрока | у игрока, `PlayerStore` RP Core | `rpstamina:stamina` | при `reset_on_death` — выносливость и измотанность сбрасываются; откат «Второго дыхания» остаётся; из Края — всё | `SyncPacket` |
| правила механики | датапак + `config/rpstamina_custom/` | — | — | не нужны клиенту |

В NBT пишутся только `stamina`, `exhausted`, `tolerance`, `secondWindUntil`. Ключи не переименовывать.

## 6. Связи

- Берёт из Core: `nutrition()` (голод замедляет восстановление), `posture` и `postureSleeping`, `profile`
  (возраст), `hasTrait`, `practice`, `exertion`, `debugCommands`; события `RpConsumeEvent`, `RpTimeSkipEvent`;
  данные — `PlayerStore`; полоска — общий HUD.
- Даёт в Core: розетку `StaminaProvider` (`integration/CoreStamina`, регистрация в `RpStamina.commonSetup`).
- RP Perks — только проверка «стоит ли» (`StaminaManager.perksLoaded`): классов Perks мод не трогает.
- RP Stamina Sports зависит от этого мода напрямую: `StaminaConsumeEvent` и `StaminaAPI` для него — внешний API,
  менять осторожно.

## 7. Конфиги

- `serverconfig/rpstamina-server.toml` — `Config` (47 ключей), обычный `ForgeConfigSpec`; перечитывается на ходу
  (`events/LifecycleEvents`), после чего всем игрокам заново ставится база максимума.
- `config/rpstamina-client.toml` — `ClientConfig`: вид полоски. Положение полоски — в общем редакторе `/rphud`.
- `config/rpstamina_custom/` — свои правила поверх датапака.

## 8. Сеть

Канал `rpstamina:main`, протокол `1`, один пакет сервер → клиент (`SyncPacket`). Клиент ничего не присылает.
Изменил поля `SyncPacket` — подними версию протокола в `NetworkHandler`.

## 9. Ловушки

- **Имя `StaminaProvider` занято дважды:** `faygolover.rpstamina.capability.StaminaProvider` — данные игрока;
  `faygolover.rpcore.api.StaminaProvider` — розетка Core, её реализует `CoreStamina`. Не перепутать при импорте.
- **Клиент предсказывает полоску сам.** Разовая трата доходит пакетом, непрерывная — как «скорость». Новая
  непрерывная трата должна быть помечена `continuous` в `Source`, иначе полоска будет дёргаться.
- **После перезахода пропадают** растянутое восстановление (`pools`), пассивная регенерация (`passives`),
  стимуляторы (`boosts`) и откат допинга (`crashTicks`, `crashMult`): в NBT их нет. Известная находка
  (`docs/test_findings.md`, п. 2), решения о починке нет.
- **До 1.5.2 данные не копировались при смерти и возврате из Края** (свой `invalidate` гасил `LazyOptional`
  навсегда). Починено переводом на `PlayerStore`; подписку `invalidate` не возвращать.
- `applyBaseMax` вызывается на входе и возрождении, **не** при смене измерения — атрибуты игрока там сохраняются.
- Тег `rpstamina.force` (`StaminaManager.FORCE_TAG`) включает трату и в режимах, где мод обычно молчит: им
  пользуется RP Stamina Sports.

## 10. Тесты

`python ../_family/family.py verify --tests "RP Stamina"` — 5 игровых тестов (`StaminaEdgeTests`): запись и чтение,
ветка `ForgeCaps.rpstamina:stamina`, смерть со сбросом, возврат из Края, перезаход. Траты и восстановление
тестами не покрыты.

## 11. Документы в docs/

| Файл | Состояние |
|---|---|
| `MAP.md`, `INDEX.md` | действуют |
| `test_findings.md` | действует: п. 1 исправлен 16.11, п. 2 открыт |

Дизайн выносливости, еды и навыков исторически лежит в `RP Stamina Sports/docs/01`–`08`.
Предложение по делению `StaminaManager` (710 строк) — `../_family/refactor/STATUS.md`, строка T4.6; ждёт решения.
