package org.telegram.messenger.fakepasscode;

import com.fasterxml.jackson.annotation.JsonIgnore;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.StickersActivity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.telegram.messenger.MediaDataController.TYPE_IMAGE;

@FakePasscodeSerializer.ToggleSerialization
public class DeleteStickersAction extends AccountAction implements NotificationCenter.NotificationCenterDelegate {
    @JsonIgnore
    private static final int STEP_DELETE_REGULAR_STICKERS = 0;
    @JsonIgnore
    private static final int STEP_DELETE_ARCHIVED_STICKERS = 1;
    @JsonIgnore
    private int step = STEP_DELETE_REGULAR_STICKERS;
    @JsonIgnore
    private long lastUpdateTime = 0;
    private boolean preventBulletin = false;

    @Override
    public void execute(FakePasscode fakePasscode) {
        step = STEP_DELETE_REGULAR_STICKERS;
        lastUpdateTime = 0;
        loadStickers();
        //delete recent emoji
        Emoji.clearRecentEmoji();
        // delete recent gif
        for (TLRPC.Document document : MediaDataController.getInstance(accountNum).getRecentGifs()) {
            MediaDataController.getInstance(accountNum).removeRecentGif(document);
        }
    }

    private void loadStickers() {
        preventBulletin = true;
        NotificationCenter.getInstance(accountNum).addObserver(this, NotificationCenter.stickersDidLoad);
        MediaDataController.getInstance(accountNum).loadStickers(TYPE_IMAGE, false, false, true, s -> {
            deleteStickers();
            preventBulletin = false;
        });
    }

    private void deleteArchivedStickers() {
        TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
        req.offset_id = 0;
        req.limit = 100;
        req.masks = false;
        ConnectionsManager.getInstance(accountNum).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                processArchivedStickersResponse((TLRPC.TL_messages_archivedStickers) response);
            }
        }));
    }


    private void processArchivedStickersResponse(TLRPC.TL_messages_archivedStickers res) {
        MediaDataController controller = MediaDataController.getInstance(accountNum);
        for (TLRPC.StickerSetCovered set : res.sets) {
            controller.toggleStickerSet(null, set, 2, null, false, false);
        }
        AndroidUtilities.runOnUIThread(this::loadStickers);
    }

    private synchronized void deleteStickers() {
        MediaDataController controller = MediaDataController.getInstance(accountNum);
        List<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>(controller.getStickerSets(TYPE_IMAGE));
        for (TLRPC.TL_messages_stickerSet stickerSet : stickerSets) {
            AndroidUtilities.runOnUIThread(() -> controller.toggleStickerSet(null, stickerSet, 0, null, false, false));
        }
        for (int recent_sticker_type = 0; recent_sticker_type < 8; recent_sticker_type++) {
            for (TLRPC.Document document : controller.getRecentStickers(recent_sticker_type)) {
                controller.addRecentSticker(recent_sticker_type, null, document, 0, true, false);
            }
        }
        controller.clearRecentStickers();
    }

    @Override
    public synchronized void didReceivedNotification(int id, int account, Object... args) {
        if (account != accountNum) {
            return;
        }
        if (lastUpdateTime != 0 && System.currentTimeMillis() - lastUpdateTime > 5000) {
            step = STEP_DELETE_REGULAR_STICKERS; // reset step
            NotificationCenter.getInstance(accountNum).removeObserver(this, NotificationCenter.stickersDidLoad);
            return;
        } else {
            lastUpdateTime = System.currentTimeMillis();
        }
        deleteStickers();
        if (step == STEP_DELETE_REGULAR_STICKERS) {
            step = STEP_DELETE_ARCHIVED_STICKERS;
            deleteArchivedStickers();
        }
    }

    public boolean isPreventBulletin() {
        return preventBulletin;
    }
}
