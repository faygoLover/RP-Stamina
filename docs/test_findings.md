# Находки

## 15.11.2026 — тесты краёв состояния (план `_family/refactor`, T0.3). Не исправлено.

1. **ИСПРАВЛЕНО 16.11.2026 (1.5.2, решение Алекса 15.11, план T2.3): данные выносливости не копировались при
   смерти и при возврате из Края.** Данные переведены на `PlayerStore` RP Core, подписка `invalidate` убрана, тесты
   `deathResets` и `endReturnKeepsAll` обязательные и проходят. Теперь: возврат из Края сохраняет выносливость,
   измотанность, толерантность и стимуляторы; откат «Второго дыхания» переживает смерть; `reset_on_death = false`
   работает. Как было:
   `capability/StaminaProvider.invalidate()` вызывает `optional.invalidate()` (подписан в `ActionEvents.onAttach`
   через `event.addListener`). Погашенный `LazyOptional` назад не оживает, `reviveCaps()` его не восстанавливает.
   Поэтому в `ActionEvents.onClone` `StaminaProvider.get(event.getOriginal())` возвращает `null`, и `copyFrom` не
   вызывается никогда.
   Что это значит в игре:
   - возврат из Края: выносливость снова полная, измотанность снята, толерантность к предметам и стимуляторы потеряны;
   - смерть: откат «Второго дыхания» (`secondWindUntil`) сбрасывается, черта срабатывает снова сразу после смерти;
   - настройка `reset_on_death = false` не работает: после смерти всё равно всё сбрасывается.
   Проверено тестами `StaminaEdgeTests.deathResets` и `endReturnKeepsAll` — оба падают, помечены `required = false`.
   Починка: убрать `event.addListener(provider::invalidate)` либо пересоздавать `LazyOptional` в `reviveCaps`
   (у остальных модов семейства такой подписки нет, и копирование работает). Уйдёт само при переводе на
   `PlayerStore` (T2.3) — но это изменение поведения, нужно слово Алекса.

2. **После перезахода пропадают:** растянутое восстановление (`pools`), пассивная регенерация от предметов
   (`passives`), стимуляторы (`boosts`), откат допинга (`crashTicks`, `crashMult`). В NBT пишутся только `stamina`,
   `exhausted`, `tolerance`, `secondWindUntil`. Известно с ревью 09.11 («Stamina `pools` и `crashTicks` не в NBT»).
