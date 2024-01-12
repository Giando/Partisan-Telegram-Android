package org.telegram.messenger.fakepasscode;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Pair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.android.exoplayer2.util.Consumer;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RemoveAfterReadingMessages {
    private static class MessageLoader implements NotificationCenter.NotificationCenterDelegate {
        private final int classGuid = ConnectionsManager.generateClassGuid();
        private final Consumer<MessageObject> consumer;

        private MessageLoader(Consumer<MessageObject> consumer) {
            this.consumer = consumer;
        }

        public static void load(int accountNum, long dialogId, int messageId, Consumer<MessageObject> consumer) {
            new MessageLoader(consumer).loadInternal(accountNum, dialogId, messageId);
        }

        public void loadInternal(int accountNum, long dialogId, int messageId) {
            NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.messagesDidLoad);
            MessagesController.getInstance(accountNum).loadMessages(dialogId, 0, false, 1, messageId + 1, 0, false, 0, classGuid, 0, 0, 0, 0, 0, 1, false);
        }

        @Override
        public synchronized void didReceivedNotification(int id, int account, Object... args) {
            if ((int)args[10] == classGuid) {
                NotificationCenter.getInstance(account).removeObserver(this, NotificationCenter.messagesDidLoad);
                if ((int)args[1] >= 1) {
                    consumer.accept(((ArrayList<MessageObject>)args[2]).get(0));
                }
            }
        }
    }

    public static Map<String, Map<String, List<RemoveAsReadMessage>>> messagesToRemoveAsRead = new HashMap<>();
    public static Map<String, Integer> delays = new HashMap<>();
    private static final Object sync = new Object();
    private static boolean isLoaded = false;

    public static void load() {
        synchronized (sync) {
            if (isLoaded) {
                return;
            }

            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("removeasreadmessages", Context.MODE_PRIVATE);
                ObjectMapper mapper = new ObjectMapper();
                mapper.enableDefaultTyping();
                String messagesToRemoveAsReadString = preferences.getString("messagesToRemoveAsRead", null);
                if (messagesToRemoveAsReadString != null) {
                    messagesToRemoveAsRead = mapper.readValue(messagesToRemoveAsReadString, HashMap.class);
                }
                String delaysString = preferences.getString("delays", null);
                if (delays != null) {
                    delays = mapper.readValue(delaysString, HashMap.class);
                }
                isLoaded = true;
            } catch (Exception e) {
                Utils.handleException(e);
            }
        }
    }

    public static void save() {
        synchronized (sync) {
            try {
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("removeasreadmessages", Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                ObjectMapper mapper = new ObjectMapper();
                mapper.enableDefaultTyping();
                String messagesToRemoveAsReadString = mapper.writeValueAsString(messagesToRemoveAsRead);
                String delaysString = mapper.writeValueAsString(delays);
                editor.putString("messagesToRemoveAsRead", messagesToRemoveAsReadString);
                editor.putString("delays", delaysString);
                editor.commit();
            } catch (Exception e) {
                Utils.handleException(e);
            }
        }
    }

    private static void removeMessage(int currentAccount, long dialogId, int messageId) {
        List<RemoveAsReadMessage> messagesToRemove = getDialogMessagesToRemove(currentAccount, dialogId);
        if (messagesToRemove == null) {
            return;
        }

        for (RemoveAsReadMessage messageToRemove : new ArrayList<>(messagesToRemove)) {
            if (messageToRemove.getId() == messageId) {
                messagesToRemove.remove(messageToRemove);
                break;
            }
        }

        if (messagesToRemove.isEmpty()) {
            messagesToRemoveAsRead.get("" + currentAccount).remove("" + dialogId);
        }
        save();
    }

    public static void checkReadDialogs(int currentAccount) {
        MessagesController controller = MessagesController.getInstance(currentAccount);
        if (!controller.dialogsLoaded) {
            AndroidUtilities.runOnUIThread(() -> checkReadDialogs(currentAccount), 100);
            return;
        }
        for (int i = 0; i < controller.dialogs_dict.size(); i++) {
            TLRPC.Dialog dialog = controller.dialogs_dict.valueAt(i);
            if (dialog == null) {
                continue;
            }
            List<RemoveAsReadMessage> messagesToCheck = getDialogMessagesToRemove(currentAccount, dialog.id);
            if (messagesToCheck == null) {
                continue;
            }
            if (DialogObject.isEncryptedDialog(dialog.id)) {
                for (RemoveAsReadMessage messageToRemove : messagesToCheck) {
                    MessageLoader.load(currentAccount, dialog.id, messageToRemove.getId(), message -> {
                        if (!message.isUnread()) {
                            startEncryptedDialogDeleteProcess(currentAccount, dialog.id, messageToRemove);
                        }
                    });
                }
            } else {
                readMaxIdUpdated(currentAccount, dialog.id, dialog.read_outbox_max_id);
            }
        }
    }

    public static void notifyMessagesRead(int currentAccount, List<Pair<Long, Integer>> messages) {
        Map<Long, Integer> dialogLastReadIds = new HashMap<>();
        Map<Long, List<RemoveAsReadMessage>> encryptedMessagesToDelete = new HashMap<>();
        for (Pair<Long, Integer> message : messages) {
            long dialogId = message.first;
            int messageId = message.second;
            if (DialogObject.isEncryptedDialog(dialogId)) {
                List<RemoveAsReadMessage> messagesToCheck = getDialogMessagesToRemove(currentAccount, dialogId);
                if (messagesToCheck == null) {
                    continue;
                }
                for (RemoveAsReadMessage messageToCheck : messagesToCheck) {
                    if (messageToCheck.getId() == messageId && !messageToCheck.isRead()) {
                        messageToCheck.setReadTime(System.currentTimeMillis());
                        encryptedMessagesToDelete.put(dialogId, new ArrayList<>());
                        encryptedMessagesToDelete.get(dialogId).add(messageToCheck);
                    }
                }
            } else if (dialogLastReadIds.getOrDefault(dialogId, 0) < messageId) {
                dialogLastReadIds.put(dialogId, messageId);
            }
        }
        for (Map.Entry<Long, Integer> entry : dialogLastReadIds.entrySet()) {
            readMaxIdUpdated(currentAccount, entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Long, List<RemoveAsReadMessage>> entry : encryptedMessagesToDelete.entrySet()) {
            long dialogId = entry.getKey();
            for (RemoveAsReadMessage removeAsReadMessage : entry.getValue()) {
                startEncryptedDialogDeleteProcess(currentAccount, dialogId, removeAsReadMessage);
            }
        }
    }

    public static void readMaxIdUpdated(int currentAccount, long currentDialogId, int readMaxId) {
        List<RemoveAsReadMessage> dialogMessagesToRemove = getDialogMessagesToRemove(currentAccount, currentDialogId);
        if (dialogMessagesToRemove == null || DialogObject.isEncryptedDialog(currentDialogId)) {
            return;
        }
        List<RemoveAsReadMessage> messagesToRemove = new ArrayList<>();
        for (RemoveAsReadMessage messageToRemove : dialogMessagesToRemove) {
            if (!messageToRemove.isRead() && messageToRemove.getId() <= readMaxId) {
                messagesToRemove.add(messageToRemove);
                messageToRemove.setReadTime(System.currentTimeMillis());
            }
        }
        save();
        startDeleteProcess(currentAccount, currentDialogId, messagesToRemove);
    }

    private static void startDeleteProcess(int currentAccount, long currentDialogId, List<RemoveAsReadMessage> messagesToRemove) {
        for (RemoveAsReadMessage messageToRemove : messagesToRemove) {
            if (!messageToRemove.isRead()) {
                continue;
            }
            AndroidUtilities.runOnUIThread(() -> {
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageToRemove.getId());
                MessagesController.getInstance(currentAccount).deleteMessages(ids, null, null, currentDialogId,
                        true, false, false, 0,
                        null, false, false);
                removeMessage(currentAccount, currentDialogId, messageToRemove.getId());
            }, messageToRemove.calculateRemainingDelay());
        }
    }

    public static void encryptedReadMaxTimeUpdated(int currentAccount, long currentDialogId, int readMaxTime) {
        List<RemoveAsReadMessage> dialogMessagesToRemove = getDialogMessagesToRemove(currentAccount, currentDialogId);
        if (dialogMessagesToRemove == null || !DialogObject.isEncryptedDialog(currentDialogId)) {
            return;
        }
        List<RemoveAsReadMessage> messagesToRemove = new ArrayList<>();
        for (RemoveAsReadMessage messageToRemove : dialogMessagesToRemove) {
            if (!messageToRemove.isRead() && messageToRemove.getSendTime() - 1 <= readMaxTime) {
                messagesToRemove.add(messageToRemove);
                messageToRemove.setReadTime(System.currentTimeMillis());
            }
        }
        save();
        for (RemoveAsReadMessage messageToRemove : messagesToRemove) {
            startEncryptedDialogDeleteProcess(currentAccount, currentDialogId, messageToRemove);
        }
    }

    public static void startEncryptedDialogDeleteProcess(int currentAccount, long currentDialogId, RemoveAsReadMessage messageToRemove) {
        if (!messageToRemove.isRead()) {
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            if (DialogObject.isEncryptedDialog(currentDialogId)) {
                ArrayList<Integer> ids = new ArrayList<>();
                ids.add(messageToRemove.getId());
                ArrayList<Long> random_ids = new ArrayList<>();
                random_ids.add(messageToRemove.getRandomId());
                Integer encryptedChatId = DialogObject.getEncryptedChatId(currentDialogId);
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount)
                        .getEncryptedChat(encryptedChatId);

                MessagesController.getInstance(currentAccount).deleteMessages(ids, random_ids,
                        encryptedChat, currentDialogId, false, false,
                        false, 0, null, false, false);
            }
            removeMessage(currentAccount, currentDialogId, messageToRemove.getId());
        }, messageToRemove.calculateRemainingDelay());
    }

    public static void addMessageToRemove(int accountNum, long dialogId, RemoveAsReadMessage messageToRemove) {
        load();
        messagesToRemoveAsRead.putIfAbsent("" + accountNum, new HashMap<>());
        Map<String, List<RemoveAsReadMessage>> dialogs = messagesToRemoveAsRead.get("" + accountNum);
        dialogs.putIfAbsent("" + dialogId, new ArrayList<>());
        dialogs.get("" + dialogId).add(messageToRemove);
        save();
    }

    public static List<RemoveAsReadMessage> getDialogMessagesToRemove(int accountNum, long dialogId) {
        load();
        Map<String, List<RemoveAsReadMessage>> dialogs = messagesToRemoveAsRead.get("" + accountNum);
        return dialogs != null ? dialogs.get("" + dialogId) : null;
    }

    public static void updateMessage(int accountNum, long dialogId, int oldMessageId, int newMessageId, Integer newSendTime) {
        List<RemoveAsReadMessage> removeAsReadMessages = getDialogMessagesToRemove(accountNum, dialogId);
        if (removeAsReadMessages != null) {
            for (RemoveAsReadMessage message : removeAsReadMessages) {
                if (message.getId() == oldMessageId) {
                    message.setId(newMessageId);
                    if (newSendTime != null) {
                        message.setSendTime(newSendTime);
                    }
                    RemoveAfterReadingMessages.save();
                    break;
                }
            }
        }
    }
}
