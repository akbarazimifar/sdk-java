
import junit.framework.TestCase;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import chat.dim.Content;
import chat.dim.ID;
import chat.dim.Immortals;
import chat.dim.InstantMessage;
import chat.dim.Meta;
import chat.dim.ReliableMessage;
import chat.dim.SecureMessage;
import chat.dim.User;

import chat.dim.cpu.CommandProcessor;
import chat.dim.crypto.SymmetricKey;
import chat.dim.protocol.Command;
import chat.dim.protocol.ContentType;
import chat.dim.protocol.GroupCommand;
import chat.dim.protocol.TextContent;
import chat.dim.protocol.group.JoinCommand;

import chat.dim.core.Barrack;
import chat.dim.core.Transceiver;
import chat.dim.cpu.ContentProcessor;

import chat.dim.KeyCache;
import chat.dim.KeyStore;
import chat.dim.cpu.HandshakeCommandProcessor;
import chat.dim.cpu.TextContentProcessor;

public class Tests extends TestCase {

    static Barrack barrack;
    static KeyCache keyStore;
    static Transceiver transceiver;

    static {
        ContentProcessor.register(ContentType.TEXT, TextContentProcessor.class);
        CommandProcessor.register(Command.HANDSHAKE, HandshakeCommandProcessor.class);

        barrack = MyFacebook.getInstance();

        // keystore
        try {
            Map keys = new HashMap();
            keyStore = KeyStore.getInstance();
            boolean changed = keyStore.updateKeys(keys);
            keyStore.flush();

        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            keyStore = null;
        }

        // transceiver
        transceiver = new Transceiver();
        transceiver.setEntityDelegate(barrack);
        transceiver.setCipherKeyDelegate(keyStore);
    }

    @Test
    public void testUser() {

        ID identifier = barrack.getID(Immortals.MOKI);
        User user = barrack.getUser(identifier);
        Log.info("user: " + user);
    }

    @Test
    public void testGroupCommand() {
        ID groupID = ID.getInstance("Group-1280719982@7oMeWadRw4qat2sL4mTdcQSDAqZSo7LH5G");
        JoinCommand join = new JoinCommand(groupID);
        Log.info("join: " + join);
        assertEquals(GroupCommand.JOIN, join.command);
    }

    @Test
    public void testTransceiver() {

        ID sender = ID.getInstance("moki@4WDfe3zZ4T7opFSi3iDAKiuTnUHjxmXekk");
        ID receiver = ID.getInstance("hulk@4YeVEN3aUnvC1DNUufCq1bs9zoBSJTzVEj");

        Content content = new TextContent("Hello");

        InstantMessage<ID, SymmetricKey> iMsg = new InstantMessage<>(content, sender, receiver);
        iMsg.setDelegate(transceiver);
        SecureMessage<ID, SymmetricKey> sMsg = transceiver.encryptMessage(iMsg);
        ReliableMessage<ID, SymmetricKey> rMsg = transceiver.signMessage(sMsg);

        SecureMessage<ID, SymmetricKey> sMsg2 = transceiver.verifyMessage(rMsg);
        InstantMessage iMsg2 = transceiver.decryptMessage(sMsg2);

        Log.info("send message: " + iMsg2);
    }

    @Test
    public void testBarrack() {
        ID identifier = barrack.getID("moky@4DnqXWdTV8wuZgfqSCX9GjE2kNq7HJrUgQ");

        Meta meta = barrack.getMeta(identifier);
        Log.info("meta: " + meta);

        identifier = barrack.getID("moki@4WDfe3zZ4T7opFSi3iDAKiuTnUHjxmXekk");
        User user = barrack.getUser(identifier);

//        identifier = ID.getInstance("Group-1280719982@7oMeWadRw4qat2sL4mTdcQSDAqZSo7LH5G");
//
//        Group group = barrack.getGroup(identifier);

        Map<ID, Meta> map = new HashMap<>();
        identifier = null;
        meta = map.get(identifier);
        Log.info("meta: " + meta);
    }
}
