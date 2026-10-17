package ourmeal.app

import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Every dialog shares the brand dialog theme configured in `themes.xml`. */
internal fun AppCompatActivity.ourmealDialog(): MaterialAlertDialogBuilder =
  MaterialAlertDialogBuilder(this)
