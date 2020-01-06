package knaufdan.android.arch.databinding

import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.databinding.BindingAdapter
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

@BindingAdapter(value = ["backgroundResource"])
fun View.bindBackgroundResource(@DrawableRes background: Int) {
    if (background != -1) setBackground(context.getDrawable(background))
}

@BindingAdapter(value = ["number"])
fun TextView.bindNumber(number: Int?) {
    text = number?.toString() ?: ""
}

@BindingAdapter(value = ["element"])
fun ViewGroup.bindElement(element: IBindableElement<*>?) {
    if (element == null) {
        return
    }

    if (element.getDataSource() is List<*>) {
        element.toListElement().bindToRecyclerView(parent = this)
    } else {
        element.bindToLinearLayout(parent = this)
    }
}

private fun <DataSource> IBindableElement<List<DataSource>>.bindToRecyclerView(parent: ViewGroup) {
    val context = parent.context

    RecyclerView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutManager = LinearLayoutManager(context)
        adapter = BindableAdapter(
            dataSources = getDataSource(),
            layoutRes = getLayoutRes(),
            bindingKey = getBindingKey()
        )

        parent.removeAllViewsInLayout()
        parent.addView(this)
    }
}

private fun <DataSource> IBindableElement<DataSource>.bindToLinearLayout(parent: ViewGroup) {
    val context = parent.context

    try {
        DataBindingUtil.inflate<ViewDataBinding>(
            LayoutInflater.from(context),
            getLayoutRes(),
            parent,
            false
        ).apply {
            setVariable(getBindingKey(), getDataSource())
            if (context is LifecycleOwner) lifecycleOwner = context
            parent.removeAllViewsInLayout()
            parent.addView(root)
        }
    } catch (e: Throwable) {
        Log.e(
            ".bindToLinearLayout()",
            "LayoutRes could not be found. No binding was generated for $parent in $context"
        )
    }
}

private fun IBindableElement<*>.toListElement() =
    object : IBindableElement<List<*>> {
        override fun getLayoutRes() = this@toListElement.getLayoutRes()

        override fun getBindingKey() = this@toListElement.getBindingKey()

        override fun getDataSource() = this@toListElement.getDataSource() as List<*>
    }