package io.eqoty.dapp.secret.utils

import com.ionspin.kotlin.bignum.integer.BigInteger
import io.eqoty.cosmwasm.std.types.Coin
import io.eqoty.dapp.secret.types.ContractInstance
import io.eqoty.secret.std.contract.msg.Snip20Msgs
import io.eqoty.secret.std.contract.msg.SnipMsgs
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.secretk.types.MsgExecuteContract
import io.eqoty.secretk.types.TxOptions
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

object BalanceUtils {

    private val snip20ToAddressViewingKey =
        mutableMapOf<ContractInstance, MutableMap<String, SnipMsgs.ExecuteAnswer.ViewingKey>>()

    private val httpClient = HttpClient {
        expectSuccess = true
    }

    private val zeroUscrt = Coin("0", "uscrt")

    suspend fun fillUpFromFaucet(
        nodeInfo: NodeInfo,
        client: SigningCosmWasmClient,
        targetBalance: Int,
        address: String = client.senderAddress,
    ) {
        var balance = try {
            getScrtBalance(client, address)
        } catch (t: Throwable) {
            logger.i(t.message ?: "getScrtBalance failed")
            logger.i("Attempting to fill address $address from faucet")
            zeroUscrt
        }
        while (balance.amount.toInt() < targetBalance) {
            try {
                getFromFaucet(client, nodeInfo, address)
            } catch (t: Throwable) {
                throw RuntimeException("failed to get tokens from faucet: $t")
            }
            var newBalance = balance
            val maxTries = 10
            var tries = 0
            while (balance == newBalance) {
                // the api doesn't update immediately. So retry until the balance changes
                newBalance = try {
                    getScrtBalance(client, address)
                } catch (t: Throwable) {
                    logger.i("getScrtBalance try ${++tries}/$maxTries failed with: ${t.message}")
                    zeroUscrt
                }
                if (tries >= maxTries) {
                    throw RuntimeException("getScrtBalance did not update after $maxTries trys")
                }
            }
            balance = newBalance
            logger.i("got tokens from faucet. New balance: $balance, target balance: $targetBalance")
        }
    }

    suspend fun getScrtBalance(client: SigningCosmWasmClient, address: String = client.senderAddress): Coin {
        val balance = client.getBalance(address).balances
        return balance.getOrNull(0) ?: zeroUscrt
    }

    suspend fun getSnip20Balance(
        client: SigningCosmWasmClient,
        senderAddress: String = client.senderAddress,
        contractInstance: ContractInstance
    ): BigInteger? {
        val viewingKey =
            snip20ToAddressViewingKey[contractInstance]?.get(senderAddress)
                ?: createViewingKey(client, senderAddress, contractInstance).apply {
                    snip20ToAddressViewingKey[contractInstance]?.set(senderAddress, this)
                }
        val query =
            Json.encodeToString(Snip20Msgs.Query(balance = Snip20Msgs.Query.Balance(senderAddress, viewingKey.key)))
        val response = client.queryContractSmart(
            contractInstance.address,
            query,
            contractInstance.codeInfo.codeHash
        )
        return Json.decodeFromString<Snip20Msgs.QueryAnswer>(response).balance!!.amount
    }

    private suspend fun getFromFaucet(
        client: SigningCosmWasmClient,
        nodeInfo: NodeInfo, address: String
    ): String {
        val response = when (nodeInfo) {
            is Pulsar2 -> {
                httpClient.post(nodeInfo.faucetAddressEndpoint) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                            {
                                "denom": "uscrt",
                                "address": "$address"
                            }
                        """
                    )
                }
            }

            else -> {
                httpClient.get(nodeInfo.createFaucetAddressGetEndpoint(address))
            }
        }
        return response.bodyAsText()
    }

    private suspend fun createViewingKey(
        client: SigningCosmWasmClient,
        senderAddress: String,
        contractInstance: ContractInstance
    ): SnipMsgs.ExecuteAnswer.ViewingKey {
        val originalSenderAddress = client.senderAddress
        client.senderAddress = senderAddress
        val entropy = Random.nextBytes(40).encodeBase64()
        val msg = Json.encodeToString(SnipMsgs.Execute(createViewingKey = SnipMsgs.Execute.CreateViewingKey(entropy)))
        val msgs = listOf(
            MsgExecuteContract(
                sender = client.senderAddress,
                contractAddress = contractInstance.address,
                codeHash = contractInstance.codeInfo.codeHash,
                msg = msg,
            )
        )
        val simulate = client.simulate(msgs)
        val gasLimit = (simulate.gasUsed.toDouble() * 1.1).toInt()

        val response = client.execute(
            msgs,
            txOptions = TxOptions(gasLimit = gasLimit)
        )
        client.senderAddress = originalSenderAddress
        return Json.decodeFromString<SnipMsgs.ExecuteAnswer>(response.data[0]).viewingKey!!
    }


}