package com.ajiashi.aicontentdetection

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.database
import okhttp3.*
import okhttp3.Headers.Companion.toHeaders
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import java.util.Locale


class MainActivity : AppCompatActivity() {
    lateinit var btn_check : Button
    lateinit var text_result : TextView
    lateinit var text_use : TextView
    lateinit var edit_txt : TextInputEditText
    lateinit var txt_msg_check : TextView
    val client = OkHttpClient()
    var progressBar: ProgressBar? = null
    var i = 0
    var handler: Handler = Handler()
    var apiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX2lkIjoiMmVmMTk5OTQtMWMxMy00NjI2LWE2ODQtYjU4ZTMyZTc4NzAyIiwidHlwZSI6ImFwaV90b2tlbiJ9.kjoyC9dGVlJ778ioLBxEfyaXVfsFoHMWPz8aDPM5Lw8"
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تحديد اللغة
        val locale = Locale("en")
        Locale.setDefault(locale)

        // تكوين اتجاه العرض
        val configuration = Configuration()
        configuration.locale = locale
        configuration.setLayoutDirection(locale)

        // تطبيق التكوين
        baseContext.resources.updateConfiguration(configuration, baseContext.resources.displayMetrics)


        setContentView(R.layout.activity_main)


        btn_check = findViewById(R.id.btn_check)
        edit_txt = findViewById(R.id.edit_text)
        text_result = findViewById(R.id.txt_result)
        text_use = findViewById(R.id.txt_use)
        progressBar = findViewById(R.id.progressBar)
        txt_msg_check = findViewById(R.id.txt_msg_check)

        runOnUiThread {

            text_use.text = "The number of attempts : "+getRemainingAttempts(this).toString()

            btn_check.setOnClickListener {
                useFeature(this)
            }

            database = Firebase.database.reference

//            database.child("Apps").child("Ai_content_detection").child("key").setValue("$apiKey")

            database.child("Apps").child("Ai_content_detection").child("key").get().addOnSuccessListener {
                //Log.i("firebase", "Got value ${it.value}")
                apiKey = it.value.toString()
            }.addOnFailureListener{
                //Log.e("firebase", "Error getting data", it)
            }
        }
    }

    fun useFeature(context: Context) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)

        // احصل على تاريخ آخر محاولة
        val lastAttemptDate = sharedPreferences.getLong("lastAttemptDate", 0)

        val calendar = Calendar.getInstance()
        val currentDate = calendar.timeInMillis

        if (isSameDay(lastAttemptDate, currentDate)) {
            // لا يزال في نفس اليوم، افحص عدد المحاولات المتبقية
            val remainingAttempts = getRemainingAttempts(context)

            if (remainingAttempts > 0) {
                // قم بتنفيذ الميزة هنا
                if (edit_txt.text!!.trim().isEmpty() || edit_txt.text!!.trim().length <= 10){
                    Toast.makeText(this,"No find",Toast.LENGTH_SHORT).show()
                }else{
                    progressBar!!.visibility = View.VISIBLE
                    txt_msg_check!!.visibility = View.VISIBLE
                    btn_check.isEnabled = false
                    val text = edit_txt.text.toString()
                    checkAiContent(text)
                }
            } else {
                Toast.makeText(this,"The user has exhausted all allowed attempts",Toast.LENGTH_SHORT).show()
            }
        } else {
            // يوم جديد، قم بإعادة تعيين عدد المحاولات
            resetAttempts(context)
        }

        // حفظ تاريخ آخر محاولة
        sharedPreferences.edit().putLong("lastAttemptDate", currentDate).apply()
    }

    // قم بإعادة تعيين عدد المحاولات
    private fun resetAttempts(context: Context) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("attempts", 5).apply()
    }

    // تحقق مما إذا كانت الأوقات تنتمي إلى نفس اليوم
    private fun isSameDay(time1: Long, time2: Long): Boolean {
        val calendar1 = Calendar.getInstance()
        val calendar2 = Calendar.getInstance()
        calendar1.timeInMillis = time1
        calendar2.timeInMillis = time2

        return calendar1.get(Calendar.YEAR) == calendar2.get(Calendar.YEAR) &&
                calendar1.get(Calendar.DAY_OF_YEAR) == calendar2.get(Calendar.DAY_OF_YEAR)
    }

    // احصل على عدد المحاولات المتبقية
    private fun getRemainingAttempts(context: Context): Int {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("attempts", 5)
    }

    // قلل عدد المحاولات بواحد
    private fun decrementAttempts(context: Context) {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        val remainingAttempts = getRemainingAttempts(context) - 1

        // قم بتحديث عدد المحاولات المتبقية في SharedPreferences
        sharedPreferences.edit().putInt("attempts", remainingAttempts).apply()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.home_main, menu)
        return true
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId

        if (id == R.id.me_like){
            try {
                var playstoreuri1: Uri = Uri.parse("market://details?id=" + packageName)
                //or you can add
                //var playstoreuri:Uri=Uri.parse("market://details?id=manigautam.app.myplaystoreratingapp")
                var playstoreIntent1: Intent = Intent(Intent.ACTION_VIEW, playstoreuri1)
                startActivity(playstoreIntent1)
                //it genrate exception when devices do not have playstore
            }catch (exp:Exception){
                var playstoreuri2: Uri = Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)
                //var playstoreuri:Uri=Uri.parse("https://play.google.com/store/apps/details?id=manigautam.app.myplaystoreratingapp")
                var playstoreIntent2: Intent = Intent(Intent.ACTION_VIEW, playstoreuri2)
                startActivity(playstoreIntent2)
            }
        }

        if (id == R.id.me_call_me){
            val emailIntent = Intent(Intent.ACTION_SENDTO)
            emailIntent.data =
                Uri.parse("mailto:" + "appsmt703@gmail.com") // You can use "mailto:" if you don't know the address beforehand.

            emailIntent.putExtra(Intent.EXTRA_SUBJECT, ""+baseContext.getString(R.string.app_name))
            emailIntent.putExtra(Intent.EXTRA_TEXT, "")

            try {
                startActivity(Intent.createChooser(emailIntent, "Send email using..."))
            } catch (ex: ActivityNotFoundException) {
                Toast.makeText(this@MainActivity, "No email clients installed.", Toast.LENGTH_SHORT)
                    .show()
            }
        }

        if (id == R.id.me_close){
            finish()
        }

        if (id == R.id.me_about) {
//            val intent = Intent(this@MainActivity, AboutActivity::class.java)
//            startActivity(intent)

            val url = "https://sites.google.com/view/artificial-intelligence-conten/%D8%A7%D9%84%D8%B5%D9%81%D8%AD%D8%A9-%D8%A7%D9%84%D8%B1%D8%A6%D9%8A%D8%B3%D9%8A%D8%A9" // Replace with the URL you want to open

            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.setPackage("com.android.chrome") // Replace with the package name of the desired app
            startActivity(intent)

        }
        if (id == R.id.share) {
            val txt = "https://play.google.com/store/apps/details?id="+packageName
            val sendIntent = Intent()
            sendIntent.action = Intent.ACTION_SEND
            sendIntent.putExtra(Intent.EXTRA_TEXT, txt)
            sendIntent.type = "text/plain"
            val shareIntent = Intent.createChooser(sendIntent, null)
            startActivity(shareIntent)
        }
        return super.onOptionsItemSelected(item)
    }


    private fun checkAiContent(text:String){

        try {
            val url = "https://api.edenai.run/v2/text/ai_detection"

            val headers = mapOf(
                "Authorization" to "Bearer $apiKey",
                "Content-Type" to "application/json"
            )

            val payload = mapOf(
                "providers" to "originalityai",
                "text" to "$text",
                "fallback_providers" to ""
            )

            val jsonMediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = JSONObject(payload).toString().toRequestBody(jsonMediaType)

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .headers(headers.toHeaders())
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Handle failure (e.g., network issues, server errors)
                    Log.e("OkHttp", "Failed to make GET request", e)
                }

                override fun onResponse(call: Call, response: Response) {
                    // Handle the response
                    if (response.isSuccessful) {
                        try {
                            val responseBody = response.body?.string()
                            // Parse the JSON response
                            val jsonResponse = JSONObject(responseBody)

                            // Extract information from the JSON response
                            val originalityai = jsonResponse.getJSONObject("originalityai")
                            val itemsArray = originalityai.getJSONArray("items")

                            for (i in 0 until itemsArray.length()) {
                                val item = itemsArray.getJSONObject(i)
                                val text = item.getString("text")
                                val prediction = item.getString("prediction")
                                val aiScore = item.getDouble("ai_score")

                                runOnUiThread {
                                    // قلل عدد المحاولات بواحد
                                    decrementAttempts(baseContext)
                                    text_use.text = "The number of attempts : "+getRemainingAttempts(baseContext).toString()
                                    val resultText = "Prediction: $prediction\nAI Score: $aiScore"
                                    text_result.text = resultText
                                    btn_check.isEnabled = true
                                    progressBar!!.visibility = View.INVISIBLE
                                    txt_msg_check!!.visibility = View.INVISIBLE
                                }

                                // Now you can use these values as needed
                                Log.d("OkHttp", "Text: $text, Prediction: $prediction, AI Score: $aiScore")
                            }

                        } catch (e: JSONException) {
                            Log.e("OkHttp", "Error parsing JSON: ${e.message}")
                            btn_check.isEnabled = true
                            progressBar!!.visibility = View.INVISIBLE
                            txt_msg_check!!.visibility = View.INVISIBLE
                        }
                    } else {
                        // Handle unsuccessful response (e.g., non-2xx status codes)
                        Log.e("OkHttp", "Unsuccessful response: ${response.code}")
                        btn_check.isEnabled = true
                        progressBar!!.visibility = View.INVISIBLE
                        txt_msg_check!!.visibility = View.INVISIBLE
                    }
                }
            })

        } catch (e: IOException) {
            Log.e("okhttp","An error occurred while making the request: ${e.message}")
            btn_check.isEnabled = true
            progressBar!!.visibility = View.INVISIBLE
            txt_msg_check!!.visibility = View.INVISIBLE
        }

    }

    override fun onBackPressed() {
        super.onBackPressed()
        finishAffinity()
    }

}