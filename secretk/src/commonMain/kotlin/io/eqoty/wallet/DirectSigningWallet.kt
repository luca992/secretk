package io.eqoty.wallet

import io.eqoty.types.proto.SignDocProto
import io.eqoty.types.proto.SignMode
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf


class DirectSigningWallet(
    mnemonic: String,
) : BaseWallet(mnemonic) {
    override suspend fun getSignMode(): SignMode? = null

    fun signDirect(address: String, signDoc: SignDocProto): SignResponse {
        if (address != this.address) {
            throw Error("Address $address not found in wallet")
        }
        val signBytes = ProtoBuf.encodeToByteArray(signDoc).toUByteArray()
        return SignResponse(
            signed = signDoc,
            signature = sign(signBytes)
        )
    }

}