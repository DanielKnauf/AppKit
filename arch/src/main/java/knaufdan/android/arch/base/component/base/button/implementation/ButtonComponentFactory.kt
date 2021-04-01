package knaufdan.android.arch.base.component.base.button.implementation

import knaufdan.android.arch.base.component.IComponent
import knaufdan.android.arch.base.component.base.button.ButtonConfig
import knaufdan.android.arch.base.component.base.button.IButtonComponentFactory
import knaufdan.android.core.resources.IResourceProvider

class ButtonComponentFactory(
    private val resourceProvider: IResourceProvider
) : IButtonComponentFactory {
    override fun get(config: ButtonConfig): IComponent<*> =
        ButtonComponent(
            config = config,
            resourceProvider = resourceProvider
        )
}
