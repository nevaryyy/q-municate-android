package com.quickblox.qmunicate.qb.helpers;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.text.TextUtils;

import com.quickblox.internal.core.exception.QBResponseException;
import com.quickblox.internal.module.custom.request.QBCustomObjectRequestBuilder;
import com.quickblox.internal.module.custom.request.QBCustomObjectUpdateBuilder;
import com.quickblox.module.chat.QBChatMessage;
import com.quickblox.module.chat.QBChatService;
import com.quickblox.module.chat.QBHistoryMessage;
import com.quickblox.module.chat.QBPrivateChat;
import com.quickblox.module.chat.QBPrivateChatManager;
import com.quickblox.module.chat.QBRoomChat;
import com.quickblox.module.chat.QBRoomChatManager;
import com.quickblox.module.chat.listeners.QBMessageListener;
import com.quickblox.module.chat.listeners.QBPrivateChatManagerListener;
import com.quickblox.module.chat.model.QBAttachment;
import com.quickblox.module.chat.model.QBDialog;
import com.quickblox.module.chat.model.QBDialogType;
import com.quickblox.module.content.QBContent;
import com.quickblox.module.content.model.QBFile;
import com.quickblox.module.users.model.QBUser;
import com.quickblox.qmunicate.R;
import com.quickblox.qmunicate.caching.DatabaseManager;
import com.quickblox.qmunicate.model.DialogMessageCache;
import com.quickblox.qmunicate.model.Friend;
import com.quickblox.qmunicate.service.QBServiceConsts;
import com.quickblox.qmunicate.utils.ChatUtils;
import com.quickblox.qmunicate.utils.Consts;
import com.quickblox.qmunicate.utils.DateUtils;
import com.quickblox.qmunicate.utils.ErrorUtils;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QBChatHelper extends BaseHelper implements QBPrivateChatManagerListener {

    private static final int AUTO_PRESENCE_INTERVAL_IN_SECONDS = 30;

    private static String propertyDateSent = "date_sent";
    private static String propertySaveToHistory = "save_to_history";
    private static String valuePropertySaveToHistory = "1";
    private static String valuePropertyNotSaveToHistory = "0";
    private static String propertyNotificationTypeCreating = "notification_type";
    private static String valuePropertyNotificationTypeCreating = "1";

    private QBChatService chatService;
    private QBUser user;
    private QBPrivateChatManager privateChatManager;
    private QBPrivateChat privateChat;
    private QBRoomChatManager roomChatManager;
    private QBRoomChat roomChat;
    private PrivateChatMessageListener privateChatMessageListener = new PrivateChatMessageListener();
    private RoomChatMessageListener roomChatMessageListener = new RoomChatMessageListener();
    private int opponentId;

    public QBChatHelper(Context context) {
        super(context);
    }

    public void sendPrivateMessage(String message) throws Exception {
        QBChatMessage chatMessage = getQBChatMessage(message);
        sendPrivateMessage(chatMessage);

        String roomJidId = opponentId + Consts.EMPTY_STRING;
        String attachUrl = Consts.EMPTY_STRING;
        long time = Long.parseLong(chatMessage.getProperty(propertyDateSent).toString());

        saveMessageToCache(new DialogMessageCache(roomJidId, user.getId(), chatMessage.getBody(), attachUrl,
                time, true));
    }

    private QBChatMessage getQBChatMessage(String body) {
        long time = DateUtils.getCurrentTime();
        QBChatMessage chatMessage = new QBChatMessage();
        chatMessage.setBody(body);
        chatMessage.setProperty(propertyDateSent, time + Consts.EMPTY_STRING);
        chatMessage.setProperty(propertySaveToHistory, valuePropertySaveToHistory);
        return chatMessage;
    }

    public void saveMessageToCache(DialogMessageCache dialogMessageCache) {
        DatabaseManager.saveChatMessage(context, dialogMessageCache);
    }

    public void sendGroupMessage(String roomJidId, String message) throws Exception {
        roomChat = roomChatManager.getRoom(roomJidId);
        if (roomChat == null) {
            return;
        }
        QBChatMessage chatMessage = getQBChatMessage(message);
        sendRoomMessage(chatMessage);
    }

    public void sendGroupMessageWithAttachImage(String roomJidId, QBFile file) throws Exception {
        roomChat = roomChatManager.getRoom(roomJidId);
        if (roomChat == null) {
            return;
        }
        QBChatMessage chatMessage = getQBChatMessageWithImage(file);
        sendRoomMessage(chatMessage);
    }

    private QBChatMessage getQBChatMessageWithImage(QBFile qbFile) {
        long time = DateUtils.getCurrentTime();
        QBChatMessage chatMessage = new QBChatMessage();
        QBAttachment attachment = new QBAttachment(QBAttachment.PHOTO_TYPE);
        attachment.setUrl(qbFile.getPublicUrl());
        chatMessage.addAttachment(attachment);
        chatMessage.setProperty(propertyDateSent, time + Consts.EMPTY_STRING);
        chatMessage.setProperty(propertySaveToHistory, valuePropertySaveToHistory);
        return chatMessage;
    }

    private void sendPrivateMessage(QBChatMessage chatMessage) throws Exception {
        String error = null;
        try {
            privateChat.sendMessage(chatMessage);
        } catch (XMPPException e) {
            error = context.getString(R.string.dlg_fail_send_msg);
        } catch (SmackException.NotConnectedException e) {
            error = context.getString(R.string.dlg_fail_connection);
        }
        if (error != null) {
            throw new Exception(error);
        }
    }

    private void sendRoomMessage(QBChatMessage chatMessage) throws Exception {
        String error = null;
        try {
            roomChat.sendMessage(chatMessage);
        } catch (XMPPException e) {
            error = context.getString(R.string.dlg_fail_send_msg);
        } catch (SmackException.NotConnectedException e) {
            error = context.getString(R.string.dlg_fail_connection);
        }
        if (error != null) {
            throw new Exception(error);
        }
    }

    public void sendPrivateMessageWithAttachImage(QBFile file) throws Exception {
        QBChatMessage chatMessage = getQBChatMessageWithImage(file);
        sendPrivateMessage(chatMessage);

        String roomJidId = opponentId + Consts.EMPTY_STRING;
        String attachUrl = file.getPublicUrl();
        long time = Long.parseLong(chatMessage.getProperty(propertyDateSent).toString());

        saveMessageToCache(new DialogMessageCache(roomJidId, user.getId(), chatMessage.getBody(), attachUrl,
                time, true));
    }

    @Override
    public void chatCreated(QBPrivateChat privateChat, boolean createdLocally) {
        privateChat.addMessageListener(privateChatMessageListener);
    }

    public void updateDialog(QBDialog dialog, String roomJidId) {
        DatabaseManager.updateDialog(context, roomJidId, dialog.getLastMessage(),
                dialog.getLastMessageDateSent(), dialog.getLastMessageUserId());
    }

    public void init() {
        privateChatManager = chatService.getPrivateChatManager();
        privateChatManager.addPrivateChatManagerListener(this);
        roomChatManager = chatService.getRoomChatManager();
    }

    public void createPrivateChat(int opponentId) {
        privateChat = privateChatManager.createChat(opponentId, privateChatMessageListener);
        this.opponentId = opponentId;
    }

    public QBDialog createRoomChat(String roomName,
            List<Integer> friendIdsList) throws SmackException, XMPPException, QBResponseException {
        ArrayList<Integer> occupantIdsList = ChatUtils.getOccupantIdsWithUser(friendIdsList);
        QBDialog dialog = roomChatManager.createDialog(roomName, QBDialogType.GROUP, occupantIdsList);
        joinRoomChat(dialog.getRoomJid());
        inviteFriendsToRoom(dialog, friendIdsList);
        saveDialogToCache(dialog, dialog.getRoomJid());
        return dialog;
    }

    public void joinRoomChat(String roomJidId) throws XMPPException, SmackException {
        roomChat = roomChatManager.getRoom(roomJidId);
        if (roomChat == null) {
            roomChat = roomChatManager.createRoom(roomJidId);
        }
        roomChat.addMessageListener(roomChatMessageListener);
        roomChat.join();
    }

    private void inviteFriendsToRoom(QBDialog dialog,
            List<Integer> friendIdsList) throws XMPPException, SmackException {
        for (Integer friendId : friendIdsList) {
            notifyFriendAboutInvitation(dialog, friendId);
        }
    }

    private void saveDialogToCache(QBDialog dialog, String roomJidId) {
        DatabaseManager.saveDialog(context, dialog, roomJidId);
    }

    private void notifyFriendAboutInvitation(QBDialog dialog,
            Integer friendId) throws XMPPException, SmackException {
        long time = DateUtils.getCurrentTime();
        QBPrivateChat chat = privateChatManager.createChat(friendId, privateChatMessageListener);
        QBChatMessage chatMessage = ChatUtils.createRoomNotificationMessage(context, dialog);
        String roomJidId = friendId + Consts.EMPTY_STRING;
        String attachUrl = Consts.EMPTY_STRING;
        chatMessage.setProperty(propertyDateSent, time + Consts.EMPTY_STRING);
        chatMessage.setProperty(propertySaveToHistory, valuePropertySaveToHistory);
        chatMessage.setProperty(propertyNotificationTypeCreating, valuePropertyNotificationTypeCreating);
        chat.sendMessage(chatMessage);
        saveMessageToCache(new DialogMessageCache(roomJidId, user.getId(), chatMessage.getBody(), attachUrl,
                time, true));
    }

    public QBFile loadAttachFile(File inputFile) throws Exception {
        QBFile file = null;
        try {
            file = QBContent.uploadFileTask(inputFile, true, (String) null);
        } catch (QBResponseException exc) {
            throw new Exception(context.getString(R.string.dlg_fail_upload_attach));
        }

        return file;
    }

    public synchronized void login(QBUser user) throws XMPPException, IOException, SmackException {
        if (!QBChatService.isInitialized()) {
            QBChatService.init(context);
            chatService = QBChatService.getInstance();
        }
        if (!chatService.isLoggedIn() && user != null) {
            chatService.login(user);
            chatService.startAutoSendPresence(AUTO_PRESENCE_INTERVAL_IN_SECONDS);
            this.user = user;
        }
    }

    public synchronized void logout() throws QBResponseException, SmackException.NotConnectedException {
        chatService.stopAutoSendPresence();
        chatService.logout();
    }

    public void destroy() {
        chatService.destroy();
    }

    public boolean isLoggedIn() {
        return chatService != null && chatService.isLoggedIn();
    }

    public List<QBDialog> getDialogs() throws QBResponseException, XMPPException, SmackException {
        Bundle bundle = new Bundle();
        QBCustomObjectRequestBuilder customObjectRequestBuilder = new QBCustomObjectRequestBuilder();
        customObjectRequestBuilder.setPagesLimit(Consts.CHATS_DIALOGS_PER_PAGE);
        List<QBDialog> chatDialogsList = QBChatService.getChatDialogs(null, customObjectRequestBuilder,
                bundle);

        if (!chatDialogsList.isEmpty()) {
            List<String> roomJidList = ChatUtils.getRoomJidListFromDialogs(chatDialogsList);
            for (String roomJid : roomJidList) {
                joinRoomChat(roomJid);
            }
            saveDialogsToCache(chatDialogsList);
        }

        return chatDialogsList;
    }

    private void saveDialogsToCache(List<QBDialog> dialogsList) {
        DatabaseManager.saveDialogs(context, dialogsList);
    }

    public List<QBHistoryMessage> getDialogMessages(QBDialog dialog, String roomJidId,
            long lastDateLoad) throws QBResponseException {
        Bundle bundle = new Bundle();
        QBCustomObjectRequestBuilder customObjectRequestBuilder = new QBCustomObjectRequestBuilder();
        customObjectRequestBuilder.setPagesLimit(Consts.DIALOG_MESSAGES_PER_PAGE);
        if (lastDateLoad != Consts.ZERO_LONG_VALUE) {
            customObjectRequestBuilder.gt(com.quickblox.internal.module.chat.Consts.MESSAGE_DATE_SENT,
                    lastDateLoad);
        }

        List<QBHistoryMessage> dialogMessagesList = QBChatService.getDialogMessages(dialog,
                customObjectRequestBuilder, bundle);

        boolean isPrivate = QBDialogType.PRIVATE.equals(dialog.getType());

        if (dialogMessagesList != null) {
            // TODO SF temp
            deleteMessagesByRoomJidId(roomJidId);
            //---
            saveChatMessagesToCache(dialogMessagesList, roomJidId, isPrivate);
        }
        return dialogMessagesList;
    }

    private void saveChatMessagesToCache(List<QBHistoryMessage> dialogMessagesList, String roomJidId,
            boolean isPrivate) {
        DatabaseManager.saveChatMessages(context, dialogMessagesList, roomJidId, isPrivate);
    }

    public void updateStatusMessage(String messageId, boolean isRead) {
        DatabaseManager.updateStatusMessage(context, messageId, isRead);
    }

    private void deleteMessagesByRoomJidId(String roomJidId) {
        DatabaseManager.deleteMessagesByRoomJidId(context, roomJidId);
    }

    private void deleteDialogs() {
        DatabaseManager.deleteAllDialogs(context);
    }

    private void notifyMessageReceived(QBChatMessage chatMessage, Friend friend, String jidID) {
        Intent intent = new Intent(QBServiceConsts.GOT_CHAT_MESSAGE);
        String messageBody = getMessageBody(chatMessage);
        String extraChatMessage;
        String fullname = friend.getFullname();
        if (TextUtils.isEmpty(messageBody)) {
            extraChatMessage = context.getResources().getString(R.string.file_was_attached);
        } else {
            extraChatMessage = messageBody;
        }

        intent.putExtra(QBServiceConsts.EXTRA_CHAT_MESSAGE, extraChatMessage);
        intent.putExtra(QBServiceConsts.EXTRA_SENDER_CHAT_MESSAGE, fullname);
        intent.putExtra(QBServiceConsts.EXTRA_ROOM_JID, jidID);

        LocalBroadcastManager.getInstance(context).sendBroadcast(intent);
    }

    private String getMessageBody(QBChatMessage chatMessage) {
        String messageBody = chatMessage.getBody();
        if (TextUtils.isEmpty(messageBody)) {
            messageBody = Consts.EMPTY_STRING;
        }
        return messageBody;
    }

    private void tryJoinRoomChat(String roomJid) {
        try {
            joinRoomChat(roomJid);
        } catch (Exception e) {
            ErrorUtils.logError(e);
        }
    }

    public List<Integer> getRoomOnlineParticipantList(String roomJid) throws XMPPException {
        return new ArrayList<Integer>(roomChatManager.getRoom(roomJid).getOnlineRoomUserIds());
    }

    public void leaveRoomChat(
            String roomJid) throws XMPPException, SmackException.NotConnectedException, QBResponseException {
        roomChatManager.getRoom(roomJid).leave();

        List<Integer> userIdsList = new ArrayList<Integer>();
        userIdsList.add(user.getId());
        removeUsersFromRoom(roomJid, userIdsList);

        DatabaseManager.deleteDialogByRoomJid(context, roomJid);
    }

    public void addUsersToRoom(String roomJid, List<Integer> userIdsList) throws QBResponseException {
        QBDialog dialog = DatabaseManager.getDialogByRoomJidId(context, roomJid);

        QBCustomObjectUpdateBuilder requestBuilder = new QBCustomObjectUpdateBuilder();
        requestBuilder.push(com.quickblox.internal.module.chat.Consts.DIALOG_OCCUPANTS,
                userIdsList.toArray());
        updateDialog(dialog.getDialogId(), dialog.getName(), requestBuilder);
    }

    public void removeUsersFromRoom(String roomJid, List<Integer> userIdsList) throws QBResponseException {
        QBDialog dialog = DatabaseManager.getDialogByRoomJidId(context, roomJid);

        QBCustomObjectUpdateBuilder requestBuilder = new QBCustomObjectUpdateBuilder();
        requestBuilder.pullAll(com.quickblox.internal.module.chat.Consts.DIALOG_OCCUPANTS,
                userIdsList.toArray());
        updateDialog(dialog.getDialogId(), dialog.getName(), requestBuilder);
    }

    public void updateRoomName(String roomJid, String newName) throws QBResponseException {
        QBDialog dialog = DatabaseManager.getDialogByRoomJidId(context, roomJid);

        QBCustomObjectUpdateBuilder requestBuilder = new QBCustomObjectUpdateBuilder();
        updateDialog(dialog.getDialogId(), newName, requestBuilder);
    }

    private void updateDialog(String dialogId, String newName,
            QBCustomObjectUpdateBuilder requestBuilder) throws QBResponseException {
        QBDialog updatedDialog = roomChatManager.updateDialog(dialogId, newName, requestBuilder);
        DatabaseManager.saveDialog(context, updatedDialog, updatedDialog.getRoomJid());
    }

    private class PrivateChatMessageListener implements QBMessageListener<QBPrivateChat> {

        @Override
        public void processMessage(QBPrivateChat privateChat, QBChatMessage chatMessage) {
            Friend friend = DatabaseManager.getFriendById(context, chatMessage.getSenderId());
            if (friend == null) {
                friend = new Friend();
                friend.setFullname(Consts.EMPTY_STRING + chatMessage.getSenderId());
            }
            long time;
            String roomJidId;
            String attachUrl = null;
            if (ChatUtils.isNotificationMessage(chatMessage)) {
                time = DateUtils.getCurrentTime();
                QBDialog dialog = ChatUtils.parseDialogFromMessage(chatMessage, chatMessage.getBody(), time);
                roomJidId = dialog.getRoomJid();
                tryJoinRoomChat(roomJidId);
                saveDialogToCache(dialog, roomJidId);
                saveMessageToCache(new DialogMessageCache(chatMessage.getSenderId() + Consts.EMPTY_STRING, chatMessage.getSenderId(),
                        chatMessage.getBody(), attachUrl, time, false));
            } else {
                time = Long.parseLong(chatMessage.getProperty(propertyDateSent).toString());
                attachUrl = ChatUtils.getAttachUrlIfExists(chatMessage);
                roomJidId = chatMessage.getSenderId() + Consts.EMPTY_STRING;
                saveMessageToCache(new DialogMessageCache(roomJidId, chatMessage.getSenderId(), chatMessage.getBody(), attachUrl, time, false));
            }
            String privateJidId = chatMessage.getSenderId() + Consts.EMPTY_STRING;
            notifyMessageReceived(chatMessage, friend, privateJidId);
        }
    }

    private class RoomChatMessageListener implements QBMessageListener<QBRoomChat> {

        @Override
        public void processMessage(QBRoomChat roomChat, QBChatMessage chatMessage) {
            Friend friend = DatabaseManager.getFriendById(context, chatMessage.getSenderId());
            String attachUrl = ChatUtils.getAttachUrlIfExists(chatMessage);
            String roomJid = roomChat.getJid();
            long time = Long.parseLong(chatMessage.getProperty(propertyDateSent).toString());
            saveMessageToCache(new DialogMessageCache(roomJid, chatMessage.getSenderId(),
                    chatMessage.getBody(), attachUrl, time, false));
            if (!chatMessage.getSenderId().equals(user.getId())) {
                // TODO IS handle logic when friend is not in the friend list
                notifyMessageReceived(chatMessage, friend, roomJid);
            }
        }
    }
}