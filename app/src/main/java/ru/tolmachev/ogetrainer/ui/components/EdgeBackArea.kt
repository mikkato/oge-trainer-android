package ru.tolmachev.ogetrainer.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/**
 * Жест «назад» с ПРАВОГО края (меню при этом выезжает слева — требование заказчика).
 *
 * Срабатывает только если палец опущен в полосе 28 dp у правого края и ушёл влево
 * не меньше чем на 64 dp: обычные прокрутки и свайпы по контенту не перехватываются.
 */
@Composable
fun EdgeBackArea(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val edge = 28.dp.toPx()
                val threshold = 64.dp.toPx()
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (down.position.x < size.width - edge) return@awaitEachGesture
                    var travelled = 0f
                    var fired = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        travelled += change.positionChange().x
                        if (!fired && travelled < -threshold) {
                            fired = true
                            change.consume()
                            onBack()
                        }
                        if (change.changedToUpIgnoreConsumed()) break
                    }
                }
            },
        content = content,
    )
}
