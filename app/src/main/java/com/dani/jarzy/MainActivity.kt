package com.dani.jarzy

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.dani.jarzy.data.JarzyDatabase
import kotlinx.coroutines.launch

/**
 * MainActivity is the app's entry point / login screen.
 * NOTE: this screen currently checks the entered username/password against
 * both the parent table and the child table (parent first, then child) to
 * decide where to send the user.
 */

class MainActivity : AppCompatActivity() {

    // Reference to the Room database singleton so this Activity can run login queries.
    // Declared here with lateinit because it depends on "this" context,
    // which is only safely available once onCreate() runs.
    private lateinit var database: JarzyDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Get / create on first-ever call the single shared database instance for the whole app
        // doesnt just open a new connection every time this screen loads.
        database = JarzyDatabase.getDatabase(this)

        // Grab references to every view on the login screen by their XML IDs so we
        // can read from the text fields and attach click the buttons.
        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvError = findViewById<TextView>(R.id.tvError)
        val btnGoToRegister = findViewById<Button>(R.id.btnGoToRegister)

        // What happens after "Login".
        // .trim() removes accidental leading/trailing spaces from the username;
        // the password is left exactly as typed since spaces could be intentional there.
        btnLogin.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString()

            // Basic empty-field validation before we even touch the database.
            // return@setOnClickListener exits just this click handler.
            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "Please enter both a username and password."
                return@setOnClickListener
            }


            // Room database calls must be "suspend" functions and cannot run on the main (UI) thread.
            // lifecycleScope.launch starts a coroutine that is automatically tied to this Activity's lifecycle
            // if the Activity is destroyed while the login check is still running:
            // the coroutine cancelled automatically instead of trying to update views that no longer exist (Zoha, 2019).
            lifecycleScope.launch {
                //parents first
                val parent = database.parentDao().login(username, password)
                if (parent != null) {
                    tvError.text = ""
                    // An explicit Intent names the exact Activity to open next. putExtra()
                    // attaches small pieces of data to that Intent so the next screen knows who is logged in
                    // without having to query the database again (Vogel, 2016).
                    val intent = Intent(this@MainActivity, ParentHomeActivity::class.java)
                    intent.putExtra("PARENT_ID", parent.parentId)
                    intent.putExtra("PARENT_USERNAME", parent.username)
                    startActivity(intent)
                    // finish() closes MainActivity so the user can't press "back" and land
                    // back on the login screen while already logged in.
                    finish()
                    return@launch
                }

                //child next if no matched parent
                val child = database.childDao().login(username, password)
                if (child != null) {
                    tvError.text = ""
                    Toast.makeText(this@MainActivity, "Logged in as child: ${child.username}", Toast.LENGTH_SHORT).show()
                    val childIntent = Intent(this@MainActivity, ChildHomeActivity::class.java)
                    childIntent.putExtra("CHILD_ID", child.childId)
                    childIntent.putExtra("CHILD_USERNAME", child.username)
                    childIntent.putExtra("PARENT_ID", child.parentId)
                    startActivity(childIntent)
                    finish()
                    return@launch
                }

                // Neither table matched -> show a general error rather which is safer practice.
                tvError.text = "Incorrect username or password."
            }
        }

        // Register button opens the parent sign-up screen
        // brand-new user always starts by creating a parent account
        // then adds children from inside that account.
        btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterParentActivity::class.java))
        }

    }
}

// References:
// Vogel, L., 2016. Android Intents - Tutorial (Version 0.3) [Webpage]. Available at:
//     https://www.vogella.com/tutorials/AndroidIntent/article.html [Accessed 12 September 2026].
// Zoha, A.H., 2019. Coroutine in Android: Working with Lifecycle [Webpage]. Available at:
//     https://medium.com/android-news/coroutine-in-android-working-with-lifecycle-fc9c1a31e5f3
//     [Accessed 12 September 2026].
// GeeksforGeeks, 2019. What is Toast and How to Use it in Android with Examples? [Webpage].
//     Available at: https://www.geeksforgeeks.org/android/what-is-toast-and-how-to-use-it-in-android-with-examples/
//     [Accessed 12 September 2026].