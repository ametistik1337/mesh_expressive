# Инструкции и контекст проекта Mesh Expressive (GEMINI.md)

Документ содержит обязательные инженерные стандарты, контекст архитектуры и алгоритмы работы для AI-агентов (Google Gemini / Antigravity) при модификации кодовой базы `mesh_expressive`.

---

## 1. Архитектура и стек

- **Платформа**: Android 8.0+ (API 26..34).
- **Язык**: Kotlin 2.0.
- **UI фреймворк**: Jetpack Compose, Material Design 3 Expressive.
- **Архитектурный паттерн**: MVVM с однонаправленным потоком данных (UDF).
- **Состояние и асинхронность**: Kotlin Coroutines, `StateFlow`, `SharedFlow`.
- **Сетевой стек**: Retrofit 2, OkHttp 3, GSON.
- **Хранилище**: `SessionManager` на базе `EncryptedSharedPreferences` (офлайн-первичный кэш).
- **Инварианты**:
  - Полный запрет на добавление рекламных и телеметрических библиотек (AppMetrica, Sentry, MyTracker и др.).
  - Строгий запрет на использование любых эмодзи в исходном коде, UI и коммитах.

---

## 2. Стандарты UI Material 3 Expressive

- **Обязательный сбор требований с material.io**:
  - Перед созданием или рефакторингом любого UI-компонента агент обязан выполнить поиск и анализ актуальной спецификации на `https://m3.material.io/`.
  - Извлекать точные гайдлайны: анатомию компонента, токены скруглений (corner radius), межэлементные отступы, тональные высоты (elevation), динамические цветовые роли и параметры пружинной физики (ODE spring parameters: stiffness, dampingRatio).
- **Соединенные группы кнопок (Connected Button Groups)**:
  - Использовать `M3ConnectedButtonGroup` вместо устаревших `SegmentedButton`.
  - Зазор между сегментами: 2dp.
  - Скругления: внешние края 16dp (или CornerSize(50)), внутренние стыки 4dp–8dp.
  - Анимированная иконка выбора (Checkmark) с плавным горизонтальным раскрытием.
- **Тональная палитра и цвета**:
  - Использовать токены `surfaceContainerLowest`, `surfaceContainerLow`, `surfaceContainerHigh` для создания визуальной глубины без резких границ.
  - Поддержка движка динамических цветов Monet (Material You).
- **Физика анимаций**:
  - Пружинные анимации без жестких линейных переходов (`spring(dampingRatio = 0.7f..0.85f, stiffness = 350f..500f)`).
  - Запрет хардкода координат при позиционировании летающих оверлеев — привязка строго через `LayoutCoordinates` (`localPositionOf`).

---

## 3. Реверс-инжиниринг официального клиента МЭШ

### Получение свежего APK
1. Выгрузка установленного APK с подключенного устройства:
   ```bash
   adb shell pm path ru.mes.dnevnik
   adb pull /data/app/.../base.apk official_dnevnik.apk
   ```
2. Скачивание актуальных сборок через CLI (`apkeep`) или из доверенных репозиториев (RuStore, Google Play).

### Декомпиляция React Native бандла
Официальный клиент построен на React Native.
1. Распаковка архива APK:
   ```bash
   unzip -q official_dnevnik.apk -d extracted_apk/
   ```
2. Поиск JS-бандла:
   - Путь: `extracted_apk/assets/index.android.bundle`.
3. Анализ формата бандла:
   - Если бандл в текстовом формате JS: форматирование через `prettier` или поиск через `grep`.
   - Если скомпилирован в байткод Hermes (`.hbc`): декомпиляция через `hbctool` или `hermes-dec`.
4. Извлечение строк и эндпоинтов:
   ```bash
   strings -a -n 6 extracted_apk/assets/index.android.bundle > decompiled/dnevnik_strings.txt
   ```

### База и поиск эндпоинтов
- **Где искать в бандле**:
  - Поиск URL-шаблонов: `grep -E "https?://" decompiled/dnevnik_strings.txt`
  - Поиск путей API: `grep -E "(api/|family/|mobile/|core/|schoolinfo/)" decompiled/dnevnik_strings.txt`
  - Поиск структур ответов и заголовков: `Auth-Token`, `Profile-Id`, `client-type`.
- **Локальная база эндпоинтов**:
  - Каталог `decompiled/`:
    - `decompiled/dnevnik_strings.txt` — выгрузка строковых констант.
    - `decompiled/mesh_reverse_engineering_report.md` — детальный отчет по архитектуре и протоколам.
  - Отражение в коде:
    - [MeshApiService.kt](app/src/main/java/ru/mesh/expressive/data/remote/MeshApiService.kt) — типизированные Retrofit-интерфейсы.
    - [MeshRepository.kt](app/src/main/java/ru/mesh/expressive/data/repository/MeshRepository.kt) — бизнес-логика маппинга и агрегации.

---

## 4. Специфика бизнес-логики и правила обработки данных

- **Авторизация**:
  - Сессия использует `auth_token` и `profile_id`.
  - При ошибке 401 Unauthorized или истечении времени жизни токена (`exp`) запускается фоновое обновление через легковесный скрытый WebView (`BackgroundTokenRefreshWebView`) без разлогина пользователя.
- **Оценки и точки**:
  - Просроченные точки (дата исправления меньше текущей даты) обязаны учитываться в расчете среднего балла (GPA) по формуле МЭШ.
  - Оценка, выставленная взамен точки, помечается признаком `hasCorrection` и звездочкой `*`.
  - Полная история правок выводится в шторке урока (`LessonDetailBottomSheet`).
- **Периоды и аттестация**:
  - Итоговая выставленная оценка с галочкой привязывается к фактически завершенной четверти.
  - Незавершенные периоды остаются пустыми до момента официального выставления отметки.
  - Поддержка строковых зачетов: «З» (Зачтено), «А» (Аттестован), «ОСВ» (Освобожден), «Н/А» (Не аттестован).

---

## 5. Пайплайн сборки, установки и верификации

- **Сборка релизного APK**:
  ```bash
  cd /home/ametist1337/projects/mesh_expressive
  ./gradlew assembleRelease
  ```
  Результат сборки: `app/build/outputs/apk/release/app-release.apk`.
- **Установка на целевое устройство (Pixel 9a)**:
  ```bash
  adb -s 192.168.0.153:5555 install -r app/build/outputs/apk/release/app-release.apk
  adb -s 192.168.0.153:5555 shell am force-stop ru.mesh.expressive
  adb -s 192.168.0.153:5555 shell am start -n ru.mesh.expressive/.MainActivity
  ```
- **Снятие контрольных скриншотов**:
  ```bash
  adb -s 192.168.0.153:5555 shell screencap -p /sdcard/screen.png
  adb -s 192.168.0.153:5555 pull /sdcard/screen.png ./docs/screenshots/
  adb -s 192.168.0.153:5555 shell rm -f /sdcard/screen.png
  ```
