package io.eqoty.secretk.client

import io.eqoty.secretk.BroadcastMode
import io.eqoty.secretk.types.Account
import io.eqoty.secretk.types.response.*
import io.eqoty.secretk.types.result.GetNonceResult
import io.eqoty.secretk.utils.EncryptionUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

open class CosmWasmClient protected constructor(
    apiUrl: String,
    encryptionUtils: EncryptionUtils,
    broadcastMode: BroadcastMode = BroadcastMode.Block
) {
    internal val restClient = RestClient(apiUrl, broadcastMode, encryptionUtils)

    /** Any address the chain considers valid (valid bech32 with proper prefix) */
    protected var anyValidAddress: String? = null
    private var chainId: String? = null

    suspend fun getCodeInfoByCodeId(codeId: String): CodeInfoResponse.CodeInfo =
        restClient.getCodeInfoByCodeId(codeId)

    suspend fun getCodeHashByContractAddr(addr: String): String =
        restClient.getCodeHashByContractAddr(addr)

    // The /node_info endpoint
    suspend fun nodeInfo(): NodeInfoResponse {
        return restClient.get("/node_info")
    }

    suspend fun getChainId(): String {
        if (chainId == null) {
            val response = nodeInfo()
            val chainId = response.node_info.network
            if (chainId == "") throw Error("Chain ID must not be empty")
            this.chainId = chainId
        }

        return chainId!!
    }

    suspend fun getNonce(address: String): GetNonceResult {
        val account = this.getAccount(address)
        if (account?.address == null) {
            throw Error(
                "Account $address does not exist on chain. Send some tokens there before trying to query nonces.",
            )
        }
        return GetNonceResult(
            accountNumber = account.accountNumber,
            sequence = account.sequence,
        )
    }

    suspend fun getAccount(address: String): Account? {
        val account = this.restClient.authAccounts(address)
        if (account.address == null || account.address === "") {
            return null
        } else {
            this.anyValidAddress = account.address
            return Account(
                address = account.address,
                balance = account.coins!!,
                pubkey = account.public_key,
                accountNumber = account.account_number!!,
                sequence = account.sequence!!,
            )
        }
    }

    suspend fun postTx(tx: UByteArray): TxResponseData {
        val response: TxResponse = restClient.postTx(tx, false)
        val txResponse = response.tx_response
        if (txResponse.txhash.isBlank()) {
            throw Error("Unexpected response data format")
        }
        if (txResponse.txhash.contains("""^([0-9A-F][0-9A-F])+$""")) {
            throw Error("Received ill-formatted txhash. Must be non-empty upper-case hex")
        }

        if (txResponse.code != 0) {
            throw Error("Broadcasting transaction failed with code ${txResponse.code} (codespace: ${txResponse.codespace}). Log: ${txResponse.rawLog}")
        }

        return txResponse
    }

    suspend fun postSimulateTx(tx: UByteArray): SimulateTxsResponse {
        val response: SimulateTxsResponse = restClient.postTx(tx, true)
        return response
    }


    /**
     * Makes a smart query on the contract, returns the parsed JSON document.
     *
     * Promise is rejected when contract does not exist.
     * Promise is rejected for invalid query format.
     * Promise is rejected for invalid response format.
     *
     * Note: addedParams allows for query string additions such as "&height=1234567"
     */
    suspend fun queryContractSmart(
        contractAddress: String,
        queryMsg: String,
        contractCodeHash: String? = null,
    ): String {
        try {
            return this.restClient.queryContractSmart(
                contractAddress,
                Json.parseToJsonElement(queryMsg).jsonObject,
                contractCodeHash,
            )
        } catch (t: Throwable) {
            if (t is Error) {
                if (t.message?.startsWith("not found: contract") == true) {
                    throw Error("No contract found at address $contractAddress")
                } else {
                    throw t
                }
            } else {
                throw t
            }
        }
    }
}