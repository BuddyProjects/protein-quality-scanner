package com.proteinscannerandroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.json.JSONException

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
    data class ProductNotFound(val barcode: String, val reason: String = "API returned status=0") : FetchResult()
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

                    // Use optInt with default 0 to safely handle missing/invalid status
                    if (jsonResponse.optInt("status", 0) == 1) {
                        val product = jsonResponse.optJSONObject("product")
                        
                        // If product object is missing despite status=1, treat as not found
                        if (product == null) {
                            return@withContext FetchResult.ProductNotFound(barcode)
                        }

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
                        android.util.Log.d("OpenFoodFacts", "Product not found: status=${jsonResponse.optInt("status", -1)} for $barcode")
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
            FetchResult.NetworkError("No internet connection - check if you're online")
        } catch (e: SocketTimeoutException) {
            FetchResult.NetworkError("Connection timed out - server may be slow or unavailable")
        } catch (e: ConnectException) {
            FetchResult.NetworkError("Cannot connect - check your internet connection")
        } catch (e: SocketException) {
            FetchResult.NetworkError("Connection failed - network may be unavailable")
        } catch (e: SSLException) {
            FetchResult.NetworkError("Secure connection failed - check your network")
        } catch (e: JSONException) {
            // API returned data but in unexpected format - treat as product not found
            e.printStackTrace()
            android.util.Log.e("OpenFoodFacts", "JSONException for $barcode - treating as not found: ${e.message}")
            FetchResult.ProductNotFound(barcode, "JSONException: ${e.message}")
        } catch (e: IOException) {
            // Log for debugging
            e.printStackTrace()
            android.util.Log.e("OpenFoodFacts", "IOException: ${e.javaClass.simpleName}, message: ${e.message}")
            
            // Check if this is a real network error or just a data/parsing issue
            val message = e.message?.lowercase() ?: ""
            val isNetworkError = message.contains("network") || 
                                 message.contains("connect") || 
                                 message.contains("unreachable") || 
                                 message.contains("refused") ||
                                 message.contains("reset") ||
                                 message.contains("closed") ||
                                 message.contains("timeout") ||
                                 message.contains("host") ||
                                 message.contains("route")
            
            if (isNetworkError) {
                FetchResult.NetworkError("Network unavailable - check your connection")
            } else {
                // Non-network IOExceptions (e.g., parsing issues) → treat as not found
                // This prevents false "offline" messages
                android.util.Log.e("OpenFoodFacts", "IOException (non-network) for $barcode - treating as not found: ${e.message}")
                FetchResult.ProductNotFound(barcode, "IOException: ${e.message}")
            }
        } catch (e: Exception) {
            // Log the actual exception type for debugging
            e.printStackTrace()
            android.util.Log.e("OpenFoodFacts", "Unexpected exception type: ${e.javaClass.simpleName}, message: ${e.message}")
            
            // Only treat actual network-related exceptions as NetworkError
            // Everything else is likely a parsing/data issue, not a connectivity problem
            when (e) {
                is java.net.ProtocolException,
                is java.io.InterruptedIOException -> {
                    FetchResult.NetworkError("Connection interrupted: ${e.message ?: "Unknown error"}")
                }
                else -> {
                    // Treat non-network exceptions as "product not found" to avoid
                    // misleading users about their connection status
                    android.util.Log.e("OpenFoodFacts", "Generic exception for $barcode - treating as not found: ${e.javaClass.simpleName}: ${e.message}")
                    FetchResult.ProductNotFound(barcode, "${e.javaClass.simpleName}: ${e.message}")
                }
            }
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