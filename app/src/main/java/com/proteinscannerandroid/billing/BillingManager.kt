package com.proteinscannerandroid.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import com.proteinscannerandroid.premium.PremiumManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PurchaseState {
    object Idle : PurchaseState()
    object Loading : PurchaseState()
    object Success : PurchaseState()
    data class Error(val message: String) : PurchaseState()
}

class BillingManager(private val context: Context) : PurchasesUpdatedListener {

    companion object {
        const val PRODUCT_ID = "premium_upgrade"
    }

    private val _purchaseState = MutableStateFlow<PurchaseState>(PurchaseState.Idle)
    val purchaseState: StateFlow<PurchaseState> = _purchaseState.asStateFlow()

    private val _productDetails = MutableStateFlow<ProductDetails?>(null)
    val productDetails: StateFlow<ProductDetails?> = _productDetails.asStateFlow()

    private var billingClient: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases()
        .build()

    init {
        connectToPlayBilling()
    }

    private fun connectToPlayBilling() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    queryExistingPurchases()
                    queryProductDetails()
                }
            }

            override fun onBillingServiceDisconnected() {
                // Retry connection
                connectToPlayBilling()
            }
        })
    }

    fun queryExistingPurchases() {
        CoroutineScope(Dispatchers.IO).launch {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

            billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    for (purchase in purchases) {
                        if (purchase.products.contains(PRODUCT_ID) &&
                            purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                            // User has premium
                            PremiumManager.setPremium(true)
                            acknowledgePurchase(purchase)
                        }
                    }
                }
            }
        }
    }

    private fun queryProductDetails() {
        val productList = listOf(
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        )

        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
                productDetailsList.isNotEmpty()) {
                _productDetails.value = productDetailsList.first()
            }
        }
    }

    fun launchPurchaseFlow(activity: Activity) {
        val details = _productDetails.value
        if (details == null) {
            _purchaseState.value = PurchaseState.Error("Product not available")
            return
        }

        _purchaseState.value = PurchaseState.Loading

        val productDetailsParamsList = listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        )

        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(productDetailsParamsList)
            .build()

        billingClient.launchBillingFlow(activity, billingFlowParams)
    }

    override fun onPurchasesUpdated(billingResult: BillingResult, purchases: List<Purchase>?) {
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                purchases?.forEach { purchase ->
                    if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
                        acknowledgePurchase(purchase)
                        PremiumManager.setPremium(true)
                        _purchaseState.value = PurchaseState.Success
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _purchaseState.value = PurchaseState.Idle
            }
            else -> {
                _purchaseState.value = PurchaseState.Error("Purchase failed: ${billingResult.debugMessage}")
            }
        }
    }

    private fun acknowledgePurchase(purchase: Purchase) {
        if (!purchase.isAcknowledged) {
            val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

            billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                    // Log error but don't block - purchase is still valid
                }
            }
        }
    }

    fun resetState() {
        _purchaseState.value = PurchaseState.Idle
    }

    fun destroy() {
        billingClient.endConnection()
    }
}
