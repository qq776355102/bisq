/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.notifications.alerts;

import bisq.core.locale.Res;
import bisq.core.notifications.MobileMessage;
import bisq.core.notifications.MobileMessageType;
import bisq.core.notifications.MobileNotificationService;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.arbitration.ArbitrationManager;
import bisq.core.support.messages.ChatMessage;

import bisq.network.p2p.P2PService;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.ListChangeListener;

import java.util.UUID;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DisputeMsgEvents {
    private final P2PService p2PService;
    private final MobileNotificationService mobileNotificationService;

    @Inject
    public DisputeMsgEvents(ArbitrationManager arbitrationManager,
                            P2PService p2PService,
                            MobileNotificationService mobileNotificationService) {
        this.p2PService = p2PService;
        this.mobileNotificationService = mobileNotificationService;

        // We need to handle it here in the constructor otherwise we get repeated the messages sent.
        arbitrationManager.getDisputesAsObservableList().addListener((ListChangeListener<Dispute>) c -> {
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setDisputeListener);
            }
        });
        arbitrationManager.getDisputesAsObservableList().forEach(this::setDisputeListener);
    }

    // We ignore that onAllServicesInitialized here
    public void onAllServicesInitialized() {
    }

    private void setDisputeListener(Dispute dispute) {
        //TODO use weak ref or remove listener
        log.debug("We got a dispute added. id={}, tradeId={}", dispute.getId(), dispute.getTradeId());
        dispute.getChatMessages().addListener((ListChangeListener<ChatMessage>) c -> {
            log.debug("We got a ChatMessage added. id={}, tradeId={}", dispute.getId(), dispute.getTradeId());
            c.next();
            if (c.wasAdded()) {
                c.getAddedSubList().forEach(this::setChatMessage);
            }
        });

        //TODO test
        if (!dispute.getChatMessages().isEmpty())
            setChatMessage(dispute.getChatMessages().get(0));
    }

    private void setChatMessage(ChatMessage disputeMsg) {
        // TODO we need to prevent to send msg for old dispute messages again at restart
        // Maybe we need a new property in ChatMessage
        // As key is not set in initial iterations it seems we don't need an extra handling.
        // the mailbox msg is set a bit later so that triggers a notification, but not the old messages.

        // We only send msg in case we are not the sender
        if (!disputeMsg.getSenderNodeAddress().equals(p2PService.getAddress())) {
            String shortId = disputeMsg.getShortId();
            MobileMessage message = new MobileMessage(Res.get("account.notifications.dispute.message.title"),
                    Res.get("account.notifications.dispute.message.msg", shortId),
                    shortId,
                    MobileMessageType.DISPUTE);
            try {
                mobileNotificationService.sendMessage(message);
            } catch (Exception e) {
                log.error(e.toString());
                e.printStackTrace();
            }
        }
    }

    public static MobileMessage getTestMsg() {
        String shortId = UUID.randomUUID().toString().substring(0, 8);
        return new MobileMessage(Res.get("account.notifications.dispute.message.title"),
                Res.get("account.notifications.dispute.message.msg", shortId),
                shortId,
                MobileMessageType.DISPUTE);
    }
}
