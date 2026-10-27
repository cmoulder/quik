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
package dev.octoshrimpy.quik.feature.main

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.LayoutInflater
import android.view.ViewGroup
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.Navigator
import dev.octoshrimpy.quik.common.base.QkAdapter
import dev.octoshrimpy.quik.common.base.QkBindingViewHolder
import dev.octoshrimpy.quik.common.util.Colors
import dev.octoshrimpy.quik.common.util.DateFormatter
import dev.octoshrimpy.quik.common.util.extensions.setVisible
import dev.octoshrimpy.quik.extensions.removeAccents
import dev.octoshrimpy.quik.model.SearchMatcher
import dev.octoshrimpy.quik.model.SearchResult
import dev.octoshrimpy.quik.databinding.SearchListItemBinding
import javax.inject.Inject

class SearchAdapter @Inject constructor(
    colors: Colors,
    private val context: Context,
    private val dateFormatter: DateFormatter,
    private val navigator: Navigator
) : QkAdapter<SearchResult, QkBindingViewHolder<SearchListItemBinding>>() {

    private companion object {
        // Characters of context kept before the match when a long message is trimmed, so the
        // matched word is visible in the two-line snippet
        const val SNIPPET_LEAD = 24
    }

    private val highlightColor: Int by lazy { colors.theme().highlight }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QkBindingViewHolder<SearchListItemBinding> {
        val binding = SearchListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return QkBindingViewHolder(binding).apply {
            itemView.setOnClickListener {
                val result = getItem(adapterPosition)
                navigator.showConversation(result.conversation.id, result.query.takeIf { result.message != null })
            }
        }
    }

    override fun onBindViewHolder(holder: QkBindingViewHolder<SearchListItemBinding>, position: Int) {
        val result = getItem(position)
        val binding = holder.binding
        val query = result.query

        // Conversation name: partial, case-insensitive highlight, same rule used to match names
        val title = SpannableString(result.conversation.getTitle())
        val plainTitle = title.removeAccents()
        var index = plainTitle.indexOf(query, ignoreCase = true)
        // Removing accents can change the length of a few unusual characters, so check bounds
        while (index >= 0 && index + query.length <= title.length) {
            title.setSpan(BackgroundColorSpan(highlightColor), index, index + query.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            index = plainTitle.indexOf(query, index + query.length, true)
        }
        binding.title.text = title
        binding.avatars.recipients = result.conversation.recipients
        binding.resultsHeader.setVisible(false)

        val message = result.message
        if (message == null) {
            // Name match only: show the latest message, like the inbox
            binding.date.text = dateFormatter.getConversationTimestamp(result.conversation.date)
            binding.snippet.text = when (result.conversation.me) {
                true -> context.getString(R.string.main_sender_you, result.conversation.snippet)
                false -> result.conversation.snippet
            }
        } else {
            // Message match: show the most recent matching message with the word highlighted
            binding.date.text = dateFormatter.getConversationTimestamp(message.date)
            binding.snippet.text = highlightedSnippet(message.getText(false), query, message.isMe())
        }
        binding.date.setVisible(true)
    }

    private fun highlightedSnippet(text: String, query: String, fromMe: Boolean): CharSequence {
        val flat = text.replace('\n', ' ')
        val matches = SearchMatcher.wholeWord(query).findAll(flat).toList()

        // Trim the start of long messages so the first match isn't cut off
        val firstStart = matches.firstOrNull()?.range?.first ?: 0
        val cut = if (firstStart > SNIPPET_LEAD) {
            // Start at a word boundary
            flat.lastIndexOf(' ', firstStart - SNIPPET_LEAD).let { if (it < 0) firstStart - SNIPPET_LEAD else it + 1 }
        } else 0
        val body = (if (cut > 0) "\u2026" else "") + flat.substring(cut)
        val shift = (if (cut > 0) 1 else 0) - cut

        val prefix = if (fromMe) context.getString(R.string.main_sender_you, "") else ""
        val snippet = SpannableString(prefix + body)
        matches.forEach { match ->
            val start = match.range.first + shift + prefix.length
            val end = match.range.last + 1 + shift + prefix.length
            if (start >= prefix.length && end <= snippet.length) {
                snippet.setSpan(BackgroundColorSpan(highlightColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return snippet
    }

    override fun areItemsTheSame(old: SearchResult, new: SearchResult): Boolean {
        return old.conversation.id == new.conversation.id
    }

    override fun areContentsTheSame(old: SearchResult, new: SearchResult): Boolean {
        return old.query == new.query &&
                old.message?.id == new.message?.id &&
                old.conversation.lastMessage?.id == new.conversation.lastMessage?.id
    }
}
