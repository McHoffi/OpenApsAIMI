package app.aaps.core.ui.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuProvider
import app.aaps.core.ui.compose.MetroAppCompatActivity

open class TranslatedDaggerAppCompatActivity : MetroAppCompatActivity() {

    private var menuProvider: MenuProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Add menu items without overriding methods in the Activity
        menuProvider = object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {}

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean =
                when (menuItem.itemId) {
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }

                    else              -> false
                }
        }
        menuProvider?.let { addMenuProvider(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        menuProvider?.let { removeMenuProvider(it) }
    }
}
