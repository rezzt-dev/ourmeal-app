package ourmeal.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.FileProvider
import app.ourmeat.databinding.ItemMenuShareDayBinding
import app.ourmeat.databinding.ViewMenuShareCardBinding
import ourmeal.app.model.SharedDay
import java.io.File
import java.io.IOException

/** Folder declared in `res/xml/shared_files.xml`; only images made to be shared go there. */
private const val SHARED_DIR = "shared"
/** Width of the shared menu image, in dp, so it reads well on any phone it is sent to. */
private const val MENU_CARD_WIDTH_DP = 400

internal fun textShareIntent(subject: String, text: String): Intent =
  Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, subject)
    putExtra(Intent.EXTRA_TEXT, text)
  }

/** Lets only the app the user picks read [uri], and only for this share. */
internal fun imageShareIntent(uri: Uri, subject: String): Intent =
  Intent(Intent.ACTION_SEND).apply {
    type = "image/png"
    putExtra(Intent.EXTRA_STREAM, uri)
    putExtra(Intent.EXTRA_SUBJECT, subject)
    clipData = ClipData.newRawUri(subject, uri)
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

/** Opens the system share sheet; false when no app can receive [send]. */
internal fun Activity.startShareChooser(send: Intent, chooserTitle: String): Boolean = try {
  startActivity(Intent.createChooser(send, chooserTitle))
  true
} catch (_: ActivityNotFoundException) {
  false
}

/**
 * Writes [bitmap] as a PNG in the cache folder that the file provider exposes and returns its
 * content uri. Each name is overwritten on the next share, so old images do not pile up.
 * Blocking: call it off the main thread.
 */
@Throws(IOException::class)
internal fun Context.cacheSharedImage(bitmap: Bitmap, fileName: String): Uri {
  val dir = File(cacheDir, SHARED_DIR)
  if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Could not create the shared folder")
  val file = File(dir, fileName)
  file.outputStream().use { output ->
    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) throw IOException("Could not encode the image")
  }
  return FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
}

/**
 * Draws the week as a cream card with the app's type, off screen, ready to be shared as an
 * image. Must run on the main thread because it inflates views.
 */
internal fun Context.renderMenuCard(weekRange: String, days: List<SharedDay>): Bitmap {
  val inflater = LayoutInflater.from(this)
  val card = ViewMenuShareCardBinding.inflate(inflater)
  card.tvShareWeek.text = weekRange
  days.forEach { day ->
    val row = ItemMenuShareDayBinding.inflate(inflater, card.layoutShareDays, false)
    row.tvShareDay.text = day.title
    row.tvShareLunch.text = day.lunch
    row.tvShareDinner.text = day.dinner
    card.layoutShareDays.addView(row.root)
  }
  val width = (MENU_CARD_WIDTH_DP * resources.displayMetrics.density).toInt()
  card.root.measure(
    View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
  )
  card.root.layout(0, 0, card.root.measuredWidth, card.root.measuredHeight)
  return Bitmap.createBitmap(card.root.measuredWidth, card.root.measuredHeight, Bitmap.Config.ARGB_8888)
    .also { card.root.draw(Canvas(it)) }
}
