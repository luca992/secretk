import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.NoLiveLiterals
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.CanvasBasedWindow
import io.eqoty.secretk.client.SigningCosmWasmClient
import io.eqoty.utils.KeplrEnigmaUtils
import io.eqoty.wallet.OfflineSignerOnlyAminoWalletWrapper
import jslibs.keplrwallet.types.Keplr
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.await
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.js.Promise

@OptIn(ExperimentalComposeUiApi::class)
@NoLiveLiterals
fun main() {
    application {
        val chain = Chain.Pulsar3
////        val client = getClientWithMetamaskWallet(Chain.Pulsar2)
////        val client = setupEthWalletConnectAndGetWallet(Chain.Pulsar2)
        val wallet = getClientWithKeplrWallet(chain)
////        val client = setupCosmosWalletConnectAndGetWallet(Chain.Secret4, WalletConnectModal.Keplr)
        val accAddress = wallet.getAccounts()[0].address
        println(accAddress)
        val enigmaUtils = when (wallet) {
            is OfflineSignerOnlyAminoWalletWrapper -> KeplrEnigmaUtils(wallet.keplr, chain.id)
            else -> {
                TODO()
            }
        }
        val client = SigningCosmWasmClient(
            chain.grpcGatewayEndpoint, wallet, encryptionUtils = enigmaUtils
        )
//        console.log(client)
        CanvasBasedWindow("secretk demo") {
            Column(modifier = Modifier.fillMaxSize()) {
                SampleApp(client, accAddress) {
//                    Row {
//                        Button({
//                            if (client.wallet is OfflineSignerOnlyAminoWalletWrapper) {
//                                val keplr = (client.wallet as OfflineSignerOnlyAminoWalletWrapper).keplr
//                                (keplr.suggestToken(
//                                    Chain.Secret4.id,
//                                    "secret153wu605vvp934xhd4k9dtd640zsep5jkesstdm",
//                                    "the_viewing_key",
//                                    true
//                                ) as Promise<Unit>).then {
//                                    console.log("token suggested")
//                                }
//                            }
//                        }) {
//                            Text("Suggest token")
//                        }
//                    }
//                    Row {
//                        Button({
//                            if (client.wallet is OfflineSignerOnlyAminoWalletWrapper) {
//                                val keplr = (client.wallet as OfflineSignerOnlyAminoWalletWrapper).keplr
//                                (keplr.getSecret20QueryAuthorization(
//                                    Chain.Secret4.id,
//                                    "secret153wu605vvp934xhd4k9dtd640zsep5jkesstdm",
//                                ) as Promise<dynamic>).then { result: dynamic ->
//                                    console.log("Get Query Authorization: ${JSON.stringify(result)}")
//                                }
//                            }
//                        }) {
//                            Text("Get Query Authorization")
//                        }
//                    }
                }
            }
        }
    }
}

fun application(block: suspend () -> Unit) {
    MainScope().launch {
        block()
    }
}

//language=JavaScript
private fun getKeplr(): Keplr? {
    js(
        code = "return window.keplr"
    )
}

//language=JavaScript
private fun getOfflineSignerOnlyAmino(): JsAny? {
    js(
        code = "return window.getOfflineSignerOnlyAmino"
    )
}

//language=JavaScript
private fun getEnigmaUtils(): JsAny? {
    js(
        code = "return window.getEnigmaUtils"
    )
}

//language=JavaScript
private fun parse(json: String): JsAny = js("JSON.parse(json)")


suspend fun getClientWithKeplrWallet(
    chain: Chain, keplr: Keplr? = null, suggestChain: Boolean = true
): OfflineSignerOnlyAminoWalletWrapper {
    @Suppress("NAME_SHADOWING") val keplr = if (keplr == null) {
        while (getKeplr() == null || getOfflineSignerOnlyAmino() == null || getEnigmaUtils() == null) {
            delay(10)
        }
        getKeplr()!!
    } else {
        keplr
    }
    if (suggestChain) {
        val chainId = chain.id
        val chainName = "Local Testnet"  //Anything you want
        val lcdUrl = chain.grpcGatewayEndpoint
        val rpcUrl = chain.rpcEndpoint
        val denom = "SCRT"
        val minimalDenom = "uscrt"
        val suggestion: JsAny = parse(
            """{
            "chainId": "$chainId",
            "chainName": "$chainName",
            "rpc": "$rpcUrl",
            "rest": "$lcdUrl",
            "bip44": { "coinType": 529 },
            "alternativeBIP44s": [
                {
                    "coinType": 118
                }
            ],
            "coinType": 529,
            "stakeCurrency": { "coinDenom": "$denom",
                             "coinMinimalDenom": "$minimalDenom",
                             "coinDecimals": 6,
                             "coinGeckoId": "secret",
                             "coinImageUrl": "https://dhj8dql1kzq2v.cloudfront.net/white/secret.png"
                             },
            "bech32Config": {
                "bech32PrefixAccAddr": "secret",
                "bech32PrefixAccPub": "secretpub",
                "bech32PrefixValAddr": "secretvaloper",
                "bech32PrefixValPub": "secretvaloperpub",
                "bech32PrefixConsAddr": "secretvalcons",
                "bech32PrefixConsPub": "secretvalconspub"
            },
            "currencies": [
                { "coinDenom": "$denom",
                  "coinMinimalDenom": "$minimalDenom",
                  "coinDecimals": 6,
                  "coinGeckoId": "secret",
                  "coinImageUrl": "https://dhj8dql1kzq2v.cloudfront.net/white/secret.png"
                }
            ],
            "feeCurrencies": [
                { "coinDenom": "$denom",
                  "coinMinimalDenom": "$minimalDenom",
                  "coinDecimals": 6,
                  "coinGeckoId": "secret",
                  "coinImageUrl": "https://dhj8dql1kzq2v.cloudfront.net/white/secret.png",
                  "gasPriceStep": {
                        "low": 0.1,
                        "average": 0.25,
                        "high": 0.4
                   }
                }
            ],
            "chainSymbolImageUrl": "https://dhj8dql1kzq2v.cloudfront.net/white/secret.png",
            "features": ["secretwasm", "ibc-go", "ibc-transfer"]
        }"""
        )
        println(suggestion)
        val suggestChainPromise: Promise<JsAny> = keplr.experimentalSuggestChain(suggestion)
        suggestChainPromise.await<JsAny>()
    }
    val enablePromise: Promise<JsAny> = keplr.enable(chain.id)
    enablePromise.await<JsAny>()
    return OfflineSignerOnlyAminoWalletWrapper(keplr, chain.id)
}


//suspend fun getClientWithMetamaskWallet(chain: Chain): MetaMaskWalletWrapper {
//    val provider = window["ethereum"]
//    val web3 = Web3(provider).apply {
//        eth.handleRevert = true
//    }
//    val account = web3.eth.requestAccounts().await().firstOrNull()!!
//    return MetaMaskWalletWrapper(MetaMaskWallet.create(provider, account).await())
//}

//enum class WalletConnectModal(val signingMethods: Array<String>, val qrcodeModal: IQRCodeModal) {
//    Keplr(
//        signingMethods = arrayOf(
//            "keplr_enable_wallet_connect_v1",
//            "keplr_sign_amino_wallet_connect_v1",
//        ), qrcodeModal = KeplrQRCodeModalV1()
//    ),
//    Cosmostation(
//        signingMethods = arrayOf(
////            "cosmostation_enable_wallet_connect_v1",
////            "cosmostation_sign_amino_wallet_connect_v1",
//            "cosmostation_wc_accounts_v1",
//            "cosmostation_wc_sign_tx_v1",
//        ), qrcodeModal = CosmostationWCModal()
//    )
//}
//
//suspend fun setupCosmosWalletConnectAndGetWallet(
//    chain: Chain, wcModal: WalletConnectModal
//): OfflineSignerOnlyAminoWalletWrapper {
//    val connector = WalletConnect(
//        IWalletConnectOptionsInstance(
//            bridge = "https://bridge.walletconnect.org", // Required
//            signingMethods = wcModal.signingMethods,
//            qrcodeModal = wcModal.qrcodeModal,
//        )
//    )
//    val keplr = if (!connector.connected) {
//        connector.createSession().await()
//        suspendCoroutine<KeplrWalletConnectV1> { continuation ->
//            connector.on("connect") { error, payload ->
//                if (error != null) {
//                    continuation.resumeWithException(error)
//                } else {
//                    val keplr = KeplrWalletConnectV1(connector, KeplrWalletConnectV1OptionsInstance(null) { a, b, c ->
//                        console.log("SEND TX CALLED")
//                        Promise.resolve(Uint8Array(1))
//                    })
//                    continuation.resume(keplr)
//
//                }
//            }
//        }
//    } else {
//        KeplrWalletConnectV1(connector, KeplrWalletConnectV1OptionsInstance(null) { a, b, c ->
//            console.log("SEND TX CALLED")
//            Promise.resolve(Uint8Array(1))
//        })
//    }
//    // experimentalSuggestChain not implemented yet on WalletConnect
//    // https://github.com/chainapsis/keplr-wallet/blob/682c8402ccd09b35cecf9f028d97635b6a5cd015/packages/wc-client/src/index.ts#L275
//    return getClientWithKeplrWallet(chain, keplr, false)
//}

//suspend fun setupEthWalletConnectAndGetWallet(chain: Chain): MetaMaskWalletWrapper {
//    val provider = WalletConnectProvider(
//        IWalletConnectProviderOptionsInstance(
//            infuraId = "YOUR_ID",
//        )
//    )
//    try {
//        (provider as WalletConnectProvider).enable().await()
//    } catch (t: Throwable) {
//        println("WalletConnectProvider.enable() returned error ${t.message}")
//    }
//    val web3 = Web3(provider).apply {
//        eth.handleRevert = true
//    }
//    val account = web3.eth.getAccounts().await().firstOrNull()!!
//    return MetaMaskWalletWrapper(MetaMaskWallet.create(provider, account).await())
//}