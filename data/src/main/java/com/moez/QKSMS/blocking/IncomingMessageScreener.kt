/*
 * Copyright (C) 2026
 *
 * This file is part of QUIK.
 *
 * QUIK is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QUIK is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QUIK.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.blocking

import android.provider.Telephony
import dev.octoshrimpy.quik.extensions.anyOf
import dev.octoshrimpy.quik.model.Conversation
import dev.octoshrimpy.quik.model.Message
import dev.octoshrimpy.quik.repository.ContactRepository
import dev.octoshrimpy.quik.repository.ConversationRepository
import dev.octoshrimpy.quik.repository.MessageContentFilterRepository
import dev.octoshrimpy.quik.repository.MessageRepository
import dev.octoshrimpy.quik.util.PhoneNumberUtils
import dev.octoshrimpy.quik.util.Preferences
import io.realm.Realm
import timber.log.Timber
import javax.inject.Inject

/**
 * Decides what happens to a newly received message, in this order:
 *
 *  1. Sender is on the block list (or blocked by the external blocking manager)
 *  2. Message body matches a content filter
 *  3. Sender is not a contact, and "block unknown senders" is on with no exception matching
 *
 * A match either drops the message (deletes it) or hides the conversation in Blocked messages,
 * depending on the relevant "drop" toggle. Sender-based rules (1 and 3) use the original
 * "Drop messages" toggle; content filters use "Drop filtered messages".
 */
class IncomingMessageScreener @Inject constructor(
    private val blockingClient: BlockingClient,
    private val prefs: Preferences,
    private val filterRepo: MessageContentFilterRepository,
    private val contactRepo: ContactRepository,
    private val conversationRepo: ConversationRepository,
    private val messageRepo: MessageRepository,
    private val phoneNumberUtils: PhoneNumberUtils
) {

    private sealed class Verdict {
        class Block(val drop: Boolean, val blockedBy: Int, val reason: String?) : Verdict()
        object Unblock : Verdict()
        object None : Verdict()
    }

    /**
     * Applies blocking rules to [message]. Returns false if the message was dropped (deleted),
     * in which case the caller must not touch it again.
     */
    fun screen(message: Message): Boolean {
        val address = message.address
        val threadId = message.threadId
        val numberAction = blockingClient.shouldBlock(address).blockingGet()

        val verdict = when {
            numberAction is BlockingClient.Action.Block ->
                Verdict.Block(prefs.drop.get(), prefs.blockingManager.get(), numberAction.reason)

            filterRepo.isBlocked(message.getText(), address, contactRepo) ->
                Verdict.Block(prefs.dropContentFiltered.get(), Preferences.BLOCKED_BY_CONTENT_FILTER, null)

            isBlockedUnknownSender(address) ->
                Verdict.Block(prefs.drop.get(), Preferences.BLOCKED_BY_UNKNOWN_SENDER, null)

            numberAction is BlockingClient.Action.Unblock -> Verdict.Unblock

            else -> Verdict.None
        }

        Timber.v("screening verdict for thread $threadId: ${verdict.javaClass.simpleName}")

        when (verdict) {
            is Verdict.Block -> {
                if (verdict.drop) {
                    messageRepo.deleteMessages(listOf(message.id))
                    return false
                }

                messageRepo.markRead(listOf(threadId))
                // The conversation must exist in realm before it can be flagged as blocked. For a
                // first message from a new sender it doesn't exist yet, so create it first
                conversationRepo.getOrCreateConversation(threadId)
                conversationRepo.markBlocked(listOf(threadId), verdict.blockedBy, verdict.reason)
            }

            is Verdict.Unblock -> {
                // Conversations hidden by a content filter stay hidden until the user unblocks
                // them, otherwise the next innocuous message from a spammer would reveal the
                // whole conversation
                val conversation = conversationRepo.getConversation(threadId)
                if (conversation?.blockingClient != Preferences.BLOCKED_BY_CONTENT_FILTER) {
                    conversationRepo.markUnblocked(threadId)
                }
            }

            is Verdict.None -> Unit
        }

        return true
    }

    private fun isBlockedUnknownSender(address: String): Boolean {
        if (!prefs.blockUnknownSenders.get() || address.isBlank()) return false

        if (contactRepo.isContact(address)) return false

        // Short codes and alphanumeric sender IDs (e.g. bank alerts, 2FA codes)
        if (prefs.blockUnknownAllowShortCodes.get() &&
            address.count(Char::isDigit) < prefs.blockUnknownShortCodeDigits.get()) {
            return false
        }

        if (prefs.blockUnknownAllowMessaged.get() && hasSentMessageTo(address)) return false

        return true
    }

    /**
     * True if the user has ever sent a message (SMS or MMS, 1:1 or group) in a conversation that
     * includes [address]. Matching on conversation recipients rather than Message.address handles
     * MMS, where the address of an outgoing message isn't the recipient's number
     */
    private fun hasSentMessageTo(address: String): Boolean =
        Realm.getDefaultInstance().use { realm ->
            realm.refresh()

            val threadIds = realm.where(Conversation::class.java)
                .findAll()
                .filter { conversation ->
                    conversation.recipients.any { recipient ->
                        phoneNumberUtils.compare(recipient.address, address)
                    }
                }
                .map { conversation -> conversation.id }
                .toLongArray()

            if (threadIds.isEmpty()) return@use false

            realm.where(Message::class.java)
                .anyOf("threadId", threadIds)
                .findAll()
                .any { message -> message.isMe() && !message.isDraft() }
        }

    private fun Message.isDraft(): Boolean = when {
        isSms() -> boxId == Telephony.Sms.MESSAGE_TYPE_DRAFT
        else -> boxId == Telephony.Mms.MESSAGE_BOX_DRAFTS
    }

}
