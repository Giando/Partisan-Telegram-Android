/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import android.animation.ValueAnimator;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.DialogRadioCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.PollEditTextCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell2;
import org.telegram.ui.Cells.TextDetailCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Cells.UserCell2;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedTextView;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.CircularProgressDrawable;
import org.telegram.ui.Components.CrossfadeDrawable;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.LimitReachedBottomSheet;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Calendar;

public class ChatRightsEditActivity extends BaseFragment {

    private ListAdapter listViewAdapter;
    private RecyclerListView listView;
    private LinearLayoutManager linearLayoutManager;

    private FrameLayout addBotButtonContainer;
    private FrameLayout addBotButton;
    private AnimatedTextView addBotButtonText;
    private PollEditTextCell rankEditTextCell;
    private CrossfadeDrawable doneDrawable;

    private long chatId;
    private TLRPC.User currentUser;
    private TLRPC.Chat currentChat;
    private int currentType;
    private boolean isChannel;
    private boolean isForum;
    private boolean loading = false;

    private boolean canEdit;

    private float asAdminT = 0;
    private boolean asAdmin = false;
    private boolean initialAsAdmin = false;
    private TLRPC.TL_chatAdminRights adminRights;
    private TLRPC.TL_chatAdminRights myAdminRights;
    private TLRPC.TL_chatBannedRights bannedRights;
    private TLRPC.TL_chatBannedRights defaultBannedRights;
    private String currentBannedRights = "";
    private String currentRank;
    private String initialRank;

    private int rowCount;
    private int manageRow;
    private int permissionsStartRow;
    private int permissionsEndRow;
    private int changeInfoRow;
    private int postMessagesRow;
    private int editMesagesRow;
    private int deleteMessagesRow;
    private int addAdminsRow;
    private int anonymousRow;
    private int banUsersRow;
    private int addUsersRow;
    private int pinMessagesRow;
    private int manageTopicsRow;
    private int rightsShadowRow;
    private int removeAdminRow;
    private int removeAdminShadowRow;
    private int cantEditInfoRow;
    private int transferOwnerShadowRow;
    private int transferOwnerRow;
    private int rankHeaderRow;
    private int rankRow;
    private int rankInfoRow;
    private int addBotButtonRow;

    private int sendMessagesRow;
    private int sendMediaRow;
    private int sendStickersRow;
    private int sendPollsRow;
    private int embedLinksRow;
    private int startVoiceChatRow;
    private int untilSectionRow;
    private int untilDateRow;

    private ChatRightsEditActivityDelegate delegate;

    private String botHash;
    private boolean isAddingNew;
    private boolean initialIsSet;

    public static final int TYPE_ADMIN = 0;
    public static final int TYPE_BANNED = 1;
    public static final int TYPE_ADD_BOT = 2;

    private boolean closingKeyboardAfterFinish = false;

    public interface ChatRightsEditActivityDelegate {
        void didSetRights(int rights, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBanned, String rank);
        void didChangeOwner(TLRPC.User user);
    }

    private final static int done_button = 1;

    public ChatRightsEditActivity(long userId, long channelId, TLRPC.TL_chatAdminRights rightsAdmin, TLRPC.TL_chatBannedRights rightsBannedDefault, TLRPC.TL_chatBannedRights rightsBanned, String rank, int type, boolean edit, boolean addingNew, String addingNewBotHash) {
        super();
        isAddingNew = addingNew;
        chatId = channelId;
        currentUser = MessagesController.getInstance(currentAccount).getUser(userId);
        currentType = type;
        canEdit = edit;
        botHash = addingNewBotHash;
        currentChat = MessagesController.getInstance(currentAccount).getChat(chatId);
        if (rank == null) {
            rank = "";
        }
        initialRank = currentRank = rank;
        if (currentChat != null) {
            isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
            isForum = ChatObject.isForum(currentChat);
            myAdminRights = currentChat.admin_rights;
        }
        if (myAdminRights == null) {
            myAdminRights = emptyAdminRights(currentType != TYPE_ADD_BOT || (currentChat != null && currentChat.creator));
        }
        if (type == TYPE_ADMIN || type == TYPE_ADD_BOT) {
            if (type == TYPE_ADD_BOT) {
                TLRPC.UserFull userFull = getMessagesController().getUserFull(userId);
                if (userFull != null) {
                    TLRPC.TL_chatAdminRights botDefaultRights = isChannel ? userFull.bot_broadcast_admin_rights : userFull.bot_group_admin_rights;
                    if (botDefaultRights != null) {
                        if (rightsAdmin == null) {
                            rightsAdmin = botDefaultRights;
                        } else {
                            rightsAdmin.ban_users = rightsAdmin.ban_users || botDefaultRights.ban_users;
                            rightsAdmin.add_admins = rightsAdmin.add_admins || botDefaultRights.add_admins;
                            rightsAdmin.post_messages = rightsAdmin.post_messages || botDefaultRights.post_messages;
                            rightsAdmin.pin_messages = rightsAdmin.pin_messages || botDefaultRights.pin_messages;
                            rightsAdmin.delete_messages = rightsAdmin.delete_messages || botDefaultRights.delete_messages;
                            rightsAdmin.change_info = rightsAdmin.change_info || botDefaultRights.change_info;
                            rightsAdmin.anonymous = rightsAdmin.anonymous || botDefaultRights.anonymous;
                            rightsAdmin.edit_messages = rightsAdmin.edit_messages || botDefaultRights.edit_messages;
                            rightsAdmin.manage_call = rightsAdmin.manage_call || botDefaultRights.manage_call;
                            rightsAdmin.manage_topics = rightsAdmin.manage_topics || botDefaultRights.manage_topics;
                            rightsAdmin.other = rightsAdmin.other || botDefaultRights.other;
                        }
                    }
                }
            }
            if (rightsAdmin == null) {
                initialAsAdmin = false;
                if (type == TYPE_ADD_BOT) {
                    adminRights = emptyAdminRights(false);
                    asAdmin = isChannel;
                    asAdminT = asAdmin ? 1 : 0;
                    initialIsSet = false;
                } else {
                    adminRights = new TLRPC.TL_chatAdminRights();
                    adminRights.change_info = myAdminRights.change_info;
                    adminRights.post_messages = myAdminRights.post_messages;
                    adminRights.edit_messages = myAdminRights.edit_messages;
                    adminRights.delete_messages = myAdminRights.delete_messages;
                    adminRights.manage_call = myAdminRights.manage_call;
                    adminRights.ban_users = myAdminRights.ban_users;
                    adminRights.invite_users = myAdminRights.invite_users;
                    adminRights.pin_messages = myAdminRights.pin_messages;
                    adminRights.manage_topics = myAdminRights.manage_topics;
                    adminRights.other = myAdminRights.other;
                    initialIsSet = false;
                }
            } else {
                initialAsAdmin = true;
                adminRights = new TLRPC.TL_chatAdminRights();
                adminRights.change_info = rightsAdmin.change_info;
                adminRights.post_messages = rightsAdmin.post_messages;
                adminRights.edit_messages = rightsAdmin.edit_messages;
                adminRights.delete_messages = rightsAdmin.delete_messages;
                adminRights.manage_call = rightsAdmin.manage_call;
                adminRights.ban_users = rightsAdmin.ban_users;
                adminRights.invite_users = rightsAdmin.invite_users;
                adminRights.pin_messages = rightsAdmin.pin_messages;
                adminRights.manage_topics = rightsAdmin.manage_topics;
                adminRights.add_admins = rightsAdmin.add_admins;
                adminRights.anonymous = rightsAdmin.anonymous;
                adminRights.other = rightsAdmin.other;

                initialIsSet = adminRights.change_info || adminRights.post_messages || adminRights.edit_messages ||
                        adminRights.delete_messages || adminRights.ban_users || adminRights.invite_users ||
                        adminRights.pin_messages || adminRights.add_admins || adminRights.manage_call || adminRights.anonymous || adminRights.manage_topics || adminRights.other;

                if (type == TYPE_ADD_BOT) {
                    asAdmin = isChannel || initialIsSet;
                    asAdminT = asAdmin ? 1 : 0;
                    initialIsSet = false;
                }
            }

            if (currentChat != null) {
                defaultBannedRights = currentChat.default_banned_rights;
            }
            if (defaultBannedRights == null) {
                defaultBannedRights = new TLRPC.TL_chatBannedRights();
                defaultBannedRights.view_messages = defaultBannedRights.send_media = defaultBannedRights.send_messages =
                    defaultBannedRights.embed_links = defaultBannedRights.send_stickers = defaultBannedRights.send_gifs =
                    defaultBannedRights.send_games = defaultBannedRights.send_inline = defaultBannedRights.send_polls =
                    defaultBannedRights.invite_users = defaultBannedRights.change_info = defaultBannedRights.pin_messages =
                    defaultBannedRights.manage_topics = true;
            }

            if (!defaultBannedRights.change_info) {
                adminRights.change_info = true;
            }
            if (!defaultBannedRights.pin_messages) {
                adminRights.pin_messages = true;
            }
        } else if (type == TYPE_BANNED) {
            defaultBannedRights = rightsBannedDefault;
            if (defaultBannedRights == null) {
                defaultBannedRights = new TLRPC.TL_chatBannedRights();
                defaultBannedRights.view_messages = defaultBannedRights.send_media = defaultBannedRights.send_messages =
                defaultBannedRights.embed_links = defaultBannedRights.send_stickers = defaultBannedRights.send_gifs =
                defaultBannedRights.send_games = defaultBannedRights.send_inline = defaultBannedRights.send_polls =
                defaultBannedRights.invite_users = defaultBannedRights.change_info = defaultBannedRights.pin_messages =
                defaultBannedRights.manage_topics = false;
            }

            bannedRights = new TLRPC.TL_chatBannedRights();
            if (rightsBanned == null) {
                bannedRights.view_messages = bannedRights.send_media = bannedRights.send_messages =
                bannedRights.embed_links = bannedRights.send_stickers = bannedRights.send_gifs =
                bannedRights.send_games = bannedRights.send_inline = bannedRights.send_polls =
                bannedRights.invite_users = bannedRights.change_info = bannedRights.pin_messages =
                bannedRights.manage_topics = false;
            } else {
                bannedRights.view_messages = rightsBanned.view_messages;
                bannedRights.send_messages = rightsBanned.send_messages;
                bannedRights.send_media = rightsBanned.send_media;
                bannedRights.send_stickers = rightsBanned.send_stickers;
                bannedRights.send_gifs = rightsBanned.send_gifs;
                bannedRights.send_games = rightsBanned.send_games;
                bannedRights.send_inline = rightsBanned.send_inline;
                bannedRights.embed_links = rightsBanned.embed_links;
                bannedRights.send_polls = rightsBanned.send_polls;
                bannedRights.invite_users = rightsBanned.invite_users;
                bannedRights.change_info = rightsBanned.change_info;
                bannedRights.pin_messages = rightsBanned.pin_messages;
                bannedRights.until_date = rightsBanned.until_date;
                bannedRights.manage_topics = rightsBanned.manage_topics;
            }
            if (defaultBannedRights.view_messages) {
                bannedRights.view_messages = true;
            }
            if (defaultBannedRights.send_messages) {
                bannedRights.send_messages = true;
            }
            if (defaultBannedRights.send_media) {
                bannedRights.send_media = true;
            }
            if (defaultBannedRights.send_stickers) {
                bannedRights.send_stickers = true;
            }
            if (defaultBannedRights.send_gifs) {
                bannedRights.send_gifs = true;
            }
            if (defaultBannedRights.send_games) {
                bannedRights.send_games = true;
            }
            if (defaultBannedRights.send_inline) {
                bannedRights.send_inline = true;
            }
            if (defaultBannedRights.embed_links) {
                bannedRights.embed_links = true;
            }
            if (defaultBannedRights.send_polls) {
                bannedRights.send_polls = true;
            }
            if (defaultBannedRights.invite_users) {
                bannedRights.invite_users = true;
            }
            if (defaultBannedRights.change_info) {
                bannedRights.change_info = true;
            }
            if (defaultBannedRights.pin_messages) {
                bannedRights.pin_messages = true;
            }
            if (defaultBannedRights.manage_topics) {
                bannedRights.manage_topics = true;
            }

            currentBannedRights = ChatObject.getBannedRightsString(bannedRights);

            initialIsSet = rightsBanned == null || !rightsBanned.view_messages;
        }
        updateRows(false);
    }

    public static TLRPC.TL_chatAdminRights emptyAdminRights(boolean value) {
        TLRPC.TL_chatAdminRights adminRights = new TLRPC.TL_chatAdminRights();
        adminRights.change_info = adminRights.post_messages = adminRights.edit_messages =
            adminRights.delete_messages = adminRights.ban_users = adminRights.invite_users =
            adminRights.pin_messages = adminRights.add_admins = adminRights.manage_call = adminRights.manage_topics = value;
        return adminRights;
    }


    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);

        if (currentType == TYPE_ADMIN) {
            actionBar.setTitle(LocaleController.getString("EditAdmin", R.string.EditAdmin));
        } else if (currentType == TYPE_ADD_BOT) {
            actionBar.setTitle(LocaleController.getString("AddBot", R.string.AddBot));
        } else {
            actionBar.setTitle(LocaleController.getString("UserRestrictions", R.string.UserRestrictions));
        }

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (checkDiscard()) {
                        finishFragment();
                    }
                } else if (id == done_button) {
                    onDonePressed();
                }
            }
        });

        if (canEdit || !isChannel && currentChat.creator && UserObject.isUserSelf(currentUser)) {
            ActionBarMenu menu = actionBar.createMenu();
            Drawable checkmark = context.getResources().getDrawable(R.drawable.ic_ab_done).mutate();
            checkmark.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_actionBarDefaultIcon), PorterDuff.Mode.MULTIPLY));
            doneDrawable = new CrossfadeDrawable(checkmark, new CircularProgressDrawable(Theme.getColor(Theme.key_actionBarDefaultIcon)));
            menu.addItemWithWidth(done_button, 0, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
            menu.getItem(done_button).setIcon(doneDrawable);
        }

        fragmentView = new FrameLayout(context) {
            private int previousHeight = -1;
            @Override
            protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                int height = bottom - top;
                if (previousHeight != -1 && Math.abs(previousHeight - height) > AndroidUtilities.dp(20)) {
                    listView.smoothScrollToPosition(rowCount - 1);
                }
                previousHeight = height;
            }
        };
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        fragmentView.setFocusableInTouchMode(true);

        listView = new RecyclerListView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent e) {
                if (loading) {
                    return false;
                }
                return super.onTouchEvent(e);
            }

            @Override
            public boolean onInterceptTouchEvent(MotionEvent e) {
                if (loading) {
                    return false;
                }
                return super.onInterceptTouchEvent(e);
            }
        };
        listView.setClipChildren(currentType != TYPE_ADD_BOT);
        linearLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
            @Override
            protected int getExtraLayoutSpace(RecyclerView.State state) {
                return 5000;
            }
        };
        linearLayoutManager.setInitialPrefetchItemCount(100);
        listView.setLayoutManager(linearLayoutManager);
        listView.setAdapter(listViewAdapter = new ListAdapter(context));
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        if (currentType == TYPE_ADD_BOT) {
            listView.setResetSelectorOnChanged(false);
        }
        itemAnimator.setDelayAnimations(false);
        listView.setItemAnimator(itemAnimator);
        listView.setVerticalScrollbarPosition(LocaleController.isRTL ? RecyclerListView.SCROLLBAR_POSITION_LEFT : RecyclerListView.SCROLLBAR_POSITION_RIGHT);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
                }
            }
        });

        listView.setOnItemClickListener((view, position) -> {
            if (!canEdit && (!currentChat.creator || currentType != TYPE_ADMIN || position != anonymousRow)) {
                return;
            }
            if (position == 0) {
                Bundle args = new Bundle();
                args.putLong("user_id", currentUser.id);
                presentFragment(new ProfileActivity(args));
            } else if (position == removeAdminRow) {
                if (currentType == TYPE_ADMIN) {
                    MessagesController.getInstance(currentAccount).setUserAdminRole(chatId, currentUser, new TLRPC.TL_chatAdminRights(), currentRank, isChannel, getFragmentForAlert(0), isAddingNew, false, null,null);
                    if (delegate != null) {
                        delegate.didSetRights(0, adminRights, bannedRights, currentRank);
                    }
                    finishFragment();
                } else if (currentType == TYPE_BANNED) {
                    bannedRights = new TLRPC.TL_chatBannedRights();
                    bannedRights.view_messages = true;
                    bannedRights.send_media = true;
                    bannedRights.send_messages = true;
                    bannedRights.send_stickers = true;
                    bannedRights.send_gifs = true;
                    bannedRights.send_games = true;
                    bannedRights.send_inline = true;
                    bannedRights.embed_links = true;
                    bannedRights.pin_messages = true;
                    bannedRights.send_polls = true;
                    bannedRights.invite_users = true;
                    bannedRights.change_info = true;
                    bannedRights.manage_topics = true;
                    bannedRights.until_date = 0;
                    onDonePressed();
                }
            } else if (position == transferOwnerRow) {
                initTransfer(null, null);
            } else if (position == untilDateRow) {
                if (getParentActivity() == null) {
                    return;
                }
                BottomSheet.Builder builder = new BottomSheet.Builder(context);
                builder.setApplyTopPadding(false);

                LinearLayout linearLayout = new LinearLayout(context);
                linearLayout.setOrientation(LinearLayout.VERTICAL);

                HeaderCell headerCell = new HeaderCell(context, Theme.key_dialogTextBlue2, 23, 15, false);
                headerCell.setHeight(47);
                headerCell.setText(LocaleController.getString("UserRestrictionsDuration", R.string.UserRestrictionsDuration));
                linearLayout.addView(headerCell);

                LinearLayout linearLayoutInviteContainer = new LinearLayout(context);
                linearLayoutInviteContainer.setOrientation(LinearLayout.VERTICAL);
                linearLayout.addView(linearLayoutInviteContainer, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                BottomSheet.BottomSheetCell[] buttons = new BottomSheet.BottomSheetCell[5];

                for (int a = 0; a < buttons.length; a++) {
                    buttons[a] = new BottomSheet.BottomSheetCell(context, 0);
                    buttons[a].setPadding(AndroidUtilities.dp(7), 0, AndroidUtilities.dp(7), 0);
                    buttons[a].setTag(a);
                    buttons[a].setBackgroundDrawable(Theme.getSelectorDrawable(false));
                    String text;
                    switch (a) {
                        case 0:
                            text = LocaleController.getString("UserRestrictionsUntilForever", R.string.UserRestrictionsUntilForever);
                            break;
                        case 1:
                            text = LocaleController.formatPluralString("Days", 1);
                            break;
                        case 2:
                            text = LocaleController.formatPluralString("Weeks", 1);
                            break;
                        case 3:
                            text = LocaleController.formatPluralString("Months", 1);
                            break;
                        case 4:
                        default:
                            text = LocaleController.getString("UserRestrictionsCustom", R.string.UserRestrictionsCustom);
                            break;
                    }
                    buttons[a].setTextAndIcon(text, 0);
                    linearLayoutInviteContainer.addView(buttons[a], LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    buttons[a].setOnClickListener(v2 -> {
                        Integer tag = (Integer) v2.getTag();
                        switch (tag) {
                            case 0:
                                bannedRights.until_date = 0;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 1:
                                bannedRights.until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 2:
                                bannedRights.until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24 * 7;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 3:
                                bannedRights.until_date = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24 * 30;
                                listViewAdapter.notifyItemChanged(untilDateRow);
                                break;
                            case 4: {
                                Calendar calendar = Calendar.getInstance();
                                int year = calendar.get(Calendar.YEAR);
                                int monthOfYear = calendar.get(Calendar.MONTH);
                                int dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH);
                                try {
                                    DatePickerDialog dialog = new DatePickerDialog(getParentActivity(), (view1, year1, month, dayOfMonth1) -> {
                                        Calendar calendar1 = Calendar.getInstance();
                                        calendar1.clear();
                                        calendar1.set(year1, month, dayOfMonth1);
                                        final int time = (int) (calendar1.getTime().getTime() / 1000);
                                        try {
                                            TimePickerDialog dialog13 = new TimePickerDialog(getParentActivity(), (view11, hourOfDay, minute) -> {
                                                bannedRights.until_date = time + hourOfDay * 3600 + minute * 60;
                                                listViewAdapter.notifyItemChanged(untilDateRow);
                                            }, 0, 0, true);
                                            dialog13.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString("Set", R.string.Set), dialog13);
                                            dialog13.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog131, which) -> {

                                            });
                                            showDialog(dialog13);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }, year, monthOfYear, dayOfMonth);

                                    final DatePicker datePicker = dialog.getDatePicker();

                                    Calendar date = Calendar.getInstance();
                                    date.setTimeInMillis(System.currentTimeMillis());
                                    date.set(Calendar.HOUR_OF_DAY, date.getMinimum(Calendar.HOUR_OF_DAY));
                                    date.set(Calendar.MINUTE, date.getMinimum(Calendar.MINUTE));
                                    date.set(Calendar.SECOND, date.getMinimum(Calendar.SECOND));
                                    date.set(Calendar.MILLISECOND, date.getMinimum(Calendar.MILLISECOND));
                                    datePicker.setMinDate(date.getTimeInMillis());

                                    date.setTimeInMillis(System.currentTimeMillis() + 31536000000L);
                                    date.set(Calendar.HOUR_OF_DAY, date.getMaximum(Calendar.HOUR_OF_DAY));
                                    date.set(Calendar.MINUTE, date.getMaximum(Calendar.MINUTE));
                                    date.set(Calendar.SECOND, date.getMaximum(Calendar.SECOND));
                                    date.set(Calendar.MILLISECOND, date.getMaximum(Calendar.MILLISECOND));
                                    datePicker.setMaxDate(date.getTimeInMillis());

                                    dialog.setButton(DialogInterface.BUTTON_POSITIVE, LocaleController.getString("Set", R.string.Set), dialog);
                                    dialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog1, which) -> {

                                    });
                                    if (Build.VERSION.SDK_INT >= 21) {
                                        dialog.setOnShowListener(dialog12 -> {
                                            int count = datePicker.getChildCount();
                                            for (int b = 0; b < count; b++) {
                                                View child = datePicker.getChildAt(b);
                                                ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                                                layoutParams.width = LayoutHelper.MATCH_PARENT;
                                                child.setLayoutParams(layoutParams);
                                            }
                                        });
                                    }
                                    showDialog(dialog);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                                break;
                            }
                        }
                        builder.getDismissRunnable().run();
                    });
                }
                builder.setCustomView(linearLayout);
                showDialog(builder.create());
            } else if (view instanceof TextCheckCell2) {
                TextCheckCell2 checkCell = (TextCheckCell2) view;
                if (checkCell.hasIcon()) {
                    if (currentType != TYPE_ADD_BOT) {
                        new AlertDialog.Builder(getParentActivity())
                            .setTitle(LocaleController.getString("UserRestrictionsCantModify", R.string.UserRestrictionsCantModify))
                            .setMessage(LocaleController.getString("UserRestrictionsCantModifyDisabled", R.string.UserRestrictionsCantModifyDisabled))
                            .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                            .create()
                            .show();
                    }
                    return;
                }

                if (!checkCell.isEnabled()) {
                    if ((currentType == TYPE_ADD_BOT || currentType == TYPE_ADMIN) &&
                        (position == changeInfoRow && defaultBannedRights != null && !defaultBannedRights.change_info ||
                         position == pinMessagesRow && defaultBannedRights != null && !defaultBannedRights.pin_messages)) {
                        new AlertDialog.Builder(getParentActivity())
                            .setTitle(LocaleController.getString("UserRestrictionsCantModify", R.string.UserRestrictionsCantModify))
                            .setMessage(LocaleController.getString("UserRestrictionsCantModifyEnabled", R.string.UserRestrictionsCantModifyEnabled))
                            .setPositiveButton(LocaleController.getString("OK", R.string.OK), null)
                            .create()
                            .show();
                    }
                    return;
                }
                if (currentType != TYPE_ADD_BOT) {
                    checkCell.setChecked(!checkCell.isChecked());
                }
                boolean value = checkCell.isChecked();
                if (position == manageRow) {
                    value = asAdmin = !asAdmin;
                    updateAsAdmin(true);
                } else if (position == changeInfoRow) {
                    if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
                        value = adminRights.change_info = !adminRights.change_info;
                    } else {
                        value = bannedRights.change_info = !bannedRights.change_info;
                    }
                } else if (position == postMessagesRow) {
                    value = adminRights.post_messages = !adminRights.post_messages;
                } else if (position == editMesagesRow) {
                    value = adminRights.edit_messages = !adminRights.edit_messages;
                } else if (position == deleteMessagesRow) {
                    value = adminRights.delete_messages = !adminRights.delete_messages;
                } else if (position == addAdminsRow) {
                    value = adminRights.add_admins = !adminRights.add_admins;
                } else if (position == anonymousRow) {
                    value = adminRights.anonymous = !adminRights.anonymous;
                } else if (position == banUsersRow) {
                    value = adminRights.ban_users = !adminRights.ban_users;
                } else if (position == startVoiceChatRow) {
                    value = adminRights.manage_call = !adminRights.manage_call;
                } else if (position == manageTopicsRow) {
                    if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
                        value = adminRights.manage_topics = !adminRights.manage_topics;
                    } else {
                        value = bannedRights.manage_topics = !bannedRights.manage_topics;
                    }
                } else if (position == addUsersRow) {
                    if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
                        value = adminRights.invite_users = !adminRights.invite_users;
                    } else {
                        value = bannedRights.invite_users = !bannedRights.invite_users;
                    }
                } else if (position == pinMessagesRow) {
                    if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
                        value = adminRights.pin_messages = !adminRights.pin_messages;
                    } else {
                        value = bannedRights.pin_messages = !bannedRights.pin_messages;
                    }
                } else if (currentType == TYPE_BANNED && bannedRights != null) {
                    boolean disabled = !checkCell.isChecked();
                    if (position == sendMessagesRow) {
                        value = bannedRights.send_messages = !bannedRights.send_messages;
                    } else if (position == sendMediaRow) {
                        value = bannedRights.send_media = !bannedRights.send_media;
                    } else if (position == sendStickersRow) {
                        value = bannedRights.send_stickers = bannedRights.send_games = bannedRights.send_gifs = bannedRights.send_inline = !bannedRights.send_stickers;
                    } else if (position == embedLinksRow) {
                        value = bannedRights.embed_links = !bannedRights.embed_links;
                    } else if (position == sendPollsRow) {
                        value = bannedRights.send_polls = !bannedRights.send_polls;
                    }
                    if (disabled) {
                        if (bannedRights.view_messages && !bannedRights.send_messages) {
                            bannedRights.send_messages = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMessagesRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                        if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.send_media) {
                            bannedRights.send_media = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMediaRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                        if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.send_polls) {
                            bannedRights.send_polls = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendPollsRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                        if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.send_stickers) {
                            bannedRights.send_stickers = bannedRights.send_games = bannedRights.send_gifs = bannedRights.send_inline = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendStickersRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                        if ((bannedRights.view_messages || bannedRights.send_messages) && !bannedRights.embed_links) {
                            bannedRights.embed_links = true;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(embedLinksRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(false);
                            }
                        }
                    } else {
                        if ((!bannedRights.send_messages || !bannedRights.embed_links || !bannedRights.send_inline || !bannedRights.send_media || !bannedRights.send_polls) && bannedRights.view_messages) {
                            bannedRights.view_messages = false;
                        }
                        if ((!bannedRights.embed_links || !bannedRights.send_inline || !bannedRights.send_media || !bannedRights.send_polls) && bannedRights.send_messages) {
                            bannedRights.send_messages = false;
                            RecyclerListView.ViewHolder holder = listView.findViewHolderForAdapterPosition(sendMessagesRow);
                            if (holder != null) {
                                ((TextCheckCell2) holder.itemView).setChecked(true);
                            }
                        }
                    }
                }
                if (currentType == TYPE_ADD_BOT) {
                    checkCell.setChecked(asAdmin && value);
                }
                updateRows(true);
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listViewAdapter != null) {
            listViewAdapter.notifyDataSetChanged();
        }
        AndroidUtilities.requestAdjustResize(getParentActivity(), classGuid);
    }

    private boolean isDefaultAdminRights() {
        return adminRights.change_info && adminRights.delete_messages && adminRights.ban_users && adminRights.invite_users && adminRights.pin_messages && (!isForum || adminRights.manage_topics) && adminRights.manage_call && !adminRights.add_admins && !adminRights.anonymous ||
                !adminRights.change_info && !adminRights.delete_messages && !adminRights.ban_users && !adminRights.invite_users && !adminRights.pin_messages && (!isForum || !adminRights.manage_topics) && !adminRights.manage_call && !adminRights.add_admins && !adminRights.anonymous;
    }

    private boolean hasAllAdminRights() {
        if (isChannel) {
            return adminRights.change_info && adminRights.post_messages && adminRights.edit_messages && adminRights.delete_messages && adminRights.invite_users && adminRights.add_admins && adminRights.manage_call;
        } else {
            return adminRights.change_info && adminRights.delete_messages && adminRights.ban_users && adminRights.invite_users && adminRights.pin_messages && adminRights.add_admins && adminRights.manage_call && (!isForum || adminRights.manage_topics);
        }
    }

    private void initTransfer(TLRPC.InputCheckPasswordSRP srp, TwoStepVerificationActivity passwordFragment) {
        if (getParentActivity() == null) {
            return;
        }
        if (srp != null && !ChatObject.isChannel(currentChat)) {
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                if (param != 0) {
                    chatId = param;
                    currentChat = MessagesController.getInstance(currentAccount).getChat(param);
                    initTransfer(srp, passwordFragment);
                }
            });
            return;
        }
        TLRPC.TL_channels_editCreator req = new TLRPC.TL_channels_editCreator();
        if (ChatObject.isChannel(currentChat)) {
            req.channel = new TLRPC.TL_inputChannel();
            req.channel.channel_id = currentChat.id;
            req.channel.access_hash = currentChat.access_hash;
        } else {
            req.channel = new TLRPC.TL_inputChannelEmpty();
        }
        req.password = srp != null ? srp : new TLRPC.TL_inputCheckPasswordEmpty();
        req.user_id = getMessagesController().getInputUser(currentUser);
        getConnectionsManager().sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error != null) {
                if (getParentActivity() == null) {
                    return;
                }
                if ("PASSWORD_HASH_INVALID".equals(error.text)) {
                    if (srp == null) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        if (isChannel) {
                            builder.setTitle(LocaleController.getString("EditAdminChannelTransfer", R.string.EditAdminChannelTransfer));
                        } else {
                            builder.setTitle(LocaleController.getString("EditAdminGroupTransfer", R.string.EditAdminGroupTransfer));
                        }
                        String title = UserConfig.getChatTitleOverride(currentAccount, currentChat.id);
                        if (title == null) {
                            title = currentChat.title;
                        }
                        builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferReadyAlertText", R.string.EditAdminTransferReadyAlertText, title, UserObject.getFirstName(currentUser))));
                        builder.setPositiveButton(LocaleController.getString("EditAdminTransferChangeOwner", R.string.EditAdminTransferChangeOwner), (dialogInterface, i) -> {
                            TwoStepVerificationActivity fragment = new TwoStepVerificationActivity();
                            fragment.setDelegate(password -> initTransfer(password, fragment));
                            presentFragment(fragment);
                        });
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                        showDialog(builder.create());
                    }
                } else if ("PASSWORD_MISSING".equals(error.text) || error.text.startsWith("PASSWORD_TOO_FRESH_") || error.text.startsWith("SESSION_TOO_FRESH_")) {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                    }
                    AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                    builder.setTitle(LocaleController.getString("EditAdminTransferAlertTitle", R.string.EditAdminTransferAlertTitle));

                    LinearLayout linearLayout = new LinearLayout(getParentActivity());
                    linearLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(2), AndroidUtilities.dp(24), 0);
                    linearLayout.setOrientation(LinearLayout.VERTICAL);
                    builder.setView(linearLayout);

                    TextView messageTextView = new TextView(getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    if (isChannel) {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("EditChannelAdminTransferAlertText", R.string.EditChannelAdminTransferAlertText, UserObject.getFirstName(currentUser))));
                    } else {
                        messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString("EditAdminTransferAlertText", R.string.EditAdminTransferAlertText, UserObject.getFirstName(currentUser))));
                    }
                    linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

                    LinearLayout linearLayout2 = new LinearLayout(getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    ImageView dotImageView = new ImageView(getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EditAdminTransferAlertText1", R.string.EditAdminTransferAlertText1)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    linearLayout2 = new LinearLayout(getParentActivity());
                    linearLayout2.setOrientation(LinearLayout.HORIZONTAL);
                    linearLayout.addView(linearLayout2, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                    dotImageView = new ImageView(getParentActivity());
                    dotImageView.setImageResource(R.drawable.list_circle);
                    dotImageView.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(11) : 0, AndroidUtilities.dp(9), LocaleController.isRTL ? 0 : AndroidUtilities.dp(11), 0);
                    dotImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogTextBlack), PorterDuff.Mode.MULTIPLY));

                    messageTextView = new TextView(getParentActivity());
                    messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                    messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                    messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                    messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.getString("EditAdminTransferAlertText2", R.string.EditAdminTransferAlertText2)));
                    if (LocaleController.isRTL) {
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT));
                    } else {
                        linearLayout2.addView(dotImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
                        linearLayout2.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
                    }

                    if ("PASSWORD_MISSING".equals(error.text)) {
                        builder.setPositiveButton(LocaleController.getString("EditAdminTransferSetPassword", R.string.EditAdminTransferSetPassword), (dialogInterface, i) -> presentFragment(new TwoStepVerificationSetupActivity(TwoStepVerificationSetupActivity.TYPE_INTRO, null)));
                        builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
                    } else {
                        messageTextView = new TextView(getParentActivity());
                        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
                        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
                        messageTextView.setText(LocaleController.getString("EditAdminTransferAlertText3", R.string.EditAdminTransferAlertText3));
                        linearLayout.addView(messageTextView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 11, 0, 0));

                        builder.setNegativeButton(LocaleController.getString("OK", R.string.OK), null);
                    }
                    showDialog(builder.create());
                } else if ("SRP_ID_INVALID".equals(error.text)) {
                    TLRPC.TL_account_getPassword getPasswordReq = new TLRPC.TL_account_getPassword();
                    ConnectionsManager.getInstance(currentAccount).sendRequest(getPasswordReq, (response2, error2) -> AndroidUtilities.runOnUIThread(() -> {
                        if (error2 == null) {
                            TLRPC.account_Password currentPassword = (TLRPC.account_Password) response2;
                            passwordFragment.setCurrentPasswordInfo(null, currentPassword);
                            TwoStepVerificationActivity.initPasswordNewAlgo(currentPassword);
                            initTransfer(passwordFragment.getNewSrpPassword(), passwordFragment);
                        }
                    }), ConnectionsManager.RequestFlagWithoutLogin);
                } else if (error.text.equals("CHANNELS_TOO_MUCH")) {
                    if (getParentActivity() != null && !AccountInstance.getInstance(currentAccount).getUserConfig().isPremium()) {
                        showDialog(new LimitReachedBottomSheet(this, getParentActivity(), LimitReachedBottomSheet.TYPE_TO_MANY_COMMUNITIES, currentAccount));
                    } else {
                        presentFragment(new TooManyCommunitiesActivity(TooManyCommunitiesActivity.TYPE_EDIT));
                    }
                } else {
                    if (passwordFragment != null) {
                        passwordFragment.needHideProgress();
                        passwordFragment.finishFragment();
                    }
                    AlertsCreator.showAddUserAlert(error.text, ChatRightsEditActivity.this, isChannel, req);
                }
            } else {
                if (srp != null) {
                    delegate.didChangeOwner(currentUser);
                    removeSelfFromStack();
                    passwordFragment.needHideProgress();
                    passwordFragment.finishFragment();
                }
            }
        }));
    }

    private void updateRows(boolean update) {
        int transferOwnerShadowRowPrev = Math.min(transferOwnerShadowRow, transferOwnerRow);

        manageRow = -1;
        changeInfoRow = -1;
        postMessagesRow = -1;
        editMesagesRow = -1;
        deleteMessagesRow = -1;
        addAdminsRow = -1;
        anonymousRow = -1;
        banUsersRow = -1;
        addUsersRow = -1;
        pinMessagesRow = -1;
        rightsShadowRow = -1;
        removeAdminRow = -1;
        removeAdminShadowRow = -1;
        cantEditInfoRow = -1;
        transferOwnerShadowRow = -1;
        transferOwnerRow = -1;
        rankHeaderRow = -1;
        rankRow = -1;
        rankInfoRow = -1;

        sendMessagesRow = -1;
        sendMediaRow = -1;
        sendStickersRow = -1;
        sendPollsRow = -1;
        embedLinksRow = -1;
        startVoiceChatRow = -1;
        untilSectionRow = -1;
        untilDateRow = -1;
        addBotButtonRow = -1;
        manageTopicsRow = -1;

        rowCount = 3;
        permissionsStartRow = rowCount;
        if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
            if (isChannel) {
                changeInfoRow = rowCount++;
                postMessagesRow = rowCount++;
                editMesagesRow = rowCount++;
                deleteMessagesRow = rowCount++;
                addUsersRow = rowCount++;
                startVoiceChatRow = rowCount++;
                addAdminsRow = rowCount++;
            } else {
                if (currentType == TYPE_ADD_BOT) {
                    manageRow = rowCount++;
                }
                changeInfoRow = rowCount++;
                deleteMessagesRow = rowCount++;
                banUsersRow = rowCount++;
                addUsersRow = rowCount++;
                pinMessagesRow = rowCount++;
                startVoiceChatRow = rowCount++;
                addAdminsRow = rowCount++;
                anonymousRow = rowCount++;
                if (isForum) {
                    manageTopicsRow = rowCount++;
                }
            }
        } else if (currentType == TYPE_BANNED) {
            sendMessagesRow = rowCount++;
            sendMediaRow = rowCount++;
            sendStickersRow = rowCount++;
            sendPollsRow = rowCount++;
            embedLinksRow = rowCount++;
            addUsersRow = rowCount++;
            pinMessagesRow = rowCount++;
            changeInfoRow = rowCount++;
            if (isForum) {
                manageTopicsRow = rowCount++;
            }
            untilSectionRow = rowCount++;
            untilDateRow = rowCount++;
        }
        permissionsEndRow = rowCount;

        if (canEdit) {
            if (!isChannel && (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT && asAdmin)) {
                rightsShadowRow = rowCount++;
                rankHeaderRow = rowCount++;
                rankRow = rowCount++;
                rankInfoRow = rowCount++;
            }
            if (currentChat != null && currentChat.creator && currentType == TYPE_ADMIN && hasAllAdminRights() && !currentUser.bot) {
                if (rightsShadowRow == -1) {
                    transferOwnerShadowRow = rowCount++;
                }
                transferOwnerRow = rowCount++;
                if (rightsShadowRow != -1) {
                    transferOwnerShadowRow = rowCount++;
                }
            }
            if (initialIsSet) {
                if (rightsShadowRow == -1) {
                    rightsShadowRow = rowCount++;
                }
                removeAdminRow = rowCount++;
                removeAdminShadowRow = rowCount++;
            }
        } else {
            if (currentType == TYPE_ADMIN) {
                if (!isChannel && (!currentRank.isEmpty() || currentChat.creator && UserObject.isUserSelf(currentUser))) {
                    rightsShadowRow = rowCount++;
                    rankHeaderRow = rowCount++;
                    rankRow = rowCount++;
                    if (currentChat.creator && UserObject.isUserSelf(currentUser)) {
                        rankInfoRow = rowCount++;
                    } else {
                        cantEditInfoRow = rowCount++;
                    }
                } else {
                    cantEditInfoRow = rowCount++;
                }
            } else {
                rightsShadowRow = rowCount++;
            }
        }
        if (currentType == TYPE_ADD_BOT) {
            addBotButtonRow = rowCount++;
        }

        if (update) {
            if (transferOwnerShadowRowPrev == -1 && transferOwnerShadowRow != -1) {
                listViewAdapter.notifyItemRangeInserted(Math.min(transferOwnerShadowRow, transferOwnerRow), 2);
            } else if (transferOwnerShadowRowPrev != -1 && transferOwnerShadowRow == -1) {
                listViewAdapter.notifyItemRangeRemoved(transferOwnerShadowRowPrev, 2);
            }
        }
    }

    private void onDonePressed() {
        if (loading) {
            return;
        }
        if (!ChatObject.isChannel(currentChat) && (currentType == TYPE_BANNED || currentType == TYPE_ADMIN && (!isDefaultAdminRights() || rankRow != -1 && currentRank.codePointCount(0, currentRank.length()) > MAX_RANK_LENGTH) || currentType == TYPE_ADD_BOT && (currentRank != null || !isDefaultAdminRights()))) {
            MessagesController.getInstance(currentAccount).convertToMegaGroup(getParentActivity(), chatId, this, param -> {
                if (param != 0) {
                    chatId = param;
                    currentChat = MessagesController.getInstance(currentAccount).getChat(param);
                    onDonePressed();
                }
            });
            return;
        }
        if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
            if (rankRow != -1 && currentRank.codePointCount(0, currentRank.length()) > MAX_RANK_LENGTH) {
                listView.smoothScrollToPosition(rankRow);
                Vibrator v = (Vibrator) getParentActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (v != null) {
                    v.vibrate(200);
                }
                RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(rankHeaderRow);
                if (holder != null) {
                    AndroidUtilities.shakeView(holder.itemView);
                }
                return;
            }
            if (isChannel) {
                adminRights.pin_messages = adminRights.ban_users = false;
            } else {
                adminRights.post_messages = adminRights.edit_messages = false;
            }
            if (!adminRights.change_info && !adminRights.post_messages && !adminRights.edit_messages &&
                    !adminRights.delete_messages && !adminRights.ban_users && !adminRights.invite_users && (!isForum || !adminRights.manage_topics) &&
                    !adminRights.pin_messages && !adminRights.add_admins && !adminRights.anonymous && !adminRights.manage_call) {
                adminRights.other = true;
            } else {
                adminRights.other = false;
            }
        }
        boolean finishFragment = true;
        if (currentType == TYPE_ADMIN) {
            finishFragment = delegate == null;
            setLoading(true);
            MessagesController.getInstance(currentAccount).setUserAdminRole(chatId, currentUser, adminRights, currentRank, isChannel, this, isAddingNew, false, null, () -> {
                if (delegate != null) {
                    delegate.didSetRights(
                        adminRights.change_info || adminRights.post_messages || adminRights.edit_messages ||
                        adminRights.delete_messages || adminRights.ban_users || adminRights.invite_users || (isForum && adminRights.manage_topics) ||
                        adminRights.pin_messages || adminRights.add_admins || adminRights.anonymous || adminRights.manage_call ||
                        adminRights.other ? 1 : 0, adminRights, bannedRights, currentRank);
                    finishFragment();
                }
            }, err -> {
                setLoading(false);
                return true;
            });
        } else if (currentType == TYPE_BANNED) {
            MessagesController.getInstance(currentAccount).setParticipantBannedRole(chatId, currentUser, null, bannedRights, isChannel, getFragmentForAlert(1));
            int rights;
            if (bannedRights.send_messages || bannedRights.send_stickers || bannedRights.embed_links || bannedRights.send_media ||
                    bannedRights.send_gifs || bannedRights.send_games || bannedRights.send_inline) {
                rights = 1;
            } else {
                bannedRights.until_date = 0;
                rights = 2;
            }
            if (delegate != null) {
                delegate.didSetRights(rights, adminRights, bannedRights, currentRank);
            }
        } else if (currentType == TYPE_ADD_BOT) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(asAdmin ?
                LocaleController.getString("AddBotAdmin", R.string.AddBotAdmin) :
                LocaleController.getString("AddBot", R.string.AddBot)
            );
            boolean isChannel = ChatObject.isChannel(currentChat) && !currentChat.megagroup;
            String chatName = currentChat == null ? "" : currentChat.title;
            builder.setMessage(AndroidUtilities.replaceTags(
                asAdmin ? (
                    isChannel ?
                        LocaleController.formatString("AddBotMessageAdminChannel", R.string.AddBotMessageAdminChannel, chatName) :
                        LocaleController.formatString("AddBotMessageAdminGroup", R.string.AddBotMessageAdminGroup, chatName)
                ) : LocaleController.formatString("AddMembersAlertNamesText", R.string.AddMembersAlertNamesText, UserObject.getUserName(currentUser), chatName)
            ));
            builder.setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null);
            builder.setPositiveButton(asAdmin ? LocaleController.getString("AddAsAdmin", R.string.AddAsAdmin) : LocaleController.getString("AddBot", R.string.AddBot), (di, i) -> {
                setLoading(true);
                Runnable onFinish = () -> {
                    if (delegate != null) {
                        delegate.didSetRights(0, asAdmin ? adminRights : null, null, currentRank);
                    }
                    closingKeyboardAfterFinish = true;

                    Bundle args1 = new Bundle();
                    args1.putBoolean("scrollToTopOnResume", true);
                    args1.putLong("chat_id", currentChat.id);
                    if (!getMessagesController().checkCanOpenChat(args1, this)) {
                        setLoading(false);
                        return;
                    }
                    ChatActivity chatActivity = new ChatActivity(args1);
                    presentFragment(chatActivity, true);
                    if (BulletinFactory.canShowBulletin(chatActivity)) {
                        if (isAddingNew && asAdmin) {
                            BulletinFactory.createAddedAsAdminBulletin(chatActivity, currentUser.first_name).show();
                        } else if (!isAddingNew && !initialAsAdmin && asAdmin) {
                            BulletinFactory.createPromoteToAdminBulletin(chatActivity, currentUser.first_name).show();
                        }
                    }
                };
                if (asAdmin || initialAsAdmin) {
                    getMessagesController().setUserAdminRole(currentChat.id, currentUser, asAdmin ? adminRights : emptyAdminRights(false), currentRank, false, this, isAddingNew, asAdmin, botHash, onFinish, err -> {
                        setLoading(false);
                        return true;
                    });
                } else {
                    getMessagesController().addUserToChat(currentChat.id, currentUser, 0, botHash, this, true, onFinish, err -> {
                        setLoading(false);
                        return true;
                    });
                }
            });
            showDialog(builder.create());
            finishFragment = false;
        }
        if (finishFragment) {
            finishFragment();
        }
    }

    private ValueAnimator doneDrawableAnimator;

    public void setLoading(boolean enable) {
        if (doneDrawableAnimator != null) {
            doneDrawableAnimator.cancel();
        }
        loading = !enable;
        actionBar.getBackButton().setEnabled(!enable);
        if (doneDrawable != null) {
            doneDrawableAnimator = ValueAnimator.ofFloat(doneDrawable.getProgress(), enable ? 1f : 0f);
            doneDrawableAnimator.addUpdateListener(a -> {
                doneDrawable.setProgress((float) a.getAnimatedValue());
                doneDrawable.invalidateSelf();
            });
            doneDrawableAnimator.setDuration((long) (150 * Math.abs(doneDrawable.getProgress() - (enable ? 1 : 0))));
            doneDrawableAnimator.start();
        }
    }

    public void setDelegate(ChatRightsEditActivityDelegate channelRightsEditActivityDelegate) {
        delegate = channelRightsEditActivityDelegate;
    }

    private boolean checkDiscard() {
        if (currentType == TYPE_ADD_BOT) {
            return true;
        }
        boolean changed;
        if (currentType == TYPE_BANNED) {
            String newBannedRights = ChatObject.getBannedRightsString(bannedRights);
            changed = !currentBannedRights.equals(newBannedRights);
        } else {
            changed = !initialRank.equals(currentRank);
        }
        if (changed) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setTitle(LocaleController.getString("UserRestrictionsApplyChanges", R.string.UserRestrictionsApplyChanges));
            TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(chatId);
            String title = UserConfig.getChatTitleOverride(currentAccount, chat.id);
            if (title == null) {
                title = chat.title;
            }
            builder.setMessage(AndroidUtilities.replaceTags(LocaleController.formatString("UserRestrictionsApplyChangesText", R.string.UserRestrictionsApplyChangesText, title)));
            builder.setPositiveButton(LocaleController.getString("ApplyTheme", R.string.ApplyTheme), (dialogInterface, i) -> onDonePressed());
            builder.setNegativeButton(LocaleController.getString("PassportDiscard", R.string.PassportDiscard), (dialog, which) -> finishFragment());
            showDialog(builder.create());
            return false;
        }
        return true;
    }

    private final static int MAX_RANK_LENGTH = 16;

    private void setTextLeft(View cell) {
        if (cell instanceof HeaderCell) {
            HeaderCell headerCell = (HeaderCell) cell;
            int left = MAX_RANK_LENGTH - (currentRank != null ? currentRank.codePointCount(0, currentRank.length()) : 0);
            if (left <= MAX_RANK_LENGTH - MAX_RANK_LENGTH * 0.7f) {
                headerCell.setText2(String.format("%d", left));
                SimpleTextView textView = headerCell.getTextView2();
                String key = left < 0 ? Theme.key_windowBackgroundWhiteRedText5 : Theme.key_windowBackgroundWhiteGrayText3;
                textView.setTextColor(Theme.getColor(key));
                textView.setTag(key);
            } else {
                headerCell.setText2("");
            }
        }
    }

    @Override
    public boolean onBackPressed() {
        return checkDiscard();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final int VIEW_TYPE_USER_CELL = 0;
        private final int VIEW_TYPE_INFO_CELL = 1;
        private final int VIEW_TYPE_TRANSFER_CELL = 2;
        private final int VIEW_TYPE_HEADER_CELL = 3;
        private final int VIEW_TYPE_SWITCH_CELL = 4;
        private final int VIEW_TYPE_SHADOW_CELL = 5;
        private final int VIEW_TYPE_UNTIL_DATE_CELL = 6;
        private final int VIEW_TYPE_RANK_CELL = 7;
        private final int VIEW_TYPE_ADD_BOT_CELL = 8;

        private Context mContext;
        private boolean ignoreTextChange;

        public ListAdapter(Context context) {
            if (currentType == TYPE_ADD_BOT) {
                setHasStableIds(true);
            }
            mContext = context;
        }

        @Override
        public long getItemId(int position) {
            if (currentType == TYPE_ADD_BOT) {
                if (position == manageRow) return 1;
                if (position == changeInfoRow) return 2;
                if (position == postMessagesRow) return 3;
                if (position == editMesagesRow) return 4;
                if (position == deleteMessagesRow) return 5;
                if (position == addAdminsRow) return 6;
                if (position == anonymousRow) return 7;
                if (position == banUsersRow) return 8;
                if (position == addUsersRow) return 9;
                if (position == pinMessagesRow) return 10;
                if (position == rightsShadowRow) return 11;
                if (position == removeAdminRow) return 12;
                if (position == removeAdminShadowRow) return 13;
                if (position == cantEditInfoRow) return 14;
                if (position == transferOwnerShadowRow) return 15;
                if (position == transferOwnerRow) return 16;
                if (position == rankHeaderRow) return 17;
                if (position == rankRow) return 18;
                if (position == rankInfoRow) return 19;
                if (position == sendMessagesRow) return 20;
                if (position == sendMediaRow) return 21;
                if (position == sendStickersRow) return 22;
                if (position == sendPollsRow) return 23;
                if (position == embedLinksRow) return 24;
                if (position == startVoiceChatRow) return 25;
                if (position == untilSectionRow) return 26;
                if (position == untilDateRow) return 27;
                if (position == addBotButtonRow) return 28;
                if (position == manageTopicsRow) return 29;
                return 0;
            } else {
                return super.getItemId(position);
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (currentChat.creator && (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT && asAdmin) && type == VIEW_TYPE_SWITCH_CELL && holder.getAdapterPosition() == anonymousRow) {
                return true;
            }
            if (!canEdit) {
                return false;
            }
            if ((currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) && type == VIEW_TYPE_SWITCH_CELL) {
                int position = holder.getAdapterPosition();
                if (position == manageRow) {
                    return myAdminRights.add_admins || (currentChat != null && currentChat.creator);
                } else {
                    if (currentType == TYPE_ADD_BOT && !asAdmin) {
                        return false;
                    }
                    if (position == changeInfoRow) {
                        return myAdminRights.change_info && (defaultBannedRights == null || defaultBannedRights.change_info);
                    } else if (position == postMessagesRow) {
                        return myAdminRights.post_messages;
                    } else if (position == editMesagesRow) {
                        return myAdminRights.edit_messages;
                    } else if (position == deleteMessagesRow) {
                        return myAdminRights.delete_messages;
                    } else if (position == startVoiceChatRow) {
                        return myAdminRights.manage_call;
                    } else if (position == addAdminsRow) {
                        return myAdminRights.add_admins;
                    } else if (position == anonymousRow) {
                        return myAdminRights.anonymous;
                    } else if (position == banUsersRow) {
                        return myAdminRights.ban_users;
                    } else if (position == addUsersRow) {
                        return myAdminRights.invite_users;
                    } else if (position == pinMessagesRow) {
                        return myAdminRights.pin_messages && (defaultBannedRights == null || defaultBannedRights.pin_messages);
                    } else if (position == manageTopicsRow) {
                        return myAdminRights.manage_topics;
                    }
                }
            }
            return type != VIEW_TYPE_HEADER_CELL && type != VIEW_TYPE_INFO_CELL && type != VIEW_TYPE_SHADOW_CELL && type != VIEW_TYPE_ADD_BOT_CELL;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_USER_CELL:
                    view = new UserCell2(mContext, 4, 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO_CELL:
                    view = new TextInfoPrivacyCell(mContext);
                    view.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    break;
                case VIEW_TYPE_TRANSFER_CELL:
                default:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER_CELL:
                    view = new HeaderCell(mContext, Theme.key_windowBackgroundWhiteBlueHeader, 21, 15, true);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SWITCH_CELL:
                    view = new TextCheckCell2(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SHADOW_CELL:
                    view = new ShadowSectionCell(mContext);
                    break;
                case VIEW_TYPE_UNTIL_DATE_CELL:
                    view = new TextDetailCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_ADD_BOT_CELL:
                    addBotButtonContainer = new FrameLayout(mContext);
                    addBotButtonContainer.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    addBotButton = new FrameLayout(mContext);
                    addBotButtonText = new AnimatedTextView(mContext, true, false, false);
                    addBotButtonText.setTypeface(AndroidUtilities.getTypeface("fonts/rmedium.ttf"));
                    addBotButtonText.setTextColor(0xffffffff);
                    addBotButtonText.setTextSize(AndroidUtilities.dp(14));
                    addBotButtonText.setGravity(Gravity.CENTER);
                    addBotButtonText.setText(LocaleController.getString("AddBotButton", R.string.AddBotButton) + " " + (asAdmin ? LocaleController.getString("AddBotButtonAsAdmin", R.string.AddBotButtonAsAdmin) : LocaleController.getString("AddBotButtonAsMember", R.string.AddBotButtonAsMember)));
                    addBotButton.addView(addBotButtonText, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
                    addBotButton.setBackground(Theme.AdaptiveRipple.filledRect(Theme.key_featuredStickers_addButton, 4));
                    addBotButton.setOnClickListener(e -> onDonePressed());
                    addBotButtonContainer.addView(addBotButton, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.FILL, 14, 28, 14, 14));
                    addBotButtonContainer.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                    View bg = new View(mContext);
                    bg.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                    addBotButtonContainer.setClipChildren(false);
                    addBotButtonContainer.setClipToPadding(false);
                    addBotButtonContainer.addView(bg, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 800, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL, 0, 0, 0, -800));
                    view = addBotButtonContainer;
                    break;
                case VIEW_TYPE_RANK_CELL:
                    PollEditTextCell cell = rankEditTextCell = new PollEditTextCell(mContext, null);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    cell.addTextWatcher(new TextWatcher() {
                        @Override
                        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                        }

                        @Override
                        public void onTextChanged(CharSequence s, int start, int before, int count) {

                        }

                        @Override
                        public void afterTextChanged(Editable s) {
                            if (ignoreTextChange) {
                                return;
                            }
                            currentRank = s.toString();
                            RecyclerView.ViewHolder holder = listView.findViewHolderForAdapterPosition(rankHeaderRow);
                            if (holder != null) {
                                setTextLeft(holder.itemView);
                            }
                        }
                    });
                    view = cell;
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_USER_CELL:
                    UserCell2 userCell2 = (UserCell2) holder.itemView;
                    String status = null;
                    if (currentType == TYPE_ADD_BOT) {
                        status = LocaleController.getString("Bot", R.string.Bot);
                    }
                    userCell2.setData(currentUser, null, status, 0);
                    break;
                case VIEW_TYPE_INFO_CELL:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == cantEditInfoRow) {
                        privacyCell.setText(LocaleController.getString("EditAdminCantEdit", R.string.EditAdminCantEdit));
                    } else if (position == rankInfoRow) {
                        String hint;
                        if (UserObject.isUserSelf(currentUser) && currentChat.creator) {
                            hint = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                        } else {
                            hint = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                        }
                        privacyCell.setText(LocaleController.formatString("EditAdminRankInfo", R.string.EditAdminRankInfo, hint));
                    }
                    break;
                case VIEW_TYPE_TRANSFER_CELL:
                    TextSettingsCell actionCell = (TextSettingsCell) holder.itemView;
                    if (position == removeAdminRow) {
                        actionCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteRedText5));
                        actionCell.setTag(Theme.key_windowBackgroundWhiteRedText5);
                        if (currentType == TYPE_ADMIN) {
                            actionCell.setText(LocaleController.getString("EditAdminRemoveAdmin", R.string.EditAdminRemoveAdmin), false);
                        } else if (currentType == TYPE_BANNED) {
                            actionCell.setText(LocaleController.getString("UserRestrictionsBlock", R.string.UserRestrictionsBlock), false);
                        }
                    } else if (position == transferOwnerRow) {
                        actionCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                        actionCell.setTag(Theme.key_windowBackgroundWhiteBlackText);
                        if (isChannel) {
                            actionCell.setText(LocaleController.getString("EditAdminChannelTransfer", R.string.EditAdminChannelTransfer), false);
                        } else {
                            actionCell.setText(LocaleController.getString("EditAdminGroupTransfer", R.string.EditAdminGroupTransfer), false);
                        }
                    }
                    break;
                case VIEW_TYPE_HEADER_CELL:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == 2) {
                        if (currentType == TYPE_ADD_BOT || (currentUser != null && currentUser.bot)) {
                            headerCell.setText(LocaleController.getString("BotRestrictionsCanDo", R.string.BotRestrictionsCanDo));
                        } else if (currentType == TYPE_ADMIN) {
                            headerCell.setText(LocaleController.getString("EditAdminWhatCanDo", R.string.EditAdminWhatCanDo));
                        } else if (currentType == TYPE_BANNED) {
                            headerCell.setText(LocaleController.getString("UserRestrictionsCanDo", R.string.UserRestrictionsCanDo));
                        }
                    } else if (position == rankHeaderRow) {
                        headerCell.setText(LocaleController.getString("EditAdminRank", R.string.EditAdminRank));
                    }
                    break;
                case VIEW_TYPE_SWITCH_CELL:
                    TextCheckCell2 checkCell = (TextCheckCell2) holder.itemView;
                    boolean asAdminValue = currentType != TYPE_ADD_BOT || asAdmin;
                    boolean isCreator = (currentChat != null && currentChat.creator);
                    if (position == manageRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("ManageGroup", R.string.ManageGroup), asAdmin, true);
                        checkCell.setIcon(myAdminRights.add_admins || isCreator ? 0 : R.drawable.permission_locked);
                    } else if (position == changeInfoRow) {
                        if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
                            if (isChannel) {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminChangeChannelInfo", R.string.EditAdminChangeChannelInfo), asAdminValue && adminRights.change_info || !defaultBannedRights.change_info, true);
                            } else {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminChangeGroupInfo", R.string.EditAdminChangeGroupInfo), asAdminValue && adminRights.change_info || !defaultBannedRights.change_info, true);
                            }
                            if (currentType == TYPE_ADD_BOT) {
                                checkCell.setIcon(myAdminRights.change_info || isCreator ? 0 : R.drawable.permission_locked);
                            }
                        } else if (currentType == TYPE_BANNED) {
                            checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsChangeInfo", R.string.UserRestrictionsChangeInfo), !bannedRights.change_info && !defaultBannedRights.change_info, manageTopicsRow != -1);
                            checkCell.setIcon(defaultBannedRights.change_info ? R.drawable.permission_locked : 0);
                        }
                    } else if (position == postMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminPostMessages", R.string.EditAdminPostMessages), asAdminValue && adminRights.post_messages, true);
                        if (currentType == TYPE_ADD_BOT) {
                            checkCell.setIcon(myAdminRights.post_messages || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == editMesagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminEditMessages", R.string.EditAdminEditMessages), asAdminValue && adminRights.edit_messages, true);
                        if (currentType == TYPE_ADD_BOT) {
                            checkCell.setIcon(myAdminRights.edit_messages || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == deleteMessagesRow) {
                        if (isChannel) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminDeleteMessages", R.string.EditAdminDeleteMessages), asAdminValue && adminRights.delete_messages, true);
                        } else {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminGroupDeleteMessages", R.string.EditAdminGroupDeleteMessages), asAdminValue && adminRights.delete_messages, true);
                        }
                        if (currentType == TYPE_ADD_BOT) {
                            checkCell.setIcon(myAdminRights.delete_messages || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == addAdminsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddAdmins", R.string.EditAdminAddAdmins), asAdminValue && adminRights.add_admins, anonymousRow != -1);
                        if (currentType == TYPE_ADD_BOT) {
                            checkCell.setIcon(myAdminRights.add_admins || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == anonymousRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminSendAnonymously", R.string.EditAdminSendAnonymously), asAdminValue && adminRights.anonymous, manageTopicsRow != -1);
                        if (currentType == TYPE_ADD_BOT) {
                            checkCell.setIcon(myAdminRights.anonymous || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == banUsersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("EditAdminBanUsers", R.string.EditAdminBanUsers), asAdminValue && adminRights.ban_users, true);
                        if (currentType == TYPE_ADD_BOT) {
                            checkCell.setIcon(myAdminRights.ban_users || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == startVoiceChatRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("StartVoipChatPermission", R.string.StartVoipChatPermission), asAdminValue && adminRights.manage_call, true);
                        if (currentType == TYPE_ADD_BOT) {
                            checkCell.setIcon(myAdminRights.manage_call || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == manageTopicsRow) {
                        if (currentType == TYPE_ADMIN) {
                            checkCell.setTextAndCheck(LocaleController.getString("ManageTopicsPermission", R.string.ManageTopicsPermission), asAdminValue && adminRights.manage_topics, false);
                        } else if (currentType == TYPE_BANNED) {
                            checkCell.setTextAndCheck(LocaleController.getString("CreateTopicsPermission", R.string.CreateTopicsPermission), !bannedRights.manage_topics && !defaultBannedRights.manage_topics, false);
                            checkCell.setIcon(defaultBannedRights.manage_topics ? R.drawable.permission_locked : 0);
                        } else if (currentType == TYPE_ADD_BOT) {
                            checkCell.setTextAndCheck(LocaleController.getString("ManageTopicsPermission", R.string.ManageTopicsPermission), asAdminValue && adminRights.manage_topics, false);
                            checkCell.setIcon(myAdminRights.manage_topics || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == addUsersRow) {
                        if (currentType == TYPE_ADMIN) {
                            if (ChatObject.isActionBannedByDefault(currentChat, ChatObject.ACTION_INVITE)) {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddUsers", R.string.EditAdminAddUsers), adminRights.invite_users, true);
                            } else {
                                checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddUsersViaLink", R.string.EditAdminAddUsersViaLink), adminRights.invite_users, true);
                            }
                        } else if (currentType == TYPE_BANNED) {
                            checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsInviteUsers", R.string.UserRestrictionsInviteUsers), !bannedRights.invite_users && !defaultBannedRights.invite_users, true);
                            checkCell.setIcon(defaultBannedRights.invite_users ? R.drawable.permission_locked : 0);
                        } else if (currentType == TYPE_ADD_BOT) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminAddUsersViaLink", R.string.EditAdminAddUsersViaLink), asAdminValue && adminRights.invite_users, true);
                            checkCell.setIcon(myAdminRights.invite_users || isCreator ? 0 : R.drawable.permission_locked);
                        }
                    } else if (position == pinMessagesRow) {
                        if (currentType == TYPE_ADMIN || currentType == TYPE_ADD_BOT) {
                            checkCell.setTextAndCheck(LocaleController.getString("EditAdminPinMessages", R.string.EditAdminPinMessages), asAdminValue && adminRights.pin_messages || !defaultBannedRights.pin_messages, true);
                            if (currentType == TYPE_ADD_BOT) {
                                checkCell.setIcon(myAdminRights.pin_messages || isCreator ? 0 : R.drawable.permission_locked);
                            }
                        } else if (currentType == TYPE_BANNED) {
                            checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsPinMessages", R.string.UserRestrictionsPinMessages), !bannedRights.pin_messages && !defaultBannedRights.pin_messages, true);
                            checkCell.setIcon(defaultBannedRights.pin_messages ? R.drawable.permission_locked : 0);
                        }
                    } else if (position == sendMessagesRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSend", R.string.UserRestrictionsSend), !bannedRights.send_messages && !defaultBannedRights.send_messages, true);
                        checkCell.setIcon(defaultBannedRights.send_messages ? R.drawable.permission_locked : 0);
                    } else if (position == sendMediaRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendMedia", R.string.UserRestrictionsSendMedia), !bannedRights.send_media && !defaultBannedRights.send_media, true);
                        checkCell.setIcon(defaultBannedRights.send_media ? R.drawable.permission_locked : 0);
                    } else if (position == sendStickersRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendStickers", R.string.UserRestrictionsSendStickers), !bannedRights.send_stickers && !defaultBannedRights.send_stickers, true);
                        checkCell.setIcon(defaultBannedRights.send_stickers ? R.drawable.permission_locked : 0);
                    } else if (position == embedLinksRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsEmbedLinks", R.string.UserRestrictionsEmbedLinks), !bannedRights.embed_links && !defaultBannedRights.embed_links, true);
                        checkCell.setIcon(defaultBannedRights.embed_links ? R.drawable.permission_locked : 0);
                    } else if (position == sendPollsRow) {
                        checkCell.setTextAndCheck(LocaleController.getString("UserRestrictionsSendPolls", R.string.UserRestrictionsSendPolls), !bannedRights.send_polls && !defaultBannedRights.send_polls, true);
                        checkCell.setIcon(defaultBannedRights.send_polls ? R.drawable.permission_locked : 0);
                    }

                    if (currentType == TYPE_ADD_BOT) {
//                        checkCell.setEnabled((asAdmin || position == manageRow) && !checkCell.hasIcon(), false);
                    } else {
                        if (position == sendMediaRow || position == sendStickersRow || position == embedLinksRow || position == sendPollsRow) {
                            checkCell.setEnabled(!bannedRights.send_messages && !bannedRights.view_messages && !defaultBannedRights.send_messages && !defaultBannedRights.view_messages);
                        } else if (position == sendMessagesRow) {
                            checkCell.setEnabled(!bannedRights.view_messages && !defaultBannedRights.view_messages);
                        }
                    }
                    break;
                case VIEW_TYPE_SHADOW_CELL:
                    ShadowSectionCell shadowCell = (ShadowSectionCell) holder.itemView;
                    if (currentType == TYPE_ADD_BOT && (position == rightsShadowRow || position == rankInfoRow)) {
                        shadowCell.setAlpha(asAdminT);
                    } else {
                        shadowCell.setAlpha(1);
                    }
                    if (position == rightsShadowRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, removeAdminRow == -1 && rankRow == -1 ? R.drawable.greydivider_bottom : R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == removeAdminShadowRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == rankInfoRow) {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, canEdit ? R.drawable.greydivider : R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else {
                        shadowCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                    }
                    break;
                case VIEW_TYPE_UNTIL_DATE_CELL:
                    TextDetailCell detailCell = (TextDetailCell) holder.itemView;
                    if (position == untilDateRow) {
                        String value;
                        if (bannedRights.until_date == 0 || Math.abs(bannedRights.until_date - System.currentTimeMillis() / 1000) > 10 * 365 * 24 * 60 * 60) {
                            value = LocaleController.getString("UserRestrictionsUntilForever", R.string.UserRestrictionsUntilForever);
                        } else {
                            value = LocaleController.formatDateForBan(bannedRights.until_date);
                        }
                        detailCell.setTextAndValue(LocaleController.getString("UserRestrictionsDuration", R.string.UserRestrictionsDuration), value, false);
                    }
                    break;
                case VIEW_TYPE_RANK_CELL:
                    PollEditTextCell textCell = (PollEditTextCell) holder.itemView;
                    String hint;
                    if (UserObject.isUserSelf(currentUser) && currentChat.creator) {
                        hint = LocaleController.getString("ChannelCreator", R.string.ChannelCreator);
                    } else {
                        hint = LocaleController.getString("ChannelAdmin", R.string.ChannelAdmin);
                    }
                    ignoreTextChange = true;
                    textCell.getTextView().setEnabled(canEdit || currentChat.creator);
                    textCell.getTextView().setSingleLine(true);
                    textCell.getTextView().setImeOptions(EditorInfo.IME_ACTION_DONE);
                    textCell.setTextAndHint(currentRank, hint, false);
                    ignoreTextChange = false;
                    break;
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() == rankHeaderRow) {
                setTextLeft(holder.itemView);
            }
        }

        @Override
        public void onViewDetachedFromWindow(RecyclerView.ViewHolder holder) {
            if (holder.getAdapterPosition() == rankRow && getParentActivity() != null) {
                AndroidUtilities.hideKeyboard(getParentActivity().getCurrentFocus());
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                return VIEW_TYPE_USER_CELL;
            } else if (position == 1 || position == rightsShadowRow || position == removeAdminShadowRow || position == untilSectionRow || position == transferOwnerShadowRow) {
                return VIEW_TYPE_SHADOW_CELL;
            } else if (position == 2 || position == rankHeaderRow) {
                return VIEW_TYPE_HEADER_CELL;
            } else if (position == changeInfoRow || position == postMessagesRow || position == editMesagesRow || position == deleteMessagesRow ||
                    position == addAdminsRow || position == banUsersRow || position == addUsersRow || position == pinMessagesRow ||
                    position == sendMessagesRow || position == sendMediaRow || position == sendStickersRow || position == embedLinksRow ||
                    position == sendPollsRow || position == anonymousRow || position == startVoiceChatRow || position == manageRow || position == manageTopicsRow) {
                return VIEW_TYPE_SWITCH_CELL;
            } else if (position == cantEditInfoRow || position == rankInfoRow) {
                return VIEW_TYPE_INFO_CELL;
            } else if (position == untilDateRow) {
                return VIEW_TYPE_UNTIL_DATE_CELL;
            } else if (position == rankRow) {
                return VIEW_TYPE_RANK_CELL;
            } else if (position == addBotButtonRow) {
                return VIEW_TYPE_ADD_BOT_CELL;
            } else {
                return VIEW_TYPE_TRANSFER_CELL;
            }
        }
    }

    private ValueAnimator asAdminAnimator;
    private void updateAsAdmin(boolean animated) {
        if (addBotButton != null) {
            addBotButton.invalidate();
        }
        final int count = listView.getChildCount();
        for (int i = 0; i < count; ++i) {
            View child = listView.getChildAt(i);
            int childPosition = listView.getChildAdapterPosition(child);
            if (child instanceof TextCheckCell2) {
                if (!asAdmin) {
                    if (childPosition == changeInfoRow && !defaultBannedRights.change_info ||
                        childPosition == pinMessagesRow && !defaultBannedRights.pin_messages) {
                        ((TextCheckCell2) child).setChecked(true);
                        ((TextCheckCell2) child).setEnabled(false, false);
                    } else {
                        ((TextCheckCell2) child).setChecked(false);
                        ((TextCheckCell2) child).setEnabled(childPosition == manageRow, animated);
                    }
                } else {
                    boolean childValue = false, childEnabled = false;
                    if (childPosition == manageRow) {
                        childValue = asAdmin;
                        childEnabled = myAdminRights.add_admins || (currentChat != null && currentChat.creator);
                    } else if (childPosition == changeInfoRow) {
                        childValue = adminRights.change_info;
                        childEnabled = myAdminRights.change_info && defaultBannedRights.change_info;
                    } else if (childPosition == postMessagesRow) {
                        childValue = adminRights.post_messages;
                        childEnabled = myAdminRights.post_messages;
                    } else if (childPosition == editMesagesRow) {
                        childValue = adminRights.edit_messages;
                        childEnabled = myAdminRights.edit_messages;
                    } else if (childPosition == deleteMessagesRow) {
                        childValue = adminRights.delete_messages;
                        childEnabled = myAdminRights.delete_messages;
                    } else if (childPosition == banUsersRow) {
                        childValue = adminRights.ban_users;
                        childEnabled = myAdminRights.ban_users;
                    } else if (childPosition == addUsersRow) {
                        childValue = adminRights.invite_users;
                        childEnabled = myAdminRights.invite_users;
                    } else if (childPosition == pinMessagesRow) {
                        childValue = adminRights.pin_messages;
                        childEnabled = myAdminRights.pin_messages && defaultBannedRights.pin_messages;
                    } else if (childPosition == startVoiceChatRow) {
                        childValue = adminRights.manage_call;
                        childEnabled = myAdminRights.manage_call;
                    } else if (childPosition == addAdminsRow) {
                        childValue = adminRights.add_admins;
                        childEnabled = myAdminRights.add_admins;
                    } else if (childPosition == anonymousRow) {
                        childValue = adminRights.anonymous;
                        childEnabled = myAdminRights.anonymous || (currentChat != null && currentChat.creator);
                    } else if (childPosition == manageTopicsRow) {
                        childValue = adminRights.manage_topics;
                        childEnabled = myAdminRights.manage_topics;
                    }
                    ((TextCheckCell2) child).setChecked(childValue);
                    ((TextCheckCell2) child).setEnabled(childEnabled, animated);
                }
            }
        }
//        listViewAdapter.notifyItemRangeChanged(permissionsStartRow, permissionsEndRow - permissionsStartRow);
//        if (asAdmin) {
//            listViewAdapter.notifyItemMoved(addBotButtonRow, rightsShadowRow + 1);
//            listViewAdapter.notifyItemRangeInserted(rightsShadowRow, rankInfoRow - rightsShadowRow + 1);
//        } else {
//            listViewAdapter.notifyItemRangeRemoved(rightsShadowRow, rankInfoRow - rightsShadowRow + 1);
//            listViewAdapter.notifyItemMoved(addBotButtonRow, permissionsEndRow + 1);
//        }
        listViewAdapter.notifyDataSetChanged();

        if (addBotButtonText != null) {
            addBotButtonText.setText(LocaleController.getString("AddBotButton", R.string.AddBotButton) + " " + (asAdmin ? LocaleController.getString("AddBotButtonAsAdmin", R.string.AddBotButtonAsAdmin) : LocaleController.getString("AddBotButtonAsMember", R.string.AddBotButtonAsMember)), animated, asAdmin);
        }
        if (asAdminAnimator != null) {
            asAdminAnimator.cancel();
            asAdminAnimator = null;
        }
        if (animated) {
            asAdminAnimator = ValueAnimator.ofFloat(asAdminT, asAdmin ? 1f : 0f);
            asAdminAnimator.addUpdateListener(a -> {
                asAdminT = (float) a.getAnimatedValue();
                if (addBotButton != null) {
                    addBotButton.invalidate();
                }
            });
            asAdminAnimator.setDuration((long) (Math.abs(asAdminT - (asAdmin ? 1f : 0f)) * 200));
            asAdminAnimator.start();
        } else {
            asAdminT = asAdmin ? 1f : 0f;
            if (addBotButton != null) {
                addBotButton.invalidate();
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        ThemeDescription.ThemeDescriptionDelegate cellDelegate = () -> {
            if (listView != null) {
                int count = listView.getChildCount();
                for (int a = 0; a < count; a++) {
                    View child = listView.getChildAt(a);
                    if (child instanceof UserCell2) {
                        ((UserCell2) child).update(0);
                    }
                }
            }
        };

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{UserCell2.class, TextSettingsCell.class, TextCheckCell2.class, HeaderCell.class, TextDetailCell.class, PollEditTextCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2Track));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell2.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switch2TrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{ShadowSectionCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteRedText5));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{HeaderCell.class}, new String[]{"textView2"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_HINTTEXTCOLOR, new Class[]{PollEditTextCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteHintText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteGrayText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, new String[]{"statusOnlineColor"}, null, null, cellDelegate, Theme.key_windowBackgroundWhiteBlueText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{UserCell2.class}, null, Theme.avatarDrawables, null, Theme.key_avatar_text));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundRed));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundOrange));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundViolet));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundGreen));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundCyan));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundBlue));
        themeDescriptions.add(new ThemeDescription(null, 0, null, null, null, cellDelegate, Theme.key_avatar_backgroundPink));

        themeDescriptions.add(new ThemeDescription(null, 0, new Class[]{DialogRadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextBlack));
        themeDescriptions.add(new ThemeDescription(null, 0, new Class[]{DialogRadioCell.class}, new String[]{"textView"}, null, null, null, Theme.key_dialogTextGray2));
        themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOX, new Class[]{DialogRadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_dialogRadioBackground));
        themeDescriptions.add(new ThemeDescription(null, ThemeDescription.FLAG_CHECKBOXCHECK, new Class[]{DialogRadioCell.class}, new String[]{"radioButton"}, null, null, null, Theme.key_dialogRadioBackgroundChecked));

        return themeDescriptions;
    }
}
