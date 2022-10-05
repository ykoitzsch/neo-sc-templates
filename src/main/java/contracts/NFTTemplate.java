package contracts;

import io.neow3j.devpack.ByteString;
import io.neow3j.devpack.Contract;
import io.neow3j.devpack.Hash160;
import io.neow3j.devpack.Helper;
import io.neow3j.devpack.Iterator;
import io.neow3j.devpack.List;
import io.neow3j.devpack.Map;
import io.neow3j.devpack.Runtime;
import io.neow3j.devpack.Storage;
import io.neow3j.devpack.StorageContext;
import io.neow3j.devpack.StorageMap;
import io.neow3j.devpack.Iterator.Struct;
import io.neow3j.devpack.annotations.ContractSourceCode;
import io.neow3j.devpack.annotations.DisplayName;
import io.neow3j.devpack.annotations.ManifestExtra;
import io.neow3j.devpack.annotations.Permission;
import io.neow3j.devpack.annotations.OnDeployment;
import io.neow3j.devpack.annotations.OnNEP17Payment;
import io.neow3j.devpack.annotations.Safe;
import io.neow3j.devpack.annotations.SupportedStandard;
import io.neow3j.devpack.constants.CallFlags;
import io.neow3j.devpack.constants.FindOptions;
import io.neow3j.devpack.constants.NeoStandard;
import io.neow3j.devpack.contracts.ContractManagement;
import io.neow3j.devpack.contracts.StdLib;
import io.neow3j.devpack.events.Event2Args;
import io.neow3j.devpack.events.Event3Args;
import io.neow3j.devpack.events.Event4Args;

@Permission(contract = "*", methods = "*")
@SupportedStandard(neoStandard = NeoStandard.NEP_11)
@ManifestExtra(key = "Author", value = "")
@ManifestExtra(key = "Description", value = "")
@ManifestExtra(key = "Email", value = "")
@ManifestExtra(key = "Website", value = "")
@ContractSourceCode("")
@DisplayName("NFTTemplate")
@SuppressWarnings("unchecked")
public class NFTTemplate {

    // EVENTS

    @DisplayName("Transfer")
    static Event4Args<Hash160, Hash160, Integer, ByteString> onTransfer;

    @DisplayName("Payment")
    static Event3Args<Hash160, Integer, Object> onPayment;

    @DisplayName("Error")
    static Event2Args<String, String> error;

    // DEFAULT METADATA
    private static final String NAME = "name";
    private static final String DESC = "description";
    private static final String IMAGE = "image";
    private static final String TOKEN_URI = "tokenURI";
    private static final String TOKEN_ID = "tokenId";

    // NFT attributes
    private static final String ATTRIBUTES = "attributes";
    private static final String ATTRIBUTE_TRAIT_TYPE = "trait_type";
    private static final String ATTRIBUTE_VALUE = "value";
    private static final String ATTRIBUTE_DISPLAY_TYPE = "display_type";

    private static final StorageContext ctx = Storage.getStorageContext();

    // STORAGE KEYS
    private static final byte[] ownerkey = Helper.toByteArray((byte) 5);
    private static final byte[] totalSupplyKey = Helper.toByteArray((byte) 6);
    private static final byte[] tokensOfKey = Helper.toByteArray((byte) 7);
    private static final byte[] imageBaseUriKey = Helper.toByteArray((byte) 8);
    private static final byte[] currentSupplyKey = Helper.toByteArray((byte) 9);
    private static final byte[] isPausedKey = Helper.toByteArray((byte) 10);
    private static final byte[] mintPriceKey = Helper.toByteArray((byte) 11);
    private static final byte[] mintAssetKey = Helper.toByteArray((byte) 12);

    // STORAGE MAPS
    private static final StorageMap tokens = new StorageMap(ctx, Helper.toByteArray((byte) 101));
    private static final StorageMap balances = new StorageMap(ctx, Helper.toByteArray((byte) 102));
    private static final StorageMap ownerOfMap = new StorageMap(ctx, Helper.toByteArray((byte) 103));
    private static final StorageMap immutableTokenProperties = new StorageMap(ctx, (byte) 104);

    @OnNEP17Payment
    public static void onPayment(Hash160 from, int amount, Object data) throws Exception {
        if (isPaused()) {
            throw new Exception("onPayment_isPaused");
        }
        if (amount <= 0) {
            throw new Exception("onPayment_invalidAmount");
        }
        if (data == null) {
            if (currentSupply() >= totalSupply()) {
                throw new Exception("onPayment_soldOut");
            }
        }
        onPayment.fire(from, amount, data);
    }

    /* READ ONLY */

    @Safe
    public static Hash160 contractOwner() {
        return new Hash160(Storage.get(ctx, ownerkey));
    }

    @Safe
    public static String symbol() {
        return "TEMPLATE";
    }

    @Safe
    public static int decimals() {
        return 0;
    }

    @Safe
    public static int totalSupply() {
        return Storage.getInt(ctx.asReadOnly(), totalSupplyKey);
    }

    @Safe
    public static int balanceOf(Hash160 owner) {
        return getBalanceOf(owner);
    }

    @Safe
    public static int currentSupply() {
        return Storage.getIntOrZero(ctx.asReadOnly(), currentSupplyKey);
    }

    @Safe
    public static boolean isPaused() {
        return Storage.getInt(ctx.asReadOnly(), isPausedKey) == 1;
    }

    @Safe
    public static Iterator<ByteString> tokensOf(Hash160 owner) {
        return (Iterator<ByteString>) Storage.find(
                ctx.asReadOnly(),
                createStorageMapPrefix(owner, tokensOfKey),
                FindOptions.RemovePrefix);
    }

    @Safe
    public static List<String> tokensOfJson(Hash160 owner) throws Exception {
        Iterator<Struct<ByteString, ByteString>> iterator = (Iterator<Struct<ByteString, ByteString>>) Storage.find(
                ctx.asReadOnly(),
                createStorageMapPrefix(owner, tokensOfKey),
                FindOptions.RemovePrefix);
        List<String> tokens = new List<>();
        while (iterator.next()) {
            ByteString result = (ByteString) iterator.get().key;
            tokens.add(propertiesJson(result));
        }
        return tokens;
    }

    @Safe
    public static Hash160 ownerOf(ByteString tokenId) {
        ByteString owner = ownerOfMap.get(tokenId);
        if (owner == null) {
            return null;
        }
        return new Hash160(owner);
    }

    @Safe
    public static Iterator<Iterator.Struct<ByteString, ByteString>> tokens() {
        return (Iterator<Iterator.Struct<ByteString, ByteString>>) tokens.find(FindOptions.RemovePrefix);
    }

    @Safe
    public static Map<String, Object> properties(ByteString tokenId) throws Exception {
        TokenProperties tokenProps = (TokenProperties) new StdLib()
                .deserialize(immutableTokenProperties.get(tokenId));
        Map<String, Object> p = new Map<>();

        if (tokenProps == null) {
            throw new Exception("properties_tokenDoesNotExist");
        }
        p.put(TOKEN_ID, tokenProps.tokenId);
        p.put(NAME, tokenProps.name);
        p.put(DESC, tokenProps.description);
        p.put(IMAGE, tokenProps.image);
        p.put(TOKEN_URI, tokenProps.tokenUri);

        List<Map<String, Object>> attributes = new List<>();
        p.put(ATTRIBUTES, attributes);

        return p;
    }

    @Safe
    public static String propertiesJson(ByteString tokenId) throws Exception {
        return new StdLib().jsonSerialize(properties(tokenId));
    }

    @Safe
    public static int mintPrice() {
        return Storage.getInt(ctx.asReadOnly(), mintPriceKey);
    }

    @Safe
    public static Hash160 mintAsset() {
        return new Hash160(Storage.get(ctx.asReadOnly(), mintAssetKey));
    }

    /* READ & WRITE */

    public static boolean transfer(Hash160 to, ByteString tokenId, Object data) throws Exception {
        Hash160 owner = ownerOf(tokenId);
        if (owner == null) {
            throw new Exception("transfer_tokenDoesNotExist");
        }
        ownerOfMap.put(tokenId, to.toByteArray());
        new StorageMap(ctx, createStorageMapPrefix(owner, tokensOfKey)).delete(tokenId);
        new StorageMap(ctx, createStorageMapPrefix(to, tokensOfKey)).put(tokenId, 1);

        decrementBalanceByOne(owner);
        incrementBalanceByOne(to);

        onTransfer.fire(owner, to, 1, tokenId);

        if (new ContractManagement().getContract(to) != null) {
            Contract.call(to, "onNEP11Payment", CallFlags.All,
                    new Object[] { owner, 1, tokenId, data });
        }
        return true;
    }

    /* UTIL */

    public static void mint(Hash160 owner) throws Exception {
        int currentSupply = currentSupply();
        String cs = new StdLib().itoa(++currentSupply, 10);
        ByteString tokenId = new ByteString(cs);
        Map<String, Object> properties = new Map<>();
        properties.put(TOKEN_ID, cs);
        properties.put(DESC, "Description Placeholder");
        properties.put(TOKEN_URI, "");
        properties.put(NAME, "");
        properties.put(IMAGE, getImageBaseURI() + cs + ".png");

        incrementCurrentSupplyByOne();
        saveProperties(properties, tokenId);
        tokens.put(tokenId, tokenId);
        ownerOfMap.put(tokenId, owner);
        new StorageMap(ctx, createStorageMapPrefix(owner, tokensOfKey)).put(tokenId, 1);
        incrementBalanceByOne(owner);
        onTransfer.fire(null, owner, 1, tokenId);
    }

    private static void fireErrorAndAbort(String msg, String method) {
        error.fire(msg, method);
        Helper.abort();
    }

    private static void saveProperties(Map<String, Object> properties, ByteString tokenId) throws Exception {

        if (!properties.containsKey(NAME)) {
            throw new Exception("saveProperties_missingName");
        }

        if (!properties.containsKey(DESC)) {
            throw new Exception("saveProperties_missingDescription");
        }

        if (!properties.containsKey(IMAGE)) {
            throw new Exception("saveProperties_missingImage");
        }

        if (!properties.containsKey(TOKEN_URI)) {
            throw new Exception("saveProperties_missingTokenUri");
        }

        String name = (String) properties.get(NAME);
        String desc = (String) properties.get(DESC);
        String img = (String) properties.get(IMAGE);
        String uri = (String) properties.get(TOKEN_URI);

        TokenProperties tokenProps = new TokenProperties(tokenId, name, img, desc, uri);
        immutableTokenProperties.put(tokenId, new StdLib().serialize(tokenProps));
    }

    private static Map<String, Object> getAttributeMap(String trait, Object value) {
        Map<String, Object> m = new Map<>();
        m.put(ATTRIBUTE_TRAIT_TYPE, trait);
        m.put(ATTRIBUTE_VALUE, value);
        m.put(ATTRIBUTE_DISPLAY_TYPE, "");
        return m;
    }

    private static void incrementBalanceByOne(Hash160 owner) {
        balances.put(owner.toByteArray(), getBalanceOf(owner) + 1);
    }

    private static void decrementBalanceByOne(Hash160 owner) {
        balances.put(owner.toByteArray(), getBalanceOf(owner) - 1);
    }

    private static String getImageBaseURI() {
        return Storage.getString(ctx, imageBaseUriKey);
    }

    private static int getBalanceOf(Hash160 owner) {
        if (balances.get(owner.toByteArray()) == null) {
            return 0;
        }
        return balances.get(owner.toByteArray()).toInt();
    }

    private static void incrementCurrentSupplyByOne() {
        int updatedCurrentSupply = currentSupply() + 1;
        Storage.put(ctx, currentSupplyKey, updatedCurrentSupply);
    }

    public static byte[] createStorageMapPrefix(Hash160 owner, byte[] prefix) {
        return Helper.concat(prefix, owner.toByteArray());
    }

    /* PERMISSION CHECKS */

    private static void onlyOwner() throws Exception {
        if (!Runtime.checkWitness(contractOwner())) {
            throw new Exception("onlyOwner");
        }
    }

    /* OWNER ONLY METHODS */

    public static void updateImageBaseURI(String uri) throws Exception {
        onlyOwner();
        Storage.put(ctx, imageBaseUriKey, uri);
    }

    public static void updateTotalSupply(int amount) throws Exception {
        onlyOwner();
        if (amount < currentSupply() || amount <= 0) {
            throw new Exception("updateTotalSupply_invalidAmount");
        }
        Storage.put(ctx, totalSupplyKey, amount);
    }

    public static void updatePause(boolean paused) throws Exception {
        onlyOwner();
        Storage.put(ctx, isPausedKey, paused ? 1 : 0);
    }

    /* CONTRACT MANAGEMENT */

    @OnDeployment
    public static void deploy(Object data, boolean update) throws Exception {
        if (!update) {
            Object[] arr = (Object[]) data;

            Hash160 owner = (Hash160) arr[0];
            if (!Hash160.isValid(owner)) {
                throw new Exception("deploy_invalidOwner");
            }
            Storage.put(ctx, ownerkey, owner);

            String imageBaseURI = (String) arr[1];
            if (imageBaseURI.length() == 0) {
                throw new Exception("deploy_invalidImageBaseURI");
            }
            Storage.put(ctx, imageBaseUriKey, imageBaseURI);

            int totalSupply = (int) arr[2];
            if (totalSupply < 1) {
                throw new Exception("deploy_totalSupply");
            }
            Storage.put(ctx, totalSupplyKey, totalSupply);
            Storage.put(ctx, isPausedKey, 1);
            int mintPrice = (int) arr[3];
            if (mintPrice <= 0) {
                throw new Exception("deploy_mintPrice");
            }
            Storage.put(ctx, mintPriceKey, mintPrice);

            Hash160 mintAsset = (Hash160) arr[3];
            if (!Hash160.isValid(mintAsset)) {
                throw new Exception("deploy_mintAsset");
            }
            Storage.put(ctx, mintAssetKey, mintAsset);
        }
    }

    public static void update(ByteString script, String manifest) throws Exception {
        onlyOwner();
        if (script.length() == 0 && manifest.length() == 0) {
            throw new Exception("update_contractAndManifestEmpty");
        }
        new ContractManagement().update(script, manifest);
    }

    static class TokenProperties {

        public ByteString tokenId;
        public String name;
        public String image;
        public String description;
        public String tokenUri;

        public TokenProperties(ByteString tokenId, String name, String image, String description, String tokenUri) {
            this.tokenId = tokenId;
            this.name = name;
            this.image = image;
            this.description = description;
            this.tokenUri = tokenUri;
        }
    }

}