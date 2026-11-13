package ru.tolmachev.ogetrainer.data.llm

import ru.tolmachev.ogetrainer.data.models.ModelEntry

/**
 * Шаблон диалога под семейство модели. Один источник для генерации заданий
 * и для проверки письменных ответов: раньше проверка всегда слала ChatML,
 * из-за чего Gemma 3 отвечала мусором и вердикт терялся.
 */
object ChatPrompt {

    fun build(entry: ModelEntry, system: String, user: String): String = when {
        entry.repo.contains("gemma", ignoreCase = true) ->
            "<start_of_turn>user\n$system\n\n$user<end_of_turn>\n<start_of_turn>model\n"

        entry.repo.contains("Llama-3", ignoreCase = true) || entry.repo.contains("llama", ignoreCase = true) ->
            "<|start_header_id|>system<|end_header_id|>\n\n$system<|eot_id|>" +
                "<|start_header_id|>user<|end_header_id|>\n\n$user<|eot_id|>" +
                "<|start_header_id|>assistant<|end_header_id|>\n\n"

        else ->
            "<|im_start|>system\n$system<|im_end|>\n<|im_start|>user\n$user<|im_end|>\n" +
                "<|im_start|>assistant\n"
    }
}
