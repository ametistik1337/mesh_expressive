# Инструкция по выкатке релиза mesh_expressive (update.md)

Руководство для AI-агентов в новых сессиях по полному циклу релиза приложения **Дневник МЭШ Expressive**.

---

## 1. Обновление версии

1. Открой `app/build.gradle.kts` и найди `versionCode` и `versionName`.
2. Увеличь `versionCode` (целое число, +1 или +2 от предыдущего) и обнови `versionName` (например, `"2.4.3"`).

---

## 2. Сборка релизного APK

```bash
cd /home/ametist1337/projects/mesh_expressive
./gradlew assembleRelease
```

Готовый файл: `app/build/outputs/apk/release/app-release.apk`.

---

## 3. Git: коммиты и пуш

1. Разбивай изменения на несколько осмысленных коммитов (отдельно фичи, фиксы, UI-компоненты и финальный бамп версии `chore(release): bump version to X.Y.Z`).
2. Не коммить временные скриншоты из `docs/screenshots/` и временные демо-видео, если пользователь не просил добавлять их в репозиторий.
3. Запушь в `origin`:
   ```bash
   git push origin main
   ```

---

## 4. GitHub Release

- **Репозиторий**: `ametistik1337/mesh_expressive` (всегда передавай `-R ametistik1337/mesh_expressive`, так как у `gh` не выбран default remote).
- **Формат ченжлога**: полный, структурированный список пользовательских изменений на русском языке.
- **Важно**: не включать в публичный ченжлог внутренние технические чистки (удаление неиспользуемых пакетов, правки счетчиков, README и т.п.), если пользователь явно не попросил об этом.

Команда создания релиза:
```bash
gh release create vX.Y.Z app/build/outputs/apk/release/app-release.apk \
  -R ametistik1337/mesh_expressive \
  --title "Дневник МЭШ Expressive vX.Y.Z" \
  --notes "- **Фича 1**: описание..."
```

Редактирование существующего релиза:
```bash
gh release edit vX.Y.Z -R ametistik1337/mesh_expressive --notes "..."
```

---

## 5. Пост в Telegram-канал (`@expressive_mesh`)

- **Канал проекта**: `@expressive_mesh` (chat_id: `-1002651553118`).
- **Внимание**: не путать с каналом `@serpantinum_git` — это другой проект пользователя.
- **Инструмент**: MCP-сервер `telegram-userbot` (`send_file`, `send_message`, `get_messages`).
- **Стиль поста**:
  - Поддерживается полная Markdown-верстка (`**жирный**`, `` `код` ``, `[текст](url)`).
  - Краткая живая выжимка изменений строчными буквами, без воды и лишних списков-буллетов.
  - Для сложных фич — короткое пояснение в скобках, как пользоваться (например: `(открыл ЦДЗ -> тыкнул робота -> вставил в нейронку)`).
  - В конце ссылка: `[фулл ченжлог](https://github.com/ametistik1337/mesh_expressive/releases/tag/vX.Y.Z)`.

### Отправка медиа и APK
Telegram не позволяет смешивать видео/фото и APK-документ в одном сообщении через `send_file`:
1. Сначала отправь видео/демо с основным текстом ченжлога в `caption` (`send_file(peer="@expressive_mesh", file_path="...", caption="...")`).
2. Следующим сообщением прикрепи `app/build/outputs/apk/release/app-release.apk` с короткой подписью `Дневник МЭШ Expressive vX.Y.Z APK`.

---

## 6. Заметки по генерации демо-видео (если требуется)

- Для точного рендера UI в нужной палитре (например, зеленый Monet) используй `@Preview` + sidecar `compose-preview` (`POST /api/session` / `POST /api/render`).
- При сведении видео и аудио через `ffmpeg` на текущей системе **используй `-c:a libmp3lame` вместо `-c:a aac`** (кодер `aac` в `ffmpeg n9.1` зависает на моно 48kHz). Кодируй видео-дорожку отдельно (`-c:v libx264 -preset ultrafast`), затем своди с WAV.
