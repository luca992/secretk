package io.eqoty.secretk.extensions.accesscontrol

import io.eqoty.secretk.types.*
import io.eqoty.secretk.types.extensions.Permission
import io.eqoty.secretk.types.extensions.Permit
import io.eqoty.secretk.wallet.Wallet
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object PermitFactory {

    internal fun newSignDoc(
        chainId: String,
        permitName: String,
        allowedTokens: List<String>,
        permissions: List<Permission>,
    ) =
        StdSignDoc(
            chainId = chainId,
            accountNumber = "0", // Must be 0
            sequence = "0", // Must be 0
            fee = StdFee(
                amount = listOf(Coin(amount = "0", denom = "uscrt")), // Must be 0 uscrt
                gas = "1", // Must be 1
            ),
            msgs = listOf(
                MsgQueryPermitAmino(
                    MsgQueryPermitAminoData(
                        allowedTokens,
                        permissions,
                        permitName,
                    )
                ),
            ),
            memo = "", // Must be empty
        )

    suspend fun newPermit(
        wallet: Wallet,
        owner: String,
        chainId: String,
        permitName: String,
        allowedTokens: List<String>,
        permissions: List<Permission>,
    ): Permit {
        var signature = newPermitWithTargetSpecificWallet(
            wallet, owner, chainId, permitName, allowedTokens, permissions
        )
        if (signature == null) {
            println(Json.encodeToString(newSignDoc(chainId, permitName, allowedTokens, permissions)))
            val aa=  wallet.signAmino(
                owner,
                newSignDoc(chainId, permitName, allowedTokens, permissions),
            )
            println(aa.signature.toString())
            signature = aa.signature
        }

        return Permit(
            params = Permit.Params(
                chainId = chainId,
                permitName = permitName,
                allowedTokens = allowedTokens,
                permissions = permissions,
            ),
            signature = signature,
        )
    }
}

internal expect suspend fun PermitFactory.newPermitWithTargetSpecificWallet(
    wallet: Wallet,
    owner: String,
    chainId: String,
    permitName: String,
    allowedTokens: List<String>,
    permissions: List<Permission>,
): StdSignature?