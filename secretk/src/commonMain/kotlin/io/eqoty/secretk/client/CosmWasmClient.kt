package io.eqoty.secretk.client

import io.eqoty.secretk.BroadcastMode
import io.eqoty.secretk.types.response.*
import io.eqoty.secretk.types.result.GetNonceResult
import io.eqoty.secretk.utils.EncryptionUtils
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

open class CosmWasmClient protected constructor(
    apiUrl: String,
    encryptionUtils: EncryptionUtils,
    broadcastMode: BroadcastMode = BroadcastMode.Block,
    var chainId: String? = null
) {
    internal val restClient = RestClient(apiUrl, broadcastMode, encryptionUtils)

    /** Any address the chain considers valid (valid bech32 with proper prefix) */
    protected var anyValidAddress: String? = null

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
        val account = getAccount(address)
        return GetNonceResult(
            accountNumber = account.accountNumber!!,
            sequence = account.sequence!!,
        )
    }

    suspend fun getBalance(address: String): BalanceResponse {
        return try {
            restClient.get("/cosmos/bank/v1beta1/balances/${address}")
        } catch (t: Throwable) {
            throw Error(
                "Account $address does not exist on chain. Send some tokens there before trying to query nonces.",
            )
        }
    }

    suspend fun getAccount(address: String): io.eqoty.secretk.types.response.Account {
        return try {
            restClient.get<AccountResponse>("/cosmos/auth/v1beta1/accounts/${address}").account
        } catch (t: Throwable) {
            throw Error(
                "Account $address does not exist on chain. Send some tokens there before trying to query nonces.",
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
        val txResponse: SimulateTxsResponse = restClient.postTx(tx, true)
        if (txResponse.code != null && txResponse.code != 0) {
            throw Error("Simulating transaction failed with code ${txResponse.code} (message: ${txResponse.message}). Details: ${txResponse.details}")
        }
        return txResponse
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