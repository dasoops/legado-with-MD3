package io.legado.app.help

import androidx.annotation.IntDef
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

@Suppress("unused")
object LayoutManager {
    interface LayoutManagerFactory {
        fun create(recyclerView: RecyclerView): RecyclerView.LayoutManager
    }

    @IntDef(LinearLayoutManager.HORIZONTAL, LinearLayoutManager.VERTICAL)
    @Retention(AnnotationRetention.SOURCE)
    annotation class Orientation

    fun linear(): LayoutManagerFactory = object : LayoutManagerFactory {
        override fun create(recyclerView: RecyclerView): RecyclerView.LayoutManager = LinearLayoutManager(recyclerView.context)
    }

    fun linear(
        @Orientation orientation: Int,
        reverseLayout: Boolean,
    ): LayoutManagerFactory = object : LayoutManagerFactory {
        override fun create(recyclerView: RecyclerView): RecyclerView.LayoutManager = LinearLayoutManager(recyclerView.context, orientation, reverseLayout)
    }

    fun grid(spanCount: Int): LayoutManagerFactory = object : LayoutManagerFactory {
        override fun create(recyclerView: RecyclerView): RecyclerView.LayoutManager = GridLayoutManager(recyclerView.context, spanCount)
    }

    fun grid(
        spanCount: Int,
        @Orientation orientation: Int,
        reverseLayout: Boolean,
    ): LayoutManagerFactory = object : LayoutManagerFactory {
        override fun create(recyclerView: RecyclerView): RecyclerView.LayoutManager = GridLayoutManager(
            recyclerView.context,
            spanCount,
            orientation,
            reverseLayout,
        )
    }

    fun staggeredGrid(
        spanCount: Int,
        @Orientation orientation: Int,
    ): LayoutManagerFactory = object : LayoutManagerFactory {
        override fun create(recyclerView: RecyclerView): RecyclerView.LayoutManager = StaggeredGridLayoutManager(spanCount, orientation)
    }
}
