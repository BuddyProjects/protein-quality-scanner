package com.proteinscannerandroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

data class ProductInfo(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val ingredientsText: String?,
    val proteinPer100g: Double?
)

/**
 * Result of an API fetch attempt.
 */
sealed class FetchResult {
    data class Success(val product: ProductInfo) : FetchResult()
    data class ProductNotFound(val barcode: String) : FetchResult()
    data class ApiUnavailable(val reason: String) : FetchResult()
    data class NetworkError(val reason: String) : FetchResult()
}

object OpenFoodFactsService {

    /**
     * Check if the OpenFoodFacts API is reachable.
     */
    suspend fun checkApiStatus(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/737628064502.json") // Known product
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            val responseCode = connection.responseCode
            connection.disconnect()
            responseCode == HttpURLConnection.HTTP_OK
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Fetch product data with detailed error information.
     */
    suspend fun fetchProductWithStatus(barcode: String): FetchResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode

            when {
                responseCode == HttpURLConnection.HTTP_OK -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonResponse = JSONObject(response)

                    if (jsonResponse.getInt("status") == 1) {
                        val product = jsonResponse.getJSONObject("product")

                        val productName = product.optString("product_name", null).takeIf { it.isNotBlank() }
                        val brand = product.optString("brands", null).takeIf { it.isNotBlank() }
                        val ingredients = product.optString("ingredients_text", null).takeIf { it.isNotBlank() }

                        val proteinPer100g = try {
                            val nutriments = product.optJSONObject("nutriments")
                            nutriments?.optDouble("proteins_100g")?.takeIf { !it.isNaN() }
                        } catch (e: Exception) {
                            null
                        }

                        FetchResult.Success(
                            ProductInfo(
                                barcode = barcode,
                                name = productName,
                                brand = brand,
                                ingredientsText = ingredients,
                                proteinPer100g = proteinPer100g
                            )
                        )
                    } else {
                        FetchResult.ProductNotFound(barcode)
                    }
                }
                responseCode >= 500 -> {
                    FetchResult.ApiUnavailable("OpenFoodFacts server error (HTTP $responseCode)")
                }
                responseCode == 429 -> {
                    FetchResult.ApiUnavailable("Rate limited - please try again later")
                }
                else -> {
                    FetchResult.ApiUnavailable("Unexpected response (HTTP $responseCode)")
                }
            }
        } catch (e: UnknownHostException) {
            FetchResult.NetworkError("No internet connection")
        } catch (e: SocketTimeoutException) {
            FetchResult.NetworkError("Connection timed out - server may be slow or unavailable")
        } catch (e: Exception) {
            e.printStackTrace()
            FetchResult.NetworkError("Network error: ${e.message ?: "Unknown error"}")
        }
    }
    
    /**
     * Legacy method for backward compatibility. Returns null on any error.
     */
    suspend fun fetchProductData(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        when (val result = fetchProductWithStatus(barcode)) {
            is FetchResult.Success -> result.product
            else -> null
        }
    }
}