package com.specknet.pdiotapp

import android.content.Context
import android.content.Intent
import android.content.res.Resources.NotFoundException
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.specknet.pdiotapp.databinding.ActivityLoginpageBinding
import com.specknet.pdiotapp.live.LiveDataActivity
import kotlinx.android.synthetic.main.activity_loginpage.view.*
import org.apache.commons.lang3.ObjectUtils.Null
import java.io.BufferedReader
import java.math.BigInteger
import java.security.MessageDigest

class Loginpage : AppCompatActivity() {

    private lateinit var binding: ActivityLoginpageBinding

    private fun map2Str2File(m:MutableMap<String,String>,filename :String){
        //turn the map into desired string format and write to file
        var str=""
        for (t in m.keys) {
            str = str + t + "," + m[t] + "."
        }
        this.openFileOutput(filename, Context.MODE_PRIVATE).use{
            it.write(str.toByteArray())
        }
        Log.d("wu",str)
    }

    private fun md5(input:String): String {
        // md5 convert
        val md = MessageDigest.getInstance("MD5")
        return BigInteger(1, md.digest(input.toByteArray())).toString(16).padStart(32, '0')
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginpageBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val filename="storePassword"
        //generate a new file if not exists
        this.openFileOutput(filename, Context.MODE_APPEND).use{
            it.write("".toByteArray())
        }
        val allText = this.openFileInput(filename).bufferedReader().use(BufferedReader::readText)
        var usernamePassword = mutableMapOf<String,String>()
        //parse the file, write to map
        if (allText.isNotEmpty()){
            val eachUsernamePassword = allText.slice(0..allText.length-2).split(".")
            for (t in eachUsernamePassword){
                val stored = t.split(",")
                usernamePassword[stored[0]] = stored[1]
            }
        }
        binding.button.setOnClickListener {
            val username=binding.editTextTextPersonName.text.toString()
            val pw = binding.editTextNumberPassword.text.toString()
            if (usernamePassword[username]!=null && usernamePassword[username]==md5(pw)){
                //go to main activity
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("userName",username)
                startActivity(intent)
            }
            else{
                Toast.makeText(this, "Check username or password.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.signup.setOnClickListener {
            val username=binding.editTextTextPersonName.text.toString()
            val pw = binding.editTextNumberPassword.text.toString()
            // check format
            if (usernamePassword[username]!=null){
                Toast.makeText(this, "This username is already used.", Toast.LENGTH_SHORT).show()
            }
            else if (!(username.map{it -> Character.isLetterOrDigit(it)}).all { it }){
                Toast.makeText(this, "Only letter or digits.", Toast.LENGTH_SHORT).show()
            }
            else if (username.length<2 || username.length>8 ){
                Toast.makeText(this, "Choose a username between 2 and 8 chars.", Toast.LENGTH_SHORT).show()
            }
            else if ( pw.length <4 || pw.length>12){
                Toast.makeText(this, "Choose a password between 4 and 12 digits.", Toast.LENGTH_SHORT).show()
            }
            else{
                usernamePassword[username]=md5(pw) // convert to md5
                Toast.makeText(this, "Sign up successful.", Toast.LENGTH_SHORT).show()
                map2Str2File(usernamePassword,filename)
                // go to main activity
                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("userName",username)
                intent.putExtra("firstTime","true")
                startActivity(intent)
            }
        }
        binding.guestLogin.setOnClickListener {
            // go to main activity
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("userName","guest")
            startActivity(intent)
        }
//        binding.button2.setOnClickListener {
//            map2Str2File(mutableMapOf<String,String>(),filename)
//            usernamePassword=mutableMapOf<String,String>()
//        }
    }

}