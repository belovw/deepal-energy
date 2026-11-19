# Deepal Energy — вариант B (CarProperty, как в Lynk-версии)

Источник данных: `CarPropertyManager` через существующий `FuelEnergyReader`. Это тот же путь, что в AutoEnergy Fuel UI.

Что уже есть:
- Весь код чтения, сброса, истории и симулятора из Lynk-версии, переименованный в `com.BW.deepalenergy`.

Что нужно до работы на Deepal S07:
- Заменить ID свойств в `FuelEnergyReader.kt` и `SimulatedCarProperties.kt`. У Deepal они другие, и список у меня пока нет.
- Проверить, открыты ли `android.car.Car` и CarProperty без системной подписи. По прошлому проекту известно, что сервис `mega.car` требует отдельных прав (`mega.car.permission.*`), и без них чтение вероятно будет отказано.
- Добавить сигналы EREV (батарея, поток энергии), которых в Lynk-версии нет.

Сборка Gradle не запускалась (нет Android SDK).
