package io.eqoty.secretk.wallet

import cash.z.ecc.android.bip39.Mnemonics
import cash.z.ecc.android.bip39.toSeed
import io.eqoty.kryptools.*
import io.eqoty.secret.std.types.StdSignature
import io.eqoty.secretk.types.StdSignDoc
import io.eqoty.secretk.types.proto.SignDocProto
import io.eqoty.secretk.utils.Address.pubkeyToAddress
import io.eqoty.secretk.utils.logger
import io.eqoty.secretk.utils.toByteString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json


/**
 * The Cosmoshub derivation path in the form `m/44'/118'/0'/0/a`
 * with 0-based account index `a`.
 * The Secret Network derivation path in the form `m/44'/529'/0'/0/a`
 * with 0-based account index `a`.
 */
fun makeSecretNetworkPath(a: UInt): Array<Slip10RawIndex> = arrayOf(
    Slip10RawIndex.hardened(44.toUInt()),
    Slip10RawIndex.hardened(529.toUInt()),
    Slip10RawIndex.hardened(0.toUInt()),
    Slip10RawIndex.normal(0.toUInt()),
    Slip10RawIndex.normal(a),
)

enum class PrehashType(val type: String) {
    SHA256("sha256"),
    SHA512("sha512")
}

sealed class BaseWallet : Wallet {
    val hdPath: Array<Slip10RawIndex>
    val bech32Prefix: String
    val addressToAccountSigningData: MutableMap<String, AccountSigningData> = mutableMapOf()
    val accountAddresses get() = addressToAccountSigningData.keys

    constructor(
        mnemonic: String?,
        hdPath: Array<Slip10RawIndex> = makeSecretNetworkPath(0.toUInt()),
        bech32Prefix: String = "secret"
    ) {
        this.hdPath = hdPath
        this.bech32Prefix = bech32Prefix
        addAccount(mnemonic)
    }

    constructor(
        privkey: UByteArray,
        hdPath: Array<Slip10RawIndex> = makeSecretNetworkPath(0.toUInt()),
        bech32Prefix: String = "secret"
    ) {
        this.hdPath = hdPath
        this.bech32Prefix = bech32Prefix
        addAccount(privkey)
    }

    fun addAccount(mnemonic: String? = null): AccountSigningData {
        val mnemonicCode = if (mnemonic != null) {
            Mnemonics.MnemonicCode(mnemonic)
        } else {
            val randMnemonic = Mnemonics.MnemonicCode(Mnemonics.WordCount.COUNT_12)
            randMnemonic
        }
        val seed = mnemonicCode.toSeed().toUByteArray()
        val result = Slip10.derivePath(Slip10Curve.Secp256k1, seed, hdPath)
        val privkey = result.privkey.getPadded(32)
        val uncompressed = try {
            Secp256k1.makeKeypair(privkey).pubkey
        } catch (t: Throwable) {
            if (mnemonic == null) {
                // todo: remove this after https://github.com/eqoty-labs/kryptools/issues/2 fixed
                logger.w("A bad key was randomly generated. Trying again. See https://github.com/eqoty-labs/kryptools/issues/2")
                return addAccount()
            } else throw t
        }
        val pubkey = Secp256k1.compressPubkey(uncompressed)
        val address = pubkeyToAddress(encodeSecp256k1Pubkey(pubkey), bech32Prefix)
        addressToAccountSigningData[address] =
            AccountSigningData(address, Algo.secp256k1, pubkey, privkey, mnemonicCode.words)
        return addressToAccountSigningData[address]!!
    }

    fun addAccount(privkey: UByteArray): AccountSigningData {
        val uncompressed = Secp256k1.makeKeypair(privkey).pubkey
        val pubkey = Secp256k1.compressPubkey(uncompressed)
        val address = pubkeyToAddress(encodeSecp256k1Pubkey(pubkey), bech32Prefix)
        addressToAccountSigningData[address] = AccountSigningData(address, Algo.secp256k1, pubkey, privkey)
        return addressToAccountSigningData[address]!!
    }

    /**
     * Removes all account data for an address including private keys.
     *
     * @return Returns [AccountData] if account is successfully removed from wallet.
     */
    fun removeAccount(address: String): AccountData? {
        return addressToAccountSigningData.remove(address)?.publicData
    }

    val accounts get() = addressToAccountSigningData.values.map { it.publicData }.toList()

    override suspend fun getAccount(address: String): AccountData? {
        return addressToAccountSigningData[address]?.publicData
    }

    /**
     * Get AccountData array from wallet. Rejects if not enabled.
     */
    override suspend fun getAccounts(): List<AccountData> = accounts


    private fun prehash(bytes: UByteArray, type: PrehashType?): UByteArray {
        return when (type) {
            null -> bytes
            PrehashType.SHA256 -> bytes.toByteString().sha256().toByteArray().toUByteArray()
            PrehashType.SHA512 -> bytes.toByteString().sha512().toByteArray().toUByteArray()
        }
    }


    /**
     * Creates and returns a signature
     */
    fun sign(
        signerAddress: String,
        signBytes: UByteArray,
        prehashType: PrehashType = PrehashType.SHA256
    ): StdSignature {
        val account =
            addressToAccountSigningData[signerAddress] ?: throw Error("Address $signerAddress not found in wallet")
        val messageHash = prehash(signBytes, prehashType)
        val signature = Secp256k1.createSignature(messageHash, account.privkey)
        val fixedLengthSignature = signature.r.getPadded(32) + signature.s.getPadded(32)
        return encodeSecp256k1Signature(account.publicData.pubkey, fixedLengthSignature)
    }

    override suspend fun signAmino(signerAddress: String, signDoc: StdSignDoc): AminoSignResponse {
        val signBytes = Json.encodeToString(signDoc).encodeToByteArray().toUByteArray()
        return AminoSignResponse(
            signed = signDoc,
            signature = sign(signerAddress, signBytes)
        )
    }
}


data class SignResponse(
    /**
     * The sign doc that was signed.
     * This may be different from the input signDoc when the signer modifies it as part of the signing process.
     */
    val signed: SignDocProto,
    val signature: StdSignature
)