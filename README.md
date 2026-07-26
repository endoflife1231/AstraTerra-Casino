# AstraTerra Casino 0.9.5

Мод казино для **Minecraft 1.20.1**, **Fabric** и **Java 17**. Версия 0.9.5 добавляет графическое «Колесо экспедиции» с серверным определением результата, адаптивным интерфейсом, восстановлением активного вращения и защитой от повторной выплаты.

## Скачать

Готовые файлы находятся в каталоге [`releases/v0.9.5`](releases/v0.9.5):

- `astraterra-casino-0.9.5+mc1.20.1.jar` — мод для клиента и сервера;
- `AstraTerra-Casino-0.9.5-release.zip` — полный релизный комплект;
- `astraterra-casino-0.9.5-sources.zip` — архив исходников;
- `SHA256SUMS.txt` — контрольные суммы.

## Установка

1. Полностью закройте Minecraft и сервер.
2. Удалите старые версии `astraterra-casino` из папки `mods`.
3. Установите `astraterra-casino-0.9.5+mc1.20.1.jar` на клиент и сервер.
4. Убедитесь, что у всех участников установлена одна версия мода.
5. Конфигурацию FTB Quests заменять не требуется.

## Зависимости

- Fabric Loader `>=0.15.3`;
- Fabric API;
- Minecraft `1.20.1`;
- Java `17+`;
- Numismatic Overhaul `>=0.2.17`;
- owo-lib `>=0.11.2`;
- FTB Quests `>=2001.1.0`;
- FTB Library;
- FTB Teams.

## Основные изменения 0.9.5

- графическое колесо из 12 секторов;
- FPS-независимая анимация с quintic ease-out;
- сервер заранее фиксирует сектор, ставку и выплату;
- уникальный `spinId` и защита от повторной выплаты;
- восстановление вращения после закрытия интерфейса;
- адаптивные Large, Medium и Compact layout;
- звуки, подсветка результата и короткие частицы;
- сетевые протоколы S2C `6` и C2S `5`.

Полный список: [`CHANGELOG.md`](CHANGELOG.md).

## Клиентские параметры

```text
-Dastraterra.casino.wheel.disableAnimation=true
-Dastraterra.casino.wheel.reducedAnimation=true
-Dastraterra.casino.wheel.disableSounds=true
-Dastraterra.casino.wheel.particles=OFF|LOW|NORMAL
```

## Исходный код и сборка

Исходный код версии 0.9.5 опубликован в `src/`. Текущий архив **не содержит Gradle Wrapper, `build.gradle` и Fabric Loom-конфигурацию**, поэтому воспроизводимая сборка одной командой из этого репозитория пока не настроена. Подробности: [`BUILD-NOTES-RU.md`](BUILD-NOTES-RU.md).

Готовый JAR был собран непосредственно в intermediary namespace с Java 17. Автономные проверки описаны в [`docs/validation/VALIDATION-0.9.5-RU.txt`](docs/validation/VALIDATION-0.9.5-RU.txt). Полный запуск внутри конкретной сборки модов остаётся интеграционным тестом.

## Совместимость

Версии 0.9.4 и 0.9.5 нельзя смешивать на клиенте и сервере из-за изменения сетевого протокола.

## Лицензия

Код и специально созданные ресурсы распространяются по лицензии MIT. См. [`LICENSE`](LICENSE) и [`ASSET-LICENSES-RU.md`](ASSET-LICENSES-RU.md).
