# RP Stamina

Выносливость для RP-сервера (`rpstamina`): траты, плавное восстановление, предметы с привыканием, измотанность. Семейство RP/FL/S5, Forge 1.20.1, Java 17. Отвечать по-русски.

## Прежде чем что-то добавлять
1. `../_family/FAMILY_MAP.md`, раздел 3 «Куда это положить» — нет ли готового механизма в RP Core или другого мода для этой задачи.
2. `docs/MAP.md`, раздел 4 — рецепт для твоего случая и образец, с которого списать.
3. `docs/INDEX.md` — поиск по слову: нет ли уже такого класса, события, команды.

Новый класс, пакет, пакет сети, команда, ключ конфига или поле состояния — обнови `docs/MAP.md` в том же
коммите и пересоздай указатель: `python ../_family/tools/mapgen.py "RP Stamina"`.

## Нельзя
- Менять ключ данных игрока `rpstamina:stamina` и имена полей в `PlayerStamina.save/load`, ключи `rpstamina-server.toml`, id источников трат в `core/Source`, атрибуты `rpstamina:*`.
- Ломать `api/StaminaAPI` и `StaminaConsumeEvent`: ими пользуется RP Stamina Sports.
- Путать `capability.StaminaProvider` (данные игрока) с `rpcore.api.StaminaProvider` (розетка).
- Прямые импорты и отражение в другие моды семейства — только через RP Core (исключения: `../_family/links_allow.txt`).
- Push, выпуск сборки, правки живого сервера — только по прямому слову Алекса. Коммиты локальные.

## Команды
```
gradlew.bat build                                        # jar в build/libs
python ../_family/family.py verify --tests "RP Stamina"   # компиляция, связи, игровые тесты (5)
```
JDK 17: `JAVA_HOME="C:/Program Files/Eclipse Adoptium/jdk-17.0.14.7-hotspot"`. Игровые тесты — один прогон в конце задачи.

## Где что
- `docs/MAP.md` — устройство, потоки работы, рецепты, данные, ловушки.
- `docs/INDEX.md` — указатель кода (создаёт скрипт, руками не править).
- `docs/test_findings.md` — находки.
- Общие правила работы: `../_handover/06_how_to_work.md`.
