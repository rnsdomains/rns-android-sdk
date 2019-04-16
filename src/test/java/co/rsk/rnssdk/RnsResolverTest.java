package co.rsk.rnssdk;

import org.bouncycastle.util.encoders.Hex;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.web3j.compat.Compat;
import org.web3j.crypto.Hash;
import org.web3j.ens.NameHash;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.methods.response.EthAccounts;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.ClientTransactionManager;

import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

import co.rsk.rnssdk.contracts.PublicResolver;
import co.rsk.rnssdk.contracts.RNS;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class RnsResolverTest {

    private String rnsAddress = null;

    private static final long GAS_LIMIT = 6721975l;
    private static final byte[] HASH = Hex.decode("89ad40fcd44690fb5aa90e0fa51637c1b2d388f8056d68430d41c8284a6a7d5e");
    public static final String OK_STATUS = "0x1";
    public static final String OK_STATUS_RSK = "0x01";
    public static final String NAME = "foo.rsk";

    private List<String> ACCOUNTS = new ArrayList<>();
    private byte[] BYTES_FOR_TEST = new byte[32];
    private RnsResolver resolver;

    @Before
    public void setUp() {
        //HttpService defaults to http://localhost:8545/ if you want to change this put the URI in
        //the HttpService constructor

        Web3jService httpService = new HttpService();
        //Web3jService httpService = new HttpService("http://localhost:4444/");
        Web3j web3 = Web3j.build(httpService);
        try {

            SecureRandom random = new SecureRandom();
            random.nextBytes(BYTES_FOR_TEST);

            EthAccounts ethAccounts = web3.ethAccounts().send();
            ACCOUNTS = ethAccounts.getAccounts();
            assertTrue(ACCOUNTS.size() >= 2);
            //here we define the FROM address for every transaction;
            ClientTransactionManager transactionManager =
                    new ClientTransactionManager(web3, ACCOUNTS.get(0));
            RNS rnsContract;
            PublicResolver resolverContract;
                rnsContract = RNS.deploy(web3,
                        transactionManager,
                        BigInteger.valueOf(1000000l),
                        BigInteger.valueOf(GAS_LIMIT)).send();

                resolverContract = PublicResolver.deploy(
                        web3,
                        transactionManager,
                        BigInteger.valueOf(1000000l),
                        BigInteger.valueOf(GAS_LIMIT),
                        rnsContract.getContractAddress()).send();
            byte[] rskHash = Hash.sha3("rsk".getBytes(Compat.UTF_8));
            byte[] fooHash = Hash.sha3("foo".getBytes(Compat.UTF_8));
            byte[] nodeFooDotRsk = NameHash.nameHashAsBytes(NAME);
            String resolverAddress = resolverContract.getContractAddress();
            rnsContract.setSubnodeOwner(new byte[32], rskHash, ACCOUNTS.get(0)).send();
            rnsContract.setSubnodeOwner(NameHash.nameHashAsBytes("rsk"), fooHash, ACCOUNTS.get(0)).send();
            rnsContract.setResolver(nodeFooDotRsk, resolverAddress).send();
            //System.out.println(NAME+" -> NameHash:"+Hex.toHexString(nodeFooDotRsk)+" -> Map into: "+ACCOUNTS.get(0));
            resolverContract.setAddr(nodeFooDotRsk, ACCOUNTS.get(0)).send();
            assertEquals(resolverContract.addr(nodeFooDotRsk).send(), ACCOUNTS.get(0));
            resolverContract.setContent(nodeFooDotRsk, BYTES_FOR_TEST).send();
            //System.out.println(resolverContract.getContractAddress());
            resolver = new RnsResolver(web3, resolverAddress);
            //resolverContract.setAddr(NameHash.nameHashAsBytes(NAME), ACCOUNTS.get(1)).send();
            assertTrue("rns contract is not valid", rnsContract.isValid());
            assertTrue("resolver contract is not valid", resolverContract.isValid());
            assertTrue("resolver contract is not valid", resolver.getResolver(null).isValid());
        } catch (IOException e) {
            fail("IOException: " + e.getMessage() + " make sure node is in localhost:8545");
        } catch (Exception e) {
            fail(e.getMessage());
        }

    }

    private void waitMinned(Web3j web3j, TransactionReceipt receipt) throws Exception {
        System.out.println("STATUS: "+receipt.getStatus());
        assertTrue(receipt.getStatus().equals(OK_STATUS) || receipt.getStatus().equals(OK_STATUS_RSK));
        while (true) {
            EthGetTransactionReceipt transactionReceipt = web3j
                    .ethGetTransactionReceipt(receipt.getTransactionHash())
                    .send();
            if (transactionReceipt.getResult() != null) {
                break;
            }
            Thread.sleep(15000);
        }
    }

    @Test
    public void testSetAddress() {
        try {
            String expectedAccount = ACCOUNTS.get(1);
            assertTrue(resolver.setAddress(NAME, expectedAccount, ACCOUNTS.get(0)));
            String address = resolver.getAddress(NAME);
            assertEquals(address, expectedAccount);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testSetNotOwnedAddress() {
        try {
            String expectedAccount = ACCOUNTS.get(1);
            assertTrue(resolver.setAddress(NAME, expectedAccount, ACCOUNTS.get(0)));
            assertTrue(resolver.setAddress(NAME, expectedAccount, ACCOUNTS.get(0)));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testSetContent() {
        try {
            resolver.setContent(NAME, HASH, ACCOUNTS.get(0));
            byte[] result = resolver.content(NAME);
            assertTrue(FastByteComparisons.equalBytes(result, HASH));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testSetContentToNotOwnedAddress() {
        try {
            resolver.setContent(NAME, HASH, ACCOUNTS.get(1));
            fail("An exception should rise");
        } catch (Exception e) {
            String msg = "VM Exception while processing transaction: revert";
            assertTrue(e.getMessage().indexOf(msg) != -1);
        }
    }

    //Ignored by now
    @Ignore
    @Test
    public void testImplementHasWithAddr() {
        try {
            boolean result = resolver.has(NAME, "addr");
            assertTrue(result);
        } catch (Exception e) {

        }
    }

    @Test
    public void testFunctionNotImplementedHas() {
        try {
            boolean result = resolver.has(NAME, "boom");
            assertFalse(result);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testShouldResolveName() {
        try {
            String result = resolver.getAddress(NAME);
            assertEquals(ACCOUNTS.get(0), result);
        } catch (Exception e) {
            fail(e.getMessage());
        }

    }

    @Test
    public void testShouldResolveContent() {
        try {
            byte[] result = resolver.content(NAME);
            assertTrue(FastByteComparisons.equalBytes(result, BYTES_FOR_TEST));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testShouldntResolveName() {
        try {
            String result = resolver.getAddress("foo");
            assertEquals("0x0000000000000000000000000000000000000000", result);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testShouldImplementSupportsInterface() {
        try {
            assertTrue(resolver.supportsInterface("3b3b57de") &&
                    resolver.supportsInterface("d8389dc5"));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
}