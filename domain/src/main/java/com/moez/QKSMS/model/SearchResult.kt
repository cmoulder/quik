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
package dev.octoshrimpy.quik.model

/**
 * One row in the search results. [message] is the most recent message in the conversation that
 * matches the query, or null if only the conversation's name or a participant matched
 */
data class SearchResult(val query: String, val conversation: Conversation, val message: Message?)

object SearchMatcher {

    /**
     * Whole-word, case-insensitive match for [query], also accepting plurals ending in s or es
     * and possessives (car, cars, car's, cars', box, boxes). "car" does not match "card" or "scar".
     * Word boundaries are letters and digits, so punctuation around the word doesn't matter
     */
    fun wholeWord(query: String): Regex =
        Regex(
            "(?<![\\p{L}\\p{N}])" + Regex.escape(query) +
                "(?:e?s)?(?:['\u2019]s?)?(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE
        )
}