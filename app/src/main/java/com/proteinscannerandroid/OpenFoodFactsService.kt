package com.proteinscannerandroid

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ProductInfo(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val ingredientsText: String?,
    val proteinPer100g: Double?
)

object OpenFoodFactsService {
    
    suspend fun fetchProductData(barcode: String): ProductInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://world.openfoodfacts.org/api/v0/product/$barcode.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)
                
                if (jsonResponse.getInt("status") == 1) {
                    val product = jsonResponse.getJSONObject("product")
                    
                    val productName = product.optString("product_name", null).takeIf { it.isNotBlank() }
                    val brand = product.optString("brands", null).takeIf { it.isNotBlank() }
                    val ingredients = product.optString("ingredients_text", null).takeIf { it.isNotBlank() }
                    
                    // Extract protein content
                    val proteinPer100g = try {
                        val nutriments = product.optJSONObject("nutriments")
                        nutriments?.optDouble("proteins_100g")?.takeIf { !it.isNaN() }
                    } catch (e: Exception) {
                        null
                    }
                    
                    return@withContext ProductInfo(
                        barcode = barcode,
                        name = productName,
                        brand = brand,
                        ingredientsText = ingredients,
                        proteinPer100g = proteinPer100g
                    )
                }
            }
            
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}