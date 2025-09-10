package ourmeal.app.model

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class RecyclerItemClickListener(
  context: Context,
  recyclerView: RecyclerView,
  private val listener: OnItemClickListener
) : RecyclerView.OnItemTouchListener {

  interface OnItemClickListener {
    fun onItemClick(view: View, position: Int)
    fun onItemLongClick(view: View, position: Int)
  }

  private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
    override fun onSingleTapUp(e: MotionEvent): Boolean = true
    override fun onLongPress(e: MotionEvent) {
      val child = recyclerView.findChildViewUnder(e.x, e.y)
      if (child != null) {
        listener.onItemLongClick(child, recyclerView.getChildAdapterPosition(child))
      }
    }
  })

  override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
    val child = rv.findChildViewUnder(e.x, e.y)
    if (child != null && gestureDetector.onTouchEvent(e)) {
      listener.onItemClick(child, rv.getChildAdapterPosition(child))
      return true
    }
    return false
  }

  override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
  override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
}