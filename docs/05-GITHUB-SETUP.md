# GitHub: репозиторий, сборка и релизы

## 1. Репозиторий

| Параметр | Значение |
|---|---|
| URL | https://github.com/mikkato/oge-trainer-android (создан 13.11.2026) |
| Аккаунт GitHub | `mikkato` (`mikhail.tolmachev@gmail.com`, Mikhail E. Tolmachev) |
| Имя | `oge-trainer-android` |
| Видимость | публичный — иначе ссылка на APK из Releases требует авторизации |
| Ветка по умолчанию | `main` |
| Лицензия | решение за заказчиком; код приложения — proprietary, банк контента содержит материалы для личного обучения |

Сертификат подписи (создан 13.11.2026, хранится в `.secrets/keystore.jks`, срок до 31.03.2054):

```
Alias:   ogetrainer
Owner:   CN=Oge Trainer, O=Mikhail Tolmachev, L=Saint Petersburg, C=RU
SHA-256: C0:FB:3E:C3:B9:01:D9:1C:96:0D:1D:26:CE:D4:29:CC:C1:EA:45:4E:BE:73:38:A8:6D:F8:1A:85:D6:02:94:10
keystore sha256: b3fd1ebaaed8223459a1f2d67abb3a2a7762d4258df2f3d0b71ddb2625f430b6
```

Keystore нельзя терять и нельзя пересоздавать: с другим ключом Android откажется ставить обновление поверх установленного приложения. Копия нужна в двух местах — локально и в защищённом хранилище заказчика.

Что **не** попадает в репозиторий (см. `.gitignore`): `.secrets/`, keystore (`*.jks`), `local.properties`, артефакты сборки. Ключ DeepSeek в репозиторий не коммитится ни в каком виде — он вводится в приложении и в CI не нужен.

## 2. Секреты репозитория (уже заданы 13.11.2026)

| Имя | Назначение |
|---|---|
| `KEYSTORE_BASE64` | `keystore.jks`, закодированный base64 (5 848 байт) |
| `KEYSTORE_PASSWORD` | пароль хранилища |
| `KEY_ALIAS` | `ogetrainer` |
| `KEY_PASSWORD` | пароль ключа |

Проверить: `gh secret list`. Workflow сборки — `.github/workflows/release.yml`; до появления Gradle-проекта (этап P0-1) сборка намеренно падает с сообщением «Gradle-проект ещё не добавлен».

## 3. Процесс релиза

1. Локально: `./gradlew :app:assembleRelease` проходит, `versionName` поднят.
2. Коммит `FR-XXX: …`, тег: `git tag v1.0.0 && git push --tags`.
3. Workflow `.github/workflows/release.yml`: сборка на `ubuntu-latest`, JDK 21, подпись из Secrets, публикация `oge-trainer-<version>.apk` и `sha256` в GitHub Release.
4. В описании релиза — список изменений по ID требований и хеш файла.
5. Установка: ссылка вида `https://github.com/<owner>/oge-trainer-android/releases/latest` → APK → «Установить из неизвестного источника».

Публикация первой версии — только после приёмки AC-1…AC-10 и проверки установки на устройстве заказчика.

## 4. Обновления

- Тот же `applicationId` и тот же keystore обязательны, иначе Android не даст установить обновление поверх.
- После каждой смены схемы Room — тест миграции; перед публикацией релиза экспорт прогресса (FR-806) рекомендуется как страховка.
- Отпечаток сертификата публикуется в README (`keytool -list -v`), чтобы можно было проверить подлинность APK.
