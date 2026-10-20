package com.monchamp.verusfarm

// Cryptomonnaies couramment utilisées pour les retraits.
// L'utilisateur fournit lui-même son adresse de réception.
data class Crypto(val ticker: String, val name: String, val network: String)

object Cryptos {
    val list: List<Crypto> = listOf(
        Crypto("VRSC", "Verus Coin", "Verus"),
        Crypto("BTC", "Bitcoin", "Bitcoin"),
        Crypto("ETH", "Ethereum", "ERC-20"),
        Crypto("USDT", "Tether", "ERC-20 / TRC-20 / BEP-20"),
        Crypto("USDC", "USD Coin", "ERC-20 / BEP-20"),
        Crypto("BNB", "BNB", "BEP-20"),
        Crypto("TRX", "TRON", "TRC-20"),
        Crypto("SOL", "Solana", "Solana"),
        Crypto("XRP", "XRP", "XRP Ledger"),
        Crypto("LTC", "Litecoin", "Litecoin"),
        Crypto("DOGE", "Dogecoin", "Dogecoin"),
        Crypto("BCH", "Bitcoin Cash", "Bitcoin Cash"),
        Crypto("ADA", "Cardano", "Cardano"),
        Crypto("MATIC", "Polygon", "Polygon"),
        Crypto("DOT", "Polkadot", "Polkadot"),
        Crypto("AVAX", "Avalanche", "C-Chain"),
        Crypto("TON", "Toncoin", "TON"),
        Crypto("DAI", "Dai", "ERC-20"),
        Crypto("SHIB", "Shiba Inu", "ERC-20"),
        Crypto("XLM", "Stellar", "Stellar"),
        Crypto("XMR", "Monero", "Monero"),
        Crypto("ATOM", "Cosmos", "Cosmos"),
        Crypto("ETC", "Ethereum Classic", "Ethereum Classic"),
        Crypto("NEAR", "NEAR Protocol", "NEAR"),
        Crypto("FTM", "Fantom", "Fantom"),
        Crypto("ALGO", "Algorand", "Algorand"),
        Crypto("KAS", "Kaspa", "Kaspa"),
        Crypto("BUSD", "Binance USD", "BEP-20")
    )

    fun of(ticker: String): Crypto? = list.firstOrNull { it.ticker == ticker }
}
