package org.telegram.messenger.partisan.verification;

import org.telegram.messenger.MessageObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class VerificationMessageParser {
    public static class ParsingResult {
        List<VerificationChatInfo> chatsToAdd = new ArrayList<>();
        Set<Long> chatsToRemove = new HashSet<>();
    }
    private int currentChatType;

    public ParsingResult parseMessage(MessageObject message) {
        if (message.messageText == null) {
            return null;
        }

        ParsingResult result = new ParsingResult();
        try {
            String[] lines = message.messageText.toString().split("\n");
            for (String line : lines) {
                if (line.startsWith("#")) {
                    processControlLine(line.substring(1));
                } else if (line.startsWith("+")) {
                    result.chatsToAdd.add(parseChatInfo(line.substring(1)));
                } else if (line.startsWith("-")) {
                    VerificationChatInfo info = parseChatInfo(line.substring(1));
                    result.chatsToRemove.add(info.chatId);
                }
            }
            return result;
        } catch (Exception ignore) {
        }
        return null;
    }

    private VerificationChatInfo parseChatInfo(String chatInfoStr) {
        VerificationChatInfo info = new VerificationChatInfo();
        info.type = currentChatType;
        if (chatInfoStr.contains("=")) {
            String[] parts = chatInfoStr.split("=");
            info.username = parts[0]
                    .replace("@", "")
                    .replace("https://t.me/", "")
                    .replace("http://t.me/", "")
                    .replace("t.me/", "");
            info.chatId = Math.abs(Long.parseLong(parts[1]));
        } else {
            info.username = null;
            info.chatId = Math.abs(Long.parseLong(chatInfoStr));
        }
        return info;
    }

    private void processControlLine(String command) {
        if (command.equals("verified")) {
            currentChatType = VerificationRepository.TYPE_VERIFIED;
        } else if (command.equals("scam")) {
            currentChatType = VerificationRepository.TYPE_SCAM;
        } else if (command.equals("fake")) {
            currentChatType = VerificationRepository.TYPE_FAKE;
        }
    }
}
