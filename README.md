# NovaTV Launcher

Русскоязычный Android TV HOME-лаунчер для Android 11–14, прототип 0.1.

Функции: ТВ-приложения, запуск, избранное, алфавитная сортировка, часы, две цветовые темы, настройки и «Диагностика».

## Получить APK
GitHub → Actions → «Сборка APK» → успешный запуск → Artifacts → NovaTV-Launcher-debug. ZIP содержит APK.

## Локальная сборка
JDK 17, Android SDK 35 и Gradle 8.9: `gradle assembleDebug`.

## TCL
Производитель может ограничить замену домашнего экрана. Команда для проверки через ADB: `adb shell cmd package set-home-activity ru.novatv.launcher/.MainActivity`. Не отключайте системный лаунчер до проверки возврата.

Погода, обои, ручная сортировка и автозапуск пока не реализованы. Сборка на реальном TCL не проверена.
