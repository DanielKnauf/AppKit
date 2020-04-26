package knaufdan.android.arch.databinding.recyclerview

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import knaufdan.android.arch.base.component.BindingKey
import knaufdan.android.arch.base.component.LayoutRes
import knaufdan.android.arch.utils.findLifecycleOwner

class BindableAdapter<DataSource>(
    dataSources: List<DataSource>,
    private val layoutRes: LayoutRes,
    private val bindingKey: BindingKey
) : RecyclerView.Adapter<BindableViewHolder<DataSource>>() {
    // Store data in separate list to lose the reference and prevent error if references changes.
    private val dataSources = dataSources

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): BindableViewHolder<DataSource> =
        parent.run {
            val binding = DataBindingUtil.inflate<ViewDataBinding>(
                LayoutInflater.from(context),
                layoutRes,
                this,
                false
            ).apply {
                context.findLifecycleOwner()?.apply {
                    lifecycleOwner = this
                }
            }

            BindableViewHolder(
                binding = binding,
                bindingKey = bindingKey
            )
        }

    override fun onBindViewHolder(
        holder: BindableViewHolder<DataSource>,
        position: Int
    ) {
        holder.bind(dataSources[position])
    }

    override fun getItemCount(): Int = dataSources.size
}
