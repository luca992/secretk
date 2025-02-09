package io.eqoty.secretk.types

import io.eqoty.cosmwasm.std.types.Coin
import io.eqoty.secretk.types.proto.MsgSendProto
import io.eqoty.secretk.types.proto.ProtoMsg
import io.eqoty.secretk.types.proto.toProto

/***
 *  MsgSend represents a message to send coins from one account to another.
 */
class MsgSend(
    val fromAddress: String,
    val toAddress: String,
    /** Funds to send to the address */
    val amount: List<Coin> = emptyList(),
) : UnencryptedMsg<MsgSendProto> {

    override val sender: String
        get() = fromAddress

    override suspend fun toProto(): ProtoMsg<MsgSendProto> {
        val msgContent = MsgSendProto(
            fromAddress = fromAddress,
            toAddress = toAddress,
            amount = amount.map { it.toProto() },
        )

        return ProtoMsg(
            typeUrl = "/cosmos.bank.v1beta1.MsgSend",
            value = msgContent
        )
    }

    override suspend fun toAmino(): MsgAmino {
        return MsgSendAmino(
            MsgSendAminoData(
                fromAddress = fromAddress,
                toAddress = toAddress,
                amount = amount,
            )
        )
    }

}

