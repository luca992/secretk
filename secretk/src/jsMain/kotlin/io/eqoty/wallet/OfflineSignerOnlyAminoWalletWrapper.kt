package io.eqoty.wallet

import ext.libsodium.com.ionspin.kotlin.crypto.toUByteArray
import io.eqoty.tx.proto.SignMode
import io.eqoty.types.StdSignDoc
import jslibs.secretjs.AminoWallet
import kotlinx.coroutines.await
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class OfflineSignerOnlyAminoWalletWrapper(
    val wallet: AminoWallet,
) : Wallet {

    override suspend fun getSignMode(): SignMode? = null

    override suspend fun getAccounts(): List<AccountData> {
        return wallet.getAccounts().await().map { it.toCommonType() }
    }

    override suspend fun signAmino(signerAddress: String, signDoc: StdSignDoc): AminoSignResponse {
        val result = wallet.signAmino(signerAddress, JSON.parse(Json.encodeToString(signDoc))).await()
        return Json.decodeFromString(JSON.stringify(result))
    }

}