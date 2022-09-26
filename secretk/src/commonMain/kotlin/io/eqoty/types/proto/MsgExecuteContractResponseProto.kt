package io.eqoty.types.proto

import kotlinx.serialization.protobuf.ProtoNumber

/***
 * Reference:
 * https://github.com/scrtlabs/SecretNetwork/blob/master/proto/secret/compute/v1beta1/msg.proto
 */
@kotlinx.serialization.Serializable
class MsgExecuteContractResponseProto(
    @ProtoNumber(1) val data: ByteArray
) : MsgProto()