# Сборка AstraTerra Casino 0.9.5

## Изменённые компоненты

- `CasinoEngine`: серверная модель spin, идемпотентность и серверный tick завершения.
- `CasinoViewState` / `CasinoPacket`: protocol 6 и структурированные wheel-поля.
- `CasinoRequest`: protocol 5.
- `CasinoScreen`: адаптивный `WheelRenderer` и блокировка повторного запуска.
- `WheelSector`: рациональные множители, веса и редкости.
- `WheelMath`: целевой угол и FPS-независимое easing.
- `WheelSoundController`: звуки через SoundManager.
- `WheelClientOptions`: сокращённая анимация, отключение звука и уровни частиц.

## Ограничение сборочного окружения

Production JAR собран непосредственно в intermediary namespace с Java 17, как и предыдущие версии проекта. Полноценный Fabric Loom `runClient` в этой среде не выполнялся.

Предоставленный архив исходников не содержит Gradle Wrapper, `build.gradle` и Fabric Loom-конфигурацию, поэтому воспроизводимая сборка одной командой из репозитория пока не настроена.
