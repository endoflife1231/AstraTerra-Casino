# AstraTerra Casino

[English](README.md) · [Русский](README.ru.md)

Сервер-авторитетный мод казино для **Minecraft 1.20.1**, **Fabric** и **Java 17**. Версия 0.9.5 включает блэкджек, приватные комнаты покера и дурака, а также анимированное «Колесо экспедиции» с валютой Numismatic Overhaul и статистикой FTB Quests.

## Скачать

Готовый мод находится в [GitHub Releases](https://github.com/endoflife1231/AstraTerra-Casino/releases). Игрокам нужен только файл `astraterra-casino-0.9.5+mc1.20.1.jar`; автоматически созданные архивы “Source code” предназначены для разработчиков.

## Установка

1. Установите Minecraft 1.20.1, Java 17 и Fabric Loader 0.15.3 или новее.
2. Установите обязательные зависимости из списка ниже.
3. Удалите старые версии `astraterra-casino` из папки `mods`.
4. Поместите `astraterra-casino-0.9.5+mc1.20.1.jar` в папку `mods` на клиенте и сервере.
5. Убедитесь, что на всех клиентах и сервере установлена одна версия мода.

Существующую конфигурацию FTB Quests заменять не требуется.

## Обязательные зависимости

- [Fabric API](https://modrinth.com/mod/fabric-api)
- [Numismatic Overhaul](https://modrinth.com/mod/numismatic-overhaul) 0.2.17 или новее
- [owo-lib](https://modrinth.com/mod/owo-lib) 0.11.2 или новее
- FTB Quests 2001.1.0 или новее
- FTB Library
- FTB Teams

## Возможности

- сервер определяет результаты игр и выплаты;
- анимированное колесо из 12 секторов;
- блэкджек, покер и дурак;
- приватные сетевые комнаты;
- адаптивный интерфейс казино;
- русская и английская локализация;
- защита от повторной выплаты и восстановление вращения;
- интеграция статистики с FTB Quests.

Полный список изменений версии 0.9.5: [CHANGELOG.md](CHANGELOG.md).

## Клиентские параметры

```text
-Dastraterra.casino.wheel.disableAnimation=true
-Dastraterra.casino.wheel.reducedAnimation=true
-Dastraterra.casino.wheel.disableSounds=true
-Dastraterra.casino.wheel.particles=OFF|LOW|NORMAL
```

## Исходный код и сборка

Java-код, ресурсы и self-test опубликованы в папке [`src`](src). Переданный снимок исходников 0.9.5 использует intermediary-имена классов Minecraft и не содержит исходной конфигурации Gradle/Loom. Поэтому репозиторий открывает и документирует исходный код, но пока **не обеспечивает поддерживаемую воспроизводимую сборку одной командой**. Подробнее: [BUILD-NOTES-RU.md](BUILD-NOTES-RU.md) и [результаты проверки](docs/VALIDATION-0.9.5-RU.txt).

Файл мода — Java-архив `.jar`, а не Windows-библиотека `.dll`.

## Совместимость

Версии 0.9.4 и 0.9.5 нельзя смешивать между клиентами и сервером: их сетевые протоколы различаются.

## Лицензия

Код и оригинальные ресурсы проекта распространяются по [лицензии MIT](LICENSE). Информация о сторонних ресурсах приведена в [ASSET-LICENSES-RU.md](ASSET-LICENSES-RU.md).
