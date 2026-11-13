package ru.tolmachev.ogetrainer.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.tolmachev.ogetrainer.ui.components.ChoiceRow
import ru.tolmachev.ogetrainer.ui.components.TopBar
import ru.tolmachev.ogetrainer.ui.theme.Space

/**
 * Раздел «Помощь»: пошаговые инструкции для каждой роли.
 * Текст описывает только то, что есть в приложении, — без обещаний.
 */
@Composable
fun HelpScreen(role: String, onBack: () -> Unit) {
    // Короткие подписи: длинное «Администратор» не влезает в три сегмента.
    val roles = listOf("Ученик", "Родитель", "Админ")
    var selected by remember {
        mutableStateOf(
            when (role) {
                "admin" -> "Админ"
                "parent" -> "Родитель"
                else -> "Ученик"
            },
        )
    }

    val topics = remember(selected) { helpFor(selected) }
    var opened by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = "Помощь", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Text(
                "Инструкции для роли. Выберите роль — текст не зависит от того, под кем вы вошли.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            ChoiceRow(
                options = roles,
                selected = selected,
                onSelect = { selected = it; opened = null },
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = Space.x3,
                end = Space.x3,
                top = Space.x2,
                bottom = Space.x5,
            ),
            verticalArrangement = Arrangement.spacedBy(Space.x1_5),
        ) {
            items(topics, key = { it.title }) { section ->
                val isOpen = opened == section.title
                Card(
                    modifier = Modifier.clickable { opened = if (isOpen) null else section.title },
                ) {
                    Column(
                        modifier = Modifier.padding(Space.x3),
                        verticalArrangement = Arrangement.spacedBy(Space.x1),
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(
                                section.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                if (isOpen) "свернуть" else "открыть",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (isOpen) {
                            Text(
                                section.intro,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            section.steps.forEachIndexed { index, step ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                                    Text(
                                        "${index + 1}.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        step,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                            section.note?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text(
                                section.intro,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class HelpSection(
    val title: String,
    val intro: String,
    val steps: List<String>,
    val note: String? = null,
)

private fun helpFor(role: String): List<HelpSection> = when (role) {
    "Админ" -> adminHelp()
    "Родитель" -> parentHelp()
    else -> studentHelp()
}

private fun adminHelp(): List<HelpSection> = listOf(
    HelpSection(
        title = "Первый запуск",
        intro = "Приложение работает автономно: банк заданий внутри, интернет нужен только для моделей.",
        steps = listOf(
            "Откройте приложение: банк заданий импортируется сам, в списке профилей появится «Администратор · встроенный».",
            "Войдите под ним. Пароль задан при сборке; сменить его можно только полным сбросом профилей — это защита от перехвата.",
            "Меню открывается жестом от правого края экрана или кнопкой в верхней панели.",
        ),
        note = "Встроенного администратора нельзя удалить и переименовать — он всегда первый в списке.",
    ),
    HelpSection(
        title = "Профили учеников",
        intro = "Администрирование → «Профили» → «Добавить профиль». Карточка создаётся в два шага.",
        steps = listOf(
            "Вкладка «Карточка»: фото из галереи (нажмите на круг), ФИО, возраст.",
            "Класс выбирается ползунком от 1 до 11; программа (ОГЭ или школьная) подставляется по классу — 9 класс даёт ОГЭ.",
            "Предметы: список пуст. Нажмите предмет в блоке «ДОБАВИТЬ» — он уйдёт в выбранные; крестик убирает. Повторов быть не может.",
            "Вкладка «Доступ»: логин, роль (ученик, родитель, администратор), пароль и повтор.",
            "Сохранить. Ученик без предметов не сохранится — приложение скажет «Добавьте хотя бы один предмет».",
            "Удаление — корзина в строке профиля, с подтверждением; вместе с профилем удаляются попытки и освоение.",
        ),
        note = "У администратора и родителя в карточке нет класса и предметов — только ФИО, возраст и фото.",
    ),
    HelpSection(
        title = "Провайдер модели (облако)",
        intro = "Администрирование → «Провайдер». Любой OpenAI-совместимый endpoint: DeepSeek, OpenRouter, шлюз, локальный llama.cpp.",
        steps = listOf(
            "Укажите адрес (например https://api.deepseek.com), модель и ключ.",
            "Нажмите «Проверить подключение» — приложение делает один короткий запрос и показывает задержку или код ошибки с подсказкой.",
            "Сохранить. Ключ хранится в защищённом хранилище Android (EncryptedSharedPreferences) и в интерфейсе показывается маской.",
            "По https работает любой адрес; по http разрешены только локальные сети (10.x, 172.16–31.x, 192.168.x, localhost).",
        ),
        note = "Без провайдера работают банк, теория, локальные шаблоны и модель на устройстве.",
    ),
    HelpSection(
        title = "Локальные модели на устройстве",
        intro = "Администрирование → «Локальные модели». Движок llama.cpp встроен в приложение, сеть нужна только для скачивания файла.",
        steps = listOf(
            "Сверху показаны характеристики устройства: ОЗУ, свободное место, ядра, ABI.",
            "Список моделей подобран по памяти: 1B и 1.5B — от 3 ГБ ОЗУ, 3B — от 6 ГБ. Если не хватает, кнопка неактивна и написано, сколько нужно.",
            "«Скачать» — загрузка с Hugging Face. Можно поставить на паузу и продолжить: файл докачивается с того же места, в том числе после перезапуска приложения и после пропадания сети.",
            "После загрузки выберите модель («Выбрать»), затем «Загрузить в память». Показан бюджет: модель + контекст + запас.",
            "«Выгрузить из памяти» освобождает ОЗУ, файл остаётся на устройстве.",
        ),
        note = "Перед загрузкой считается бюджет памяти; во время генерации память проверяется каждые 16 токенов, при нехватке генерация останавливается без падения приложения.",
    ),
    HelpSection(
        title = "Банк контента и приёмка",
        intro = "Администрирование → «Банк контента»: что есть, откуда взялось и что ещё не проверено.",
        steps = listOf(
            "Сводка: число предметов, тем, конспектов, задач, сколько сгенерировано и сколько ждёт проверки.",
            "Фильтр «Источник»: Все, Банк (ФИПИ), Шаблоны (локальные, ответ вычислен кодом), Локально (модель на устройстве), Облако.",
            "Предмет → темы: у каждой темы видно число задач, количество черновиков и есть ли конспект.",
            "«+» рядом с темой — генерация заданий: шаблонами (без сети) или облачной моделью. Черновики попадают в приёмку.",
            "Откройте тему: у задачи видны источник и статус. «Принять» — задание станет проверенным и уйдёт ученикам, «Удалить» — убрать черновик, «Принять все» — принять всю пачку.",
            "Тап по задаче открывает полный рендер формул.",
        ),
        note = "Задания банка ФИПИ считаются проверенными; черновиками считаются только сгенерированные.",
    ),
    HelpSection(
        title = "Генерация заданий профилю",
        intro = "Администрирование → «Профили» → «+» в строке ученика.",
        steps = listOf(
            "Выберите предмет и тему из программы профиля.",
            "Режим: «Локально» — шаблоны без сети, ответ вычисляет приложение (проверка не нужна); «На устройстве» — загруженная модель, без сети; «Моделью» — облачный провайдер, нужен интернет.",
            "Укажите количество (3, 5, 10) и нажмите «Сгенерировать». Результат показывается в диалоге и в разделе «Банк».",
        ),
        note = "Задания модели всегда приходят черновиками: качество 1B–3B нестабильно, проверяйте ответы перед приёмкой.",
    ),
    HelpSection(
        title = "Диагностика и проверка развёрнутых",
        intro = "Диагностика — по кнопке, чтобы получить стартовый уровень; проверка развёрнутых ответов идёт в очередь.",
        steps = listOf(
            "Диагностика запускается из карточки ученика: 20 заданий по разделам предметов, без подсказок. Результат задаёт начальное освоение тем.",
            "Развёрнутые ответы, изложения и сочинения проверяет модель как предварительно, затем подтверждает родитель или администратор.",
            "Очередь проверки: меню → «Проверка». Пока ответ не подтверждён, он не влияет на освоение.",
        ),
        note = "Если модель не уверена или ответила не по формату, приложение оставляет статус «ожидает проверки».",
    ),
    HelpSection(
        title = "Сброс и перенос данных",
        intro = "Все данные хранятся на устройстве: профили, попытки, освоение, файлы моделей.",
        steps = listOf(
            "«Сбросить профили» в администрировании удаляет профили и их попытки, затем заново создаёт встроенного администратора.",
            "Задания, конспекты и скачанные модели при этом сохраняются.",
            "Перенос на другое устройство — пока вручную: переустановка с восстановлением профиля и повторной загрузкой модели.",
        ),
    ),
)

private fun parentHelp(): List<HelpSection> = listOf(
    HelpSection(
        title = "Вход и профиль ребёнка",
        intro = "Родитель видит отчёты и проверяет письменные работы; тренировки и задания создаёт администратор.",
        steps = listOf(
            "На экране входа выберите свой профиль и введите пароль.",
            "Если профиля нет — попросите администратора создать его в карточке профиля с ролью «Родитель».",
            "Предметы и класс ребёнка задаёт администратор; от этого зависит, какие темы и задания доступны.",
        ),
    ),
    HelpSection(
        title = "Отчёты по ребёнку",
        intro = "Прогресс считается по темам: освоение темы 0…1, тема считается освоенной при 0,7 и выше.",
        steps = listOf(
            "Открывается список предметов: доля освоенных тем и средний уровень по предмету.",
            "Внутри предмета — темы со статусами: «освоено», «в работе», «слабо», «мало данных», «нет данных».",
            "«Мало данных» — меньше трёх попыток: приложение намеренно не делает выводов раньше.",
            "Без практики освоение снижается на 5 % в неделю — это не ошибка, а честная оценка забывания.",
        ),
    ),
    HelpSection(
        title = "Проверка письменных работ",
        intro = "Развёрнутые ответы, изложения и сочинения проверяет модель, окончательно подтверждаете вы или администратор.",
        steps = listOf(
            "Меню → «Проверка»: список ответов со статусом «ожидает проверки».",
            "В карточке ответа видно задание, ответ ученика, критерии и предварительный вердикт модели с оценкой уверенности.",
            "Подтвердите оценку или исправьте её. До подтверждения ответ не влияет на освоение темы.",
            "Если модель ошиблась или ответ не по теме — снимите баллы и напишите замечание.",
        ),
        note = "Локальная модель на телефоне даёт только предварительный вердикт: на 1–3B проверка русских текстов нестабильна.",
    ),
    HelpSection(
        title = "Что делать, если ребёнок «застрял»",
        intro = "Освоение растёт от практики, а не от времени в приложении.",
        steps = listOf(
            "Посмотрите темы со статусом «слабо» — там есть попытки, но результат ниже 0,5.",
            "Откройте тему вместе с ребёнком: вкладка «Теория» даёт конспект с формулами, «Задачи» — задания с решением после ответа.",
            "Попросите администратора добавить задания по этой теме (шаблоны дают задания с гарантированно верным ответом).",
            "Через 3–5 верных ответов статус сменится на «освоено».",
        ),
    ),
)

private fun studentHelp(): List<HelpSection> = listOf(
    HelpSection(
        title = "Как заниматься",
        intro = "Выберите предмет, затем тему. Внутри темы две вкладки: теория и задачи.",
        steps = listOf(
            "Предметы на главном экране показывают, сколько тем освоено.",
            "Тема открывается списком: сначала прочитайте конспект — в нём формулы и разбор.",
            "Перейдите на вкладку «Задачи», введите ответ и нажмите «Ответить».",
            "После ответа показывается результат и решение. Если ответ неверный — разберите решение и попробуйте следующую задачу.",
            "Меню открывается жестом от правого края; там же выбор темы оформления и выход.",
        ),
    ),
    HelpSection(
        title = "Короткие ответы",
        intro = "Короткий ответ проверяется сразу на телефоне, без интернета.",
        steps = listOf(
            "Ответ — число или слово, без единиц измерения и пояснений.",
            "Регистр, лишние пробелы, запятая вместо точки и разные тире не важны: «2,5» и «2.5» — одно и то же.",
            "Ошиблись — ничего страшного: попытка сохраняется, освоение пересчитывается по последним ответам, один промах не обнуляет тему.",
        ),
    ),
    HelpSection(
        title = "Развёрнутые ответы, изложение и сочинение",
        intro = "Такие задания проверяет модель, а подтверждает родитель или администратор.",
        steps = listOf(
            "Напишите ответ в поле и отправьте: он уйдёт на проверку.",
            "Пока проверка не завершена, задание висит со статусом «ожидает проверки» и не влияет на освоение.",
            "После подтверждения оценки посмотрите замечания по критериям — они показывают, что именно не засчитано.",
        ),
        note = "Ответы уходят только тому провайдеру или модели, которые настроил администратор; без настройки проверка ждёт человека.",
    ),
    HelpSection(
        title = "Уровень и прогресс",
        intro = "Освоение темы — число от 0 до 1: тема считается освоенной при 0,7.",
        steps = listOf(
            "«Мало данных» — меньше трёх попыток, оценка ещё не делается.",
            "«Слабо» — есть попытки, но результат ниже 0,5: стоит вернуться к теории.",
            "Без практики уровень снижается на 5 % в неделю, поэтому лучше заниматься понемногу, но регулярно.",
            "Диагностика (если её запустил администратор) сразу выставляет стартовый уровень по разделам — не нужно начинать с нуля.",
        ),
    ),
)
