package com.dani.jarzy

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dani.jarzy.data.SavingsCategory

/**
 * Backs the swipeable category cards on the "Categories" tab of both ChildDetailActivity
 * (the parent's per-child view) and ChildHomeActivity (the child's own home screen)
 * this is shown inside a ViewPager2, which uses a RecyclerView.Adapter under the hood like a
 * plain RecyclerView would.
 * One coloured "cube" per savings category, cycling through
 * a small fixed palette from colors.xml so categories are easy to tell apart while swiping
 *
 */
class CategoryCubeAdapter(
    private var categories: List<SavingsCategory>
) : RecyclerView.Adapter<CategoryCubeAdapter.CubeViewHolder>() {

    private val cubeColorResIds = intArrayOf(
        R.color.cube_green,
        R.color.cube_orange,
        R.color.cube_purple,
        R.color.cube_teal,
        R.color.cube_pink,
        R.color.cube_amber
    )

    class CubeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cube: View = view.findViewById(R.id.flCube)
        val tvName: TextView = view.findViewById(R.id.tvCubeName)
        val tvAmount: TextView = view.findViewById(R.id.tvCubeAmount)
    }

    // Swaps in a freshly-loaded category list and redraws every visible card
    // called by both activities' refresh functions whenever the underlying data changes
    // the same "reload then redisplay" pattern used everywhere else in this app.
    fun updateCategories(newCategories: List<SavingsCategory>) {
        categories = newCategories
        notifyDataSetChanged()
    }

    // Lets either activity turn "whichever cube the user last swiped to" back into the actual
    // category it represents for the Edit/Delete Category buttons below the pager.
    fun categoryAt(position: Int): SavingsCategory? = categories.getOrNull(position)

    override fun getItemCount(): Int = categories.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CubeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_cube, parent, false)
        return CubeViewHolder(view)
    }

    override fun onBindViewHolder(holder: CubeViewHolder, position: Int) {
        val category = categories[position]

        // mutate() first so this cube gets its own copy of the drawable instead of
        // sharing (and overwriting) the same drawable object every recycled view in the
        // pager would otherwise point at (Android Developers, n.d.).
        val background = holder.cube.background.mutate() as GradientDrawable
        val colorResId = cubeColorResIds[position % cubeColorResIds.size]
        background.setColor(ContextCompat.getColor(holder.itemView.context, colorResId))

        holder.tvName.text = category.name
        holder.tvAmount.text = "%.2f".format(category.amountSaved)
    }
}

// References:
// Android Developers, n.d. GradientDrawable [Webpage]. Available at:
//     https://developer.android.com/reference/android/graphics/drawable/GradientDrawable
//     [Accessed 16 September 2026].
