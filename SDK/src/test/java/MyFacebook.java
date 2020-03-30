
import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import chat.dim.*;
import chat.dim.crypto.*;
import chat.dim.format.JSON;

public class MyFacebook extends Facebook {
    private static MyFacebook ourInstance = new MyFacebook();
    public static MyFacebook getInstance() { return ourInstance; }
    private MyFacebook() {
        super();
    }

    @Override
    public boolean saveMeta(Meta meta, ID identifier) {
        return false;
    }

    // immortals
    private Immortals immortals = new Immortals();

    // memory caches
    private Map<ID, PrivateKey> privateKeyMap = new HashMap<>();
    private Map<ID, Profile>    profileMap    = new HashMap<>();

    // "/sdcard/chat.dim.sechat/.mkm/"
    public String directory = "/tmp/.mkm/";

    // "/sdcard/chat.dim.sechat/.mkm/{address}.meta"
    @Override
    protected Meta loadMeta(ID identifier) {
        // load from JsON file
        try {
            Map dict = readJSONFile(identifier.address + ".meta");
            return Meta.getInstance(dict);
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean saveProfile(Profile profile) {
        return false;
    }

    @Override
    protected Profile loadProfile(ID identifier) {
        return null;
    }

    @Override
    public boolean saveContacts(List<ID> contacts, ID user) {
        return false;
    }

    @Override
    protected List<ID> loadContacts(ID user) {
        return null;
    }

    @Override
    public boolean saveMembers(List<ID> members, ID group) {
        return false;
    }

    @Override
    public List<User> getLocalUsers() {
        return null;
    }

    private Map readJSONFile(String filename) throws IOException {
        String json = readTextFile(filename);
        if (json == null) {
            return null;
        }
        return (Map) JSON.decode(json);
    }

    private String readTextFile(String filename) throws IOException {
        byte[] data = readBinaryFile(filename);
        if (data == null) {
            return null;
        }
        return new String(data, "UTF-8");
    }

    private byte[] readBinaryFile(String filename) throws IOException {
        File file = new File(directory, filename);
        if (!file.exists()) {
            return null;
        }
        FileInputStream fis = new FileInputStream(file);
        int size = fis.available();
        byte[] data = new byte[size];
        int len = fis.read(data, 0, size);
        fis.close();
        assert len == size;
        return data;
    }

    //---- Private Key

    protected boolean cachePrivateKey(PrivateKey key, ID identifier) {
        privateKeyMap.put(identifier, key);
        return true;
    }

    //---- Profile

    protected boolean cacheProfile(Profile profile) {
        ID identifier = ID.getInstance(profile.getIdentifier());
        profileMap.put(identifier, profile);
        return true;
    }

    //-------- SocialNetworkDataSource

    @Override
    public User getUser(ID identifier) {
        User user = super.getUser(identifier);
        if (user != null) {
            return user;
        }
        user = new User(identifier);
        cache(user);
        return user;
    }

    @Override
    public Group getGroup(ID identifier) {
        Group group = super.getGroup(identifier);
        if (group == null) {
            group = new Group(identifier);
            cache(group);
        }
        return group;
    }

    //---- EntityDataSource

    @Override
    public Meta getMeta(ID identifier) {
        Meta meta = super.getMeta(identifier);
        if (meta == null) {
            meta = immortals.getMeta(identifier);
            if (meta != null) {
                return meta;
            }
            meta = loadMeta(identifier);
            if (meta != null) {
                cache(meta, identifier);
            }
        }
        return meta;
    }

    @Override
    public Profile getProfile(ID entity) {
        return profileMap.get(entity);
    }

    //---- UserDataSource

    @Override
    public List<ID> getContacts(ID user) {
        return immortals.getContacts(user);
    }

    @Override
    public SignKey getPrivateKeyForSignature(ID user) {
        SignKey key = privateKeyMap.get(user);
        if (key == null) {
            key = immortals.getPrivateKeyForSignature(user);
        }
        return key;
    }

    @Override
    public List<VerifyKey> getPublicKeysForVerification(ID user) {
        // NOTICE: return nothing to use meta.key
        return null;
    }

    @Override
    public List<DecryptKey> getPrivateKeysForDecryption(ID user) {
        PrivateKey key = privateKeyMap.get(user);
        if (key == null) {
            return immortals.getPrivateKeysForDecryption(user);
        } else if (key instanceof DecryptKey) {
            List<DecryptKey> keys = new ArrayList<>();
            keys.add((DecryptKey) key);
            return keys;
        }
        return null;
    }

    @Override
    public EncryptKey getPublicKeyForEncryption(ID user) {
        // NOTICE: return nothing to use profile.key or meta.key
        return null;
    }

    //---- GroupDataSource

    @Override
    public ID getFounder(ID group) {
        return null;
    }

    @Override
    public ID getOwner(ID group) {
        return null;
    }

    @Override
    public List<ID> getMembers(ID group) {
        return null;
    }
}
