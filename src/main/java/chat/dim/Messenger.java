/* license: https://mit-license.org
 *
 *  DIM-SDK : Decentralized Instant Messaging Software Development Kit
 *
 *                                Written in 2019 by Moky <albert.moky@gmail.com>
 *
 * ==============================================================================
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Albert Moky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 * ==============================================================================
 */
package chat.dim;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.core.Transceiver;
import chat.dim.crypto.EncryptKey;
import chat.dim.crypto.SymmetricKey;
import chat.dim.protocol.FileContent;
import chat.dim.protocol.ForwardContent;
import chat.dim.protocol.ReceiptCommand;
import chat.dim.protocol.TextContent;

public abstract class Messenger extends Transceiver implements ConnectionDelegate {

    private final Map<String, Object> context;

    private WeakReference<MessengerDelegate> delegateRef;

    private MessageProcessor processor;

    public Messenger() {
        super();
        context = new HashMap<>();
        processor = null;
        delegateRef = null;
    }

    //
    //  Delegate for sending data
    //
    public MessengerDelegate getDelegate() {
        if (delegateRef == null) {
            return null;
        }
        return delegateRef.get();
    }

    public  void setDelegate(MessengerDelegate delegate) {
        assert delegate != null : "Messenger delegate should not be empty";
        delegateRef = new WeakReference<>(delegate);
    }

    //
    //  Environment variables as context
    //
    public Map<String, Object> getContext() {
        return context;
    }

    public Object getContext(String key) {
        return context.get(key);
    }

    public void setContext(String key, Object value) {
        if (value == null) {
            context.remove(key);
        } else {
            context.put(key, value);
        }
    }

    //
    //  Data source for getting entity info
    //
    public Facebook getFacebook() {
        Object facebook = context.get("facebook");
        if (facebook == null) {
            facebook = getEntityDelegate();
            assert facebook instanceof Facebook : "facebook error: " + facebook;
        }
        return (Facebook) facebook;
    }

    protected User select(ID receiver) {
        Facebook facebook = getFacebook();
        List<User> users = facebook.getLocalUsers();
        if (users == null || users.size() == 0) {
            throw new NullPointerException("local users should not be empty");
        } else if (receiver.isBroadcast()) {
            // broadcast message can decrypt by anyone, so just return current user
            return users.get(0);
        }
        if (receiver.getType().isGroup()) {
            // group message (recipient not designated)
            List<ID> members = facebook.getMembers(receiver);
            if (members == null || members.size() == 0) {
                // TODO: query group members
                //       (do it by application)
                return null;
            }
            for (User item : users) {
                if (members.contains(item.identifier)) {
                    // set this item to be current user?
                    return item;
                }
            }
        } else {
            // 1. personal message
            // 2. split group message
            assert receiver.getType().isUser() : "error: " + receiver;
            for (User item : users) {
                if (receiver.equals(item.identifier)) {
                    // set this item to be current user?
                    return item;
                }
            }
        }
        return null;
    }

    private SecureMessage trim(SecureMessage sMsg) {
        ID receiver = getFacebook().getID(sMsg.envelope.receiver);
        User user = select(receiver);
        if (user == null) {
            // current users not match
            return null;
        } else if (receiver.getType().isGroup()) {
            // trim group message
            sMsg = sMsg.trim(user.identifier);
        }
        return sMsg;
    }

    //-------- Transform

    @Override
    public SecureMessage verifyMessage(ReliableMessage rMsg) {
        // Notice: check meta before calling me
        Meta meta;
        try {
            meta = Meta.getInstance(rMsg.getMeta());
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            meta = null;
        }
        Facebook facebook = getFacebook();
        ID sender = facebook.getID(rMsg.envelope.sender);
        if (meta == null) {
            meta = facebook.getMeta(sender);
            if (meta == null) {
                // NOTICE: the application will query meta automatically
                // save this message in a queue waiting sender's meta response
                suspendMessage(rMsg);
                //throw new NullPointerException("failed to get meta for sender: " + sender);
                return null;
            }
        } else {
            // [Meta Protocol]
            // save meta for sender
            if (!facebook.saveMeta(meta, sender)) {
                throw new RuntimeException("save meta error: " + sender + ", " + meta);
            }
        }
        return super.verifyMessage(rMsg);
    }

    @Override
    public SecureMessage encryptMessage(InstantMessage iMsg) {
        SecureMessage sMsg = super.encryptMessage(iMsg);
        Object group = iMsg.content.getGroup();
        if (group != null) {
            // NOTICE: this help the receiver knows the group ID
            //         when the group message separated to multi-messages,
            //         if don't want the others know you are the group members,
            //         remove it.
            sMsg.envelope.setGroup(group);
        }
        // NOTICE: copy content type to envelope
        //         this help the intermediate nodes to recognize message type
        sMsg.envelope.setType(iMsg.content.type);
        return sMsg;
    }

    @Override
    public InstantMessage decryptMessage(SecureMessage sMsg) {
        // 0. trim message
        sMsg = trim(sMsg);
        if (sMsg == null) {
            // not for you?
            return null;
        }
        // 1. decrypt message
        InstantMessage iMsg = super.decryptMessage(sMsg);
        // 2. check top-secret message
        Content content = iMsg.content;
        if (content instanceof ForwardContent) {
            // [Forward Protocol]
            // do it again to drop the wrapper,
            // the secret inside the content is the real message
            ReliableMessage rMsg = ((ForwardContent) content).forwardMessage;
            sMsg = verifyMessage(rMsg);
            if (sMsg != null) {
                // verify OK, try to decrypt
                InstantMessage secret = decryptMessage(sMsg);
                if (secret != null) {
                    // decrypt success
                    return secret;
                }
                // NOTICE: decrypt failed, not for you?
                //         check content type in subclass, if it's a 'forward' message,
                //         it means you are asked to re-pack and forward this message
            }
        }
        return iMsg;
    }

    //-------- De/serialize message, content and symmetric key

    @Override
    public byte[] serializeMessage(ReliableMessage rMsg) {
        // public
        return super.serializeMessage(rMsg);
    }

    @Override
    public ReliableMessage deserializeMessage(byte[] data) {
        // public
        return super.deserializeMessage(data);
    }

    //-------- InstantMessageDelegate

    @Override
    public byte[] encryptContent(Content content, Map<String, Object> password, InstantMessage iMsg) {
        SymmetricKey key = getSymmetricKey(password);
        assert key == password && key != null : "irregular symmetric key: " + password;
        // check attachment for File/Image/Audio/Video message content
        if (content instanceof FileContent) {
            FileContent file = (FileContent) content;
            byte[] data = file.getData();
            // encrypt and upload file data onto CDN and save the URL in message content
            data = key.encrypt(data);
            String url = getDelegate().uploadData(data, iMsg);
            if (url != null) {
                // replace 'data' with 'URL'
                file.setUrl(url);
                file.setData(null);
            }
        }
        return super.encryptContent(content, key, iMsg);
    }

    @Override
    public byte[] encryptKey(Map<String, Object> password, Object receiver, InstantMessage iMsg) {
        Facebook facebook = getFacebook();
        ID to = facebook.getID(receiver);
        EncryptKey key = facebook.getPublicKeyForEncryption(to);
        if (key == null) {
            Meta meta = facebook.getMeta(to);
            if (meta == null) {
                // save this message in a queue waiting receiver's meta response
                suspendMessage(iMsg);
                //throw new NullPointerException("failed to get encrypt key for receiver: " + receiver);
                return null;
            }
        }
        return super.encryptKey(password, receiver, iMsg);
    }

    //-------- SecureMessageDelegate

    @Override
    @SuppressWarnings("unchecked")
    public Content decryptContent(byte[] data, Map<String, Object> password, SecureMessage sMsg) {
        SymmetricKey key = getSymmetricKey(password);
        assert key == password && key != null : "irregular symmetric key: " + password;
        Content content = super.decryptContent(data, password, sMsg);
        if (content == null) {
            return null;
        }
        // check attachment for File/Image/Audio/Video message content
        if (content instanceof FileContent) {
            FileContent file = (FileContent) content;
            InstantMessage iMsg = new InstantMessage(content, sMsg.envelope);
            // download from CDN
            byte[] fileData = getDelegate().downloadData(file.getUrl(), iMsg);
            if (fileData == null) {
                // save symmetric key for decrypted file data after download from CDN
                file.setPassword(key);
            } else {
                // decrypt file data
                file.setData(key.decrypt(fileData));
                file.setUrl(null);
            }
        }
        return content;
    }

    //-------- Send message

    /**
     *  Send message content to receiver
     *
     * @param content - message content
     * @param receiver - receiver ID
     * @return true on success
     */
    public boolean sendContent(Content content, ID receiver) {
        return sendContent(content, receiver, null, true);
    }

    public boolean sendContent(Content content, ID receiver, Callback callback, boolean split) {
        User user = getFacebook().getCurrentUser();
        assert user != null : "current user not found";
        InstantMessage iMsg = new InstantMessage(content, user.identifier, receiver);
        return sendMessage(iMsg, callback, split);
    }

    /**
     *  Send instant message (encrypt and sign) onto DIM network
     *
     * @param iMsg - instant message
     * @param callback - if needs callback, set it here
     * @param split - whether split group message
     * @return true on success
     */
    public boolean sendMessage(InstantMessage iMsg, Callback callback, boolean split) {
        // Send message (secured + certified) to target station
        ReliableMessage rMsg = signMessage(encryptMessage(iMsg));
        Facebook facebook = getFacebook();
        ID receiver = facebook.getID(iMsg.envelope.receiver);
        boolean OK = true;
        if (split && receiver.getType().isGroup()) {
            // split for each members
            List<ID> members = facebook.getMembers(receiver);
            List<SecureMessage> messages;
            if (members == null || members.size() == 0) {
                messages = null;
            } else {
                messages = rMsg.split(members);
            }
            if (messages == null) {
                // failed to split message, send it to group
                OK = sendMessage(rMsg, callback);
            } else {
                for (Message msg : messages) {
                    if (!sendMessage((ReliableMessage) msg, callback)) {
                        OK = false;
                    }
                }
            }
        } else {
            OK = sendMessage(rMsg, callback);
        }
        // TODO: if OK, set iMsg.state = sending; else set iMsg.state = waiting
        return OK;
    }

    protected boolean sendMessage(ReliableMessage rMsg, Callback callback) {
        CompletionHandler handler = new CompletionHandler() {
            @Override
            public void onSuccess() {
                callback.onFinished(rMsg, null);
            }

            @Override
            public void onFailed(Error error) {
                callback.onFinished(rMsg, error);
            }
        };
        byte[] data = serializeMessage(rMsg);
        return getDelegate().sendPackage(data, handler);
    }

    //-------- Message

    /**
     * Re-pack and deliver (Top-Secret) message to the real receiver
     *
     * @param msg - top-secret message
     * @return receipt on success
     */
    public Content forwardMessage(ReliableMessage msg) {
        ID receiver = getFacebook().getID(msg.envelope.receiver);
        Content secret = new ForwardContent(msg);
        if (sendContent(secret, receiver)) {
            return new ReceiptCommand("message forwarded");
        } else {
            return new TextContent("failed to forward your message");
        }
    }

    /**
     * Deliver message to everyone@everywhere, including all neighbours
     *
     * @param msg - broadcast message
     * @return receipt on success
     */
    public Content broadcastMessage(ReliableMessage msg) {
        // NOTICE: this function is for Station
        //         if the receiver is a grouped broadcast ID,
        //         split and deliver to everyone
        assert getFacebook().getID(msg.envelope.receiver).isBroadcast() : "receiver error: " + msg;
        return null;
    }

    /**
     * Deliver message to the receiver, or to neighbours
     *
     * @param msg - reliable message
     * @return receipt on success
     */
    public Content deliverMessage(ReliableMessage msg) {
        // NOTICE: this function is for Station
        //         if the station cannot decrypt this message,
        //         it means you should deliver it to the receiver
        return null;
    }

    /**
     * Save the message into local storage
     *
     * @param msg - instant message
     * @return true on success
     */
    public abstract boolean saveMessage(InstantMessage msg);

    /**
     *  Suspend the received message for the sender's meta
     *
     * @param msg - message received from network
     * @return false on error
     */
    public abstract boolean suspendMessage(ReliableMessage msg);

    /**
     *  Suspend the sending message for the receiver's meta
     *
     * @param msg - instant message to be sent
     * @return false on error
     */
    public abstract boolean suspendMessage(InstantMessage msg);

    //-------- ConnectionDelegate

    @Override
    public byte[] onReceivePackage(byte[] data) {
        // 1. deserialize message
        ReliableMessage rMsg = deserializeMessage(data);
        // 2. process message
        Content response = process(rMsg);
        if (response == null) {
            // nothing to response
            return null;
        }
        // 3. pack response
        Facebook facebook = getFacebook();
        User user = facebook.getCurrentUser();
        assert user != null : "failed to get current user";
        ID sender = facebook.getID(rMsg.envelope.sender);
        InstantMessage iMsg = new InstantMessage(response, user.identifier, sender);
        ReliableMessage nMsg = signMessage(encryptMessage(iMsg));
        // serialize message
        return serializeMessage(nMsg);
    }

    // NOTICE: if you want to filter the response, override me
    protected Content process(ReliableMessage rMsg) {
        if (processor == null) {
            processor = new MessageProcessor(this);
        }
        return processor.process(rMsg);
    }
}
