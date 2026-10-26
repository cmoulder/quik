package dev.octoshrimpy.quik.feature.messageutils

import android.net.Uri
import dev.octoshrimpy.quik.common.base.QkViewContract
import io.reactivex.Observable
import io.reactivex.Single

interface MessageUtilsView: QkViewContract<MessageUtilsState> {
    val autoDeduplicateClickIntent: Observable<*>
    val deduplicateClickIntent: Observable<Unit>
    val previewDuplicatesClickIntent: Observable<Unit>
    val autoDeleteClickIntent: Observable<Unit>

    fun showDeduplicationConfirmationDialog(): Single<Boolean>
    fun handleDeduplicationResult(resIdString: Int)
    fun shareDuplicateReport(uri: Uri)

    fun autoDeleteChanged(): Observable<Int>
    fun showAutoDeleteDialog(days: Int)
    suspend fun showAutoDeleteWarningDialog(messages: Int): Boolean
}
