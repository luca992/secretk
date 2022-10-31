package io.eqoty

import co.touchlab.kermit.Logger
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.secretk.extensions.accesscontrol.PermitFactory
import io.eqoty.secretk.types.MsgExecuteContract
import io.eqoty.secretk.types.MsgInstantiateContract
import io.eqoty.secretk.types.MsgStoreCode
import io.eqoty.secretk.types.TxOptions
import io.eqoty.secretk.types.extensions.Permission
import io.eqoty.secretk.wallet.DirectSigningWallet
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.FileSystem
import okio.Path
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

expect val fileSystem: FileSystem
expect val snip721ReferenceImplWasmGz: Path

class ClientTests {
    val json: Json = Json
    val grpcGatewayEndpoint = "https://api.pulsar.scrttestnet.com"
    val mnemonic = "sand check forward humble between movie language siege where social crumble mouse"
    var wallet = DirectSigningWallet(mnemonic)

    @BeforeTest
    fun beforeEach() = runTest {
        platformBeforeEach()
    }


    @Test
    fun testPubKeyToAddress() = runTest {
        val accAddress = "secret1fdkdmflnrysrvg3nc4ym7zdsn2rm5atszn9q2y"
        assertEquals(accAddress, wallet.getAccounts()[0].address)
    }


    @Test
    fun testCreateViewingKeyAndUseToQuery() = runTest {
        val contractAddress = "secret1lz4m46vpdn8f2aj8yhtnexus40663udv7hhprm"
        val accAddress = wallet.getAccounts()[0].address
        val client = SigningCosmWasmClient.init(
            grpcGatewayEndpoint,
            accAddress,
            wallet
        )
        println("Querying nft contract info")
        val contractInfoQuery = """{"contract_info": {}}"""
        val contractInfo = client.queryContractSmart(contractAddress, contractInfoQuery)
        println("nft contract info response: $contractInfo")

        assertEquals("""{"contract_info":{"name":"lucasfirstsnip721","symbol":"luca721"}}""", contractInfo)
        // Entropy: Secure implementation is left to the client, but it is recommended to use base-64 encoded random bytes and not predictable inputs.
        val entropy = "Another really random thing??"
        val handleMsg = """{ "create_viewing_key": {"entropy": "$entropy"} }"""
        println("Creating viewing key")
        val msgs = listOf(
            MsgExecuteContract(
                sender = accAddress,
                contractAddress = contractAddress,
                msg = handleMsg,
            )
        )
        val simulate = client.simulate(msgs)
        val gasLimit = (simulate.gasUsed.toDouble() * 1.1).toInt()
        val response = client.execute(
            msgs,
            txOptions = TxOptions(gasLimit = gasLimit)
        )
        println("viewing key response: ${response.data}")
        val viewingKey = json.parseToJsonElement(response.data[0])
            .jsonObject["viewing_key"]!!
            .jsonObject["key"]!!.jsonPrimitive.content
        println("Querying Num Tokens")
        val numTokensQuery =
            """
            {
                "num_tokens": {
                    "viewer": {
                        "address": "$accAddress",
                        "viewing_key": "$viewingKey"
                    }
                }
            }
            """

        val numTokens = client.queryContractSmart(contractAddress, numTokensQuery)
        println("Num Tokens Response: $numTokens")
    }

    @Test
    fun testQueryWithPermit() = runTest {
        val contractAddress = "secret1lz4m46vpdn8f2aj8yhtnexus40663udv7hhprm"
        val accAddress = wallet.getAccounts()[0].address
        val client = SigningCosmWasmClient.init(
            grpcGatewayEndpoint,
            accAddress,
            wallet
        )
        println("Querying nft contract info")
        val contractInfoQuery = """{"contract_info": {}}"""
        val contractInfo = client.queryContractSmart(contractAddress, contractInfoQuery)
        println("nft contract info response: $contractInfo")

        assertEquals("""{"contract_info":{"name":"lucasfirstsnip721","symbol":"luca721"}}""", contractInfo)

        println("Querying Num Tokens")
        val permit = PermitFactory.newPermit(
            wallet,
            client.senderAddress,
            client.getChainId(),
            "Test",
            listOf(contractAddress),
            listOf(Permission.Owner),
        )
        val numTokensQuery =
            """
                {
                    "with_permit": {
                        "permit": ${Json.encodeToString(permit)},
                        "query": { "num_tokens": {} }
                    }
                }
            """

        val numTokens = client.queryContractSmart(contractAddress, numTokensQuery)
        println("Num Tokens Response: $numTokens")
    }

    @Test
    fun testStoreCode() = runTest {
        val accAddress = wallet.getAccounts()[0].address
        val client = SigningCosmWasmClient.init(
            grpcGatewayEndpoint,
            accAddress,
            wallet
        )
        val wasmBytes =
            fileSystem.read(snip721ReferenceImplWasmGz) {
                readByteArray()
            }
        val msgs = listOf(
            MsgStoreCode(
                sender = accAddress,
                wasmByteCode = wasmBytes.toUByteArray(),
            )
        )
        val simulate = client.simulate(msgs)
        val gasLimit = (simulate.gasUsed.toDouble() * 1.1).toInt()
        val response = client.execute(
            msgs,
            txOptions = TxOptions(gasLimit = gasLimit)
        )

        val codeId = response.logs[0].events
            .find { it.type == "message" }
            ?.attributes
            ?.find { it.key == "code_id" }?.value!!
        Logger.i("codeId:  $codeId")


        // contract hash, useful for contract composition
        val codeInfo = client.getCodeInfoByCodeId(codeId)
        Logger.i("code hash: ${codeInfo.codeHash}")

        assertEquals("5b64d22c7774b11cbc3aac55168d11f624a51921679b005df7d59487d254c892", codeInfo.codeHash)
    }


    @Test
    fun testInstantiateContractWithCodeHash() = runTest {
        testInstantiateContract("5b64d22c7774b11cbc3aac55168d11f624a51921679b005df7d59487d254c892")
    }

    @Test
    fun testInstantiateContractWithNullCodeHash() = runTest {
        testInstantiateContract(null)
    }

    @Test
    fun testInstantiateContractWithEmptyStringCodeHash() = runTest {
        testInstantiateContract("")
    }

    @Test
    fun testInstantiateContractWithBlankStringCodeHash() = runTest {
        testInstantiateContract("  ")
    }

    suspend fun testInstantiateContract(codeHash: String?) {
        val codeId = 13526
        val accAddress = wallet.getAccounts()[0].address
        val client = SigningCosmWasmClient.init(
            grpcGatewayEndpoint,
            accAddress,
            wallet
        )
        val initMsg =
            """
            {
                "name": "lucasfirstsnip721",
                "symbol": "luca721",
                "entropy": "sadfsadfasdfvabadfb",
                "config": {
                    "public_token_supply": false,
                    "public_owner": true
                }
            }
            """
        val msgs = listOf(
            MsgInstantiateContract(
                codeId = codeId,
                sender = accAddress,
                codeHash = codeHash,
                initMsg = initMsg,
                label = "My Snip721" + ceil(Random.nextDouble() * 10000),
            )
        )
        val simulate = client.simulate(msgs)
        val gasLimit = (simulate.gasUsed.toDouble() * 1.1).toInt()
        val instantiateResponse = client.execute(
            msgs,
            txOptions = TxOptions(gasLimit = gasLimit)
        )
        val contractAddress = instantiateResponse.logs[0].events
            .find { it.type == "message" }
            ?.attributes
            ?.find { it.key == "contract_address" }?.value!!
        Logger.i("contract address:  $contractAddress")
        assertContains(contractAddress, "secret1")
    }

}