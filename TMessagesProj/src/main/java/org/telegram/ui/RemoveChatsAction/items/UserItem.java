package org.telegram.ui.RemoveChatsAction.items;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;

class UserItem extends AbstractUserItem {
    UserItem(int accountNum, TLRPC.User user) {
        super(accountNum, user);
    }

    @Override
    public Long getId() {
        return user.id;
    }

    @Override
    public String getAlternativeName() {
        if (UserObject.isReplyUser(user)) {
            return LocaleController.getString("RepliesTitle", R.string.RepliesTitle);
        } else if (user.self) {
            return LocaleController.getString("SavedMessages", R.string.SavedMessages);
        } else {
            return null;
        }
    }

    @Override
    public boolean isSelf() {
        return UserObject.isUserSelf(user);
    }

    @Override
    public CharSequence getStatus() {
        if (isSelf()) {
            return LocaleController.getString(R.string.SavedMessages);
        } else {
            CharSequence status = super.getStatus();
            if ("".contentEquals(status)) {
                if (getMessagesController().getAllDialogs().stream().noneMatch(d -> d.id == getId())) {
                    if (isBlocked()) {
                        status = LocaleController.getString(R.string.BlockedUsers);
                    } else {
                        status = LocaleController.getString(R.string.ChatRemoved);
                    }
                }
            }
            return status;
        }
    }

    private boolean isBlocked() {
        return getMessagesController().getUnfilteredBlockedPeers().get(user.id) == 1;
    }
}
