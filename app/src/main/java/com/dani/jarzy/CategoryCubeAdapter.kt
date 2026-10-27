package com.dani.jarzy

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.dani.jarzy.data.SavingsCategory

/**
 * Backs the swipeable category cards on the "Categories" tab of both ChildDetailActivity
 * (the parent's per-child view) and ChildHomeActivity (the child's own home screen)
 * this is shown inside a ViewPager2, which uses a RecyclerView.Adapter under the hood like a
 * plain RecyclerView would (Android Developers, n.d.-a).
 * One coloured "jar" graphic per savings category, cycling through a small fixed set of
 * vector drawables (and a matching text colour from colors.xml) so categories are easy
 * to tell apart while swiping. Previously this drew a single plain coloured rounded
 * square ("cube") via GradientDrawable; that has been replaced with a real jar
 * illustration (ic_jar_green/orange/purple/teal/pink/amber) so the category cards match
 * the rest of the app's jar-themed visual language, while keeping the exact same
 * six-colour cycling behaviour the cubes used.
 */
class CategoryCubeAdapter(
    private var categories: List<SavingsCategory>
) : RecyclerView.Adapter<CategoryCubeAdapter.CubeViewHolder>() {

    // Each entry here lines up by index with the matching entry in cubeColorResIds below -
    // jarDrawableResIds[i] is always the jar drawn in that category's own colour.
    private val jarDrawableResIds = intArrayOf(
        R.drawable.ic_jar_green,
        R.drawable.ic_jar_orange,
        R.drawable.ic_jar_purple,
        R.drawable.ic_jar_teal,
        R.drawable.ic_jar_pink,
        R.drawable.ic_jar_amber
    )

    private val cubeColorResIds = intArrayOf(
        R.color.cube_green,
        R.color.cube_orange,
        R.color.cube_purple,
        R.color.cube_teal,
        R.color.cube_pink,
        R.color.cube_amber
    )

    class CubeViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val jarImage: ImageView = view.findViewById(R.id.ivCubeJar)
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
        val paletteIndex = position % cubeColorResIds.size

        // Swap in this category's jar graphic instead of recolouring a shared drawable -
        // each jar drawable already bakes in its own stroke/lid colour, so there's no
        // GradientDrawable.mutate()-and-recolour step needed any more (Android Developers, n.d.-b).
        holder.jarImage.setImageResource(jarDrawableResIds[paletteIndex])

        // The jar's body is a light, near-white fill, so the name/amount text is coloured
        // to match the jar's own colour (instead of the old static white, which was only
        // readable against a solid-colour cube background) so it stays legible on top of
        // the jar graphic (Android Developers, n.d.-c).
        val textColor = ContextCompat.getColor(holder.itemView.context, cubeColorResIds[paletteIndex])
        holder.tvName.setTextColor(textColor)
        holder.tvAmount.setTextColor(textColor)

        holder.tvName.text = category.name
        holder.tvAmount.text = "%.2f".format(category.amountSaved)
    }
}

// References:
// Android Developers, n.d.-a. Create dynamic lists with RecyclerView [Webpage].
//     Available at: <https://developer.android.com/develop/ui/views/layout/recyclerview>
//     [Accessed 27 October 2026].
// Android Developers, n.d.-b. ImageView - setImageResource(int) [Webpage]. Available at:
//     <https://developer.android.com/reference/android/widget/ImageView#setImageResource(int)>
//     [Accessed 27 October 2026].
// Android Developers, n.d.-c. ContextCompat - getColor(Context, int) [Webpage]. Available at:
//     <https://developer.android.com/reference/androidx/core/content/ContextCompat#getColor(android.content.Context,int)>
//     [Accessed 27 October 2026].
