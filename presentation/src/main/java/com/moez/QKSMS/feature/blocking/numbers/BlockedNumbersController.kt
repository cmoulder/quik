/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.blocking.numbers

import android.view.LayoutInflater
import android.view.View
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.view.isVisible
import androidx.appcompat.app.AlertDialog
import com.jakewharton.rxbinding2.view.clicks
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkController
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.extensions.setBackgroundTint
import dev.octoshrimpy.quik.common.util.extensions.setTint
import dev.octoshrimpy.quik.injection.appComponent
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import dev.octoshrimpy.quik.databinding.BlockedNumbersControllerBinding
import dev.octoshrimpy.quik.databinding.BlockedNumbersAddDialogBinding
import javax.inject.Inject

class BlockedNumbersController : QkController<BlockedNumbersControllerBinding, BlockedNumbersView, BlockedNumbersState, BlockedNumbersPresenter>(),
    BlockedNumbersView {

    @Inject override lateinit var presenter: BlockedNumbersPresenter
    @Inject lateinit var colors: Colors
    @Inject lateinit var phoneNumberUtils: PhoneNumberUtils

    private val adapter = BlockedNumbersAdapter()
    private val saveAddressSubject: Subject<String> = PublishSubject.create()
    private val shortCodeDigitsSubject: Subject<Int> = PublishSubject.create()

    init {
        appComponent.inject(this)
        retainViewMode = RetainViewMode.RETAIN_DETACH
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup): BlockedNumbersControllerBinding =
        BlockedNumbersControllerBinding.inflate(inflater, container, false)

    override fun onAttach(view: View) {
        super.onAttach(view)
        presenter.bindIntents(this)
        setTitle(R.string.blocked_numbers_title)
        showBackButton(true)
    }

    override fun onViewCreated() {
        super.onViewCreated()
        binding.add.setBackgroundTint(colors.theme().theme)
        binding.add.setTint(colors.theme().textPrimary)
        adapter.emptyView = binding.empty
        binding.numbers.adapter = adapter
    }

    override fun render(state: BlockedNumbersState) {
        adapter.updateData(state.numbers)

        binding.blockUnknown.checkbox?.isChecked = state.blockUnknown
        binding.unknownExceptions.isVisible = state.blockUnknown
        binding.allowShortCodes.checkbox?.isChecked = state.allowShortCodes
        binding.allowShortCodes.summary = activity?.getString(
            R.string.blocked_numbers_allow_short_codes_summary, state.shortCodeDigits)
        binding.shortCodeDigits.isEnabled = state.allowShortCodes
        binding.shortCodeDigits.summary = activity?.getString(
            R.string.blocked_numbers_short_code_digits_summary, state.shortCodeDigits)
        binding.allowMessaged.checkbox?.isChecked = state.allowMessaged
    }

    override fun unblockAddress(): Observable<Long> = adapter.unblockAddress
    override fun addAddress(): Observable<*> = binding.add.clicks()
    override fun saveAddress(): Observable<String> = saveAddressSubject
    override fun blockUnknownClicks(): Observable<*> = binding.blockUnknown.clicks()
    override fun allowShortCodesClicks(): Observable<*> = binding.allowShortCodes.clicks()
    override fun shortCodeDigitsClicks(): Observable<*> = binding.shortCodeDigits.clicks()
    override fun shortCodeDigitsChanged(): Observable<Int> = shortCodeDigitsSubject
    override fun allowMessagedClicks(): Observable<*> = binding.allowMessaged.clicks()

    override fun showShortCodeDigitsDialog(current: Int) {
        val context = activity ?: return
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(current.toString())
            setSelection(text.length)
            hint = context.getString(R.string.blocked_numbers_short_code_digits_hint)
        }
        val container = FrameLayout(context).apply {
            setPadding(padding, padding / 2, padding, 0)
            addView(input)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.blocked_numbers_short_code_digits_title)
            .setMessage(R.string.blocked_numbers_short_code_digits_hint)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                input.text.toString().toIntOrNull()?.let(shortCodeDigitsSubject::onNext)
            }
            .setNegativeButton(R.string.button_cancel) { _, _ -> }
            .show()
    }

    override fun showAddDialog() {
        val layout = BlockedNumbersAddDialogBinding.inflate(LayoutInflater.from(activity))
        val textWatcher = BlockedNumberTextWatcher(layout.input, phoneNumberUtils)
        val dialog = AlertDialog.Builder(activity!!)
                .setView(layout.root)
                .setPositiveButton(R.string.blocked_numbers_dialog_block) { _, _ ->
                    saveAddressSubject.onNext(layout.input.text.toString())
                }
                .setNegativeButton(R.string.button_cancel) { _, _ -> }
                .setOnDismissListener { textWatcher.dispose() }
        dialog.show()
    }

}
