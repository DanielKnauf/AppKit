package knaufdan.android.arch.navigation.implementation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.transition.Slide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import knaufdan.android.arch.R
import knaufdan.android.arch.base.component.IComponent
import knaufdan.android.arch.base.component.IComponentViewModel
import knaufdan.android.arch.base.component.addition.fragment.ComponentFragmentFactory
import knaufdan.android.arch.mvvm.implementation.BaseFragment
import knaufdan.android.arch.mvvm.implementation.BaseFragmentViewModel
import knaufdan.android.arch.mvvm.implementation.dialog.BaseDialogFragment
import knaufdan.android.arch.mvvm.implementation.dialog.ComponentDialogFragmentFactory
import knaufdan.android.arch.mvvm.implementation.dialog.api.DialogStyle
import knaufdan.android.arch.navigation.AlertDialogConfig
import knaufdan.android.arch.navigation.FragmentTag
import knaufdan.android.arch.navigation.IFragmentTransaction
import knaufdan.android.arch.navigation.INavigationService
import knaufdan.android.arch.navigation.IWebTarget
import knaufdan.android.arch.utils.getColorCompat
import knaufdan.android.core.IContextProvider
import knaufdan.android.core.common.applyIf
import knaufdan.android.core.resources.IResourceProvider
import kotlin.reflect.KClass

internal class NavigationService(
    private val contextProvider: IContextProvider
) : INavigationService {

    override val fragmentManager: FragmentManager?
        get() = activity?.supportFragmentManager

    override var containerViewId = R.id.arch_fragment_container

    override val navigationController: NavController?
        get() = runCatching {
            contextProvider
                .fragmentManager
                ?.navigationController
        }.getOrNull()

    private val context: Context
        get() = contextProvider.getContext()
    private val activity: AppCompatActivity?
        get() = context as? AppCompatActivity

    override fun toAlertDialog(
        config: AlertDialogConfig,
        onPositiveButtonClicked: () -> Unit,
        onNegativeButtonClicked: () -> Unit,
        onDismissClicked: () -> Unit
    ) {
        MaterialAlertDialogBuilder(context)
            .setTitle(config.title)
            .setMessage(config.message)
            .setCancelable(config.isCancelable)
            .setPositiveButton(config.buttonPositive) { _, _ -> onPositiveButtonClicked() }
            .setNegativeButton(config.buttonNegative) { _, _ -> onNegativeButtonClicked() }
            .setOnDismissListener { onDismissClicked() }
            .show()
    }

    override fun toDestination(directions: NavDirections) {
        navigationController?.navigate(directions)
    }

    override fun toFragment(
        transaction: IFragmentTransaction,
        containerViewId: Int
    ) {
        fragmentManager?.commit {
            when (val animation = transaction.animation) {
                null -> setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                else -> setCustomAnimations(
                    animation.enter,
                    animation.exit,
                    animation.popEnter,
                    animation.popExit
                )
            }

            setReorderingAllowed(true)
            if (transaction.addToBackStack) addToBackStack(transaction.fragment.tag)

            when (transaction) {
                is IFragmentTransaction.Add -> add(containerViewId, transaction.fragment)
                is IFragmentTransaction.Replace -> replace(containerViewId, transaction.fragment)
            }
        }
    }

    override fun toEmail(
        recipient: String,
        subject: String
    ) =
        withContext {
            val intent =
                intentForEmail(
                    recipient = recipient,
                    subject = subject
                )

            runCatching {
                startActivity(
                    Intent.createChooser(
                        intent,
                        getString(R.string.arch_navigation_service_mail_client_chooser)
                    )
                )
            }.getOrElse { error ->
                Log.e(
                    NavigationService::class.java.name,
                    "Could not resolve intent for e-mail" +
                        "\n- intent=$intent" +
                        "\n- error=$error"
                )
                showToast(R.string.arch_navigation_service_error_no_mail_client)
            }
        }

    override fun toWeb(target: IWebTarget) {
        when (target) {
            is IWebTarget.CustomTab ->
                runCatching {
                    CustomTabsIntent.Builder()
                        .applyIf(target.toolbarColor != IResourceProvider.INVALID_RES_ID) {
                            setDefaultColorSchemeParams(
                                CustomTabColorSchemeParams.Builder()
                                    .setToolbarColor(context.getColorCompat(target.toolbarColor))
                                    .build()
                            )
                        }
                        .build()
                        .launchUrl(context, Uri.parse(target.url))
                }.getOrElse {
                    showToast(R.string.arch_navigation_service_error_no_browser)
                }
            is IWebTarget.PlayStore ->
                runCatching {
                    context.startActivity(
                        intent(Intent.ACTION_VIEW) {
                            data = Uri.parse(target.url)
                            setPackage("com.android.vending")
                        }
                    )
                }.getOrElse {
                    showToast(R.string.arch_navigation_service_error_play_store)
                }
        }
    }

    override fun showComponent(
        component: IComponent<IComponentViewModel>,
        addToBackStack: Boolean,
        containerViewId: Int
    ) {
        withActivity {
            val fragment =
                ComponentFragmentFactory(component).run {
                    supportFragmentManager.fragmentFactory = this
                    instantiate()
                }

            supportFragmentManager.commit {
                setReorderingAllowed(true)

                replace(
                    containerViewId,
                    fragment,
                    component.getId()
                )

                /**
                 * Currently there is an issue in Androidx Transition library 1.3.1
                 * that results in a crash on Android 7 devices only. Therefore
                 * we skip using fragment transitions for that devices for now.
                 */
                val transitionsEnabled = Build.VERSION.SDK_INT > Build.VERSION_CODES.N
                if (transitionsEnabled) {
                    fragment.enterTransition = Slide(Gravity.END)
                    fragment.exitTransition = Slide(Gravity.START)
                }

                if (addToBackStack) addToBackStack(component.getId())
            }
        }
    }

    override fun goToFragment(
        fragment: BaseFragment<out BaseFragmentViewModel>,
        addToBackStack: Boolean,
        containerViewId: Int,
        clearBackStack: Boolean,
        vararg params: Pair<String, Any?>
    ) {
        (fragment.arguments ?: Bundle()).apply {
            putAll(bundleOf(*params))

            fragment.arguments = this
        }

        withContext {
            if (clearBackStack) {
                replaceFragmentCleanly(
                    fragment = fragment,
                    containerViewId = containerViewId
                )
            } else {
                replaceFragment(
                    fragment = fragment,
                    addToBackStack = addToBackStack,
                    containerViewId = containerViewId
                )
            }
        }
    }

    override fun <T : Fragment> getFragment(
        fragmentClass: KClass<out T>
    ): T? =
        withActivity {
            supportFragmentManager
                .fragments
                .filterIsInstance(fragmentClass.java)
                .firstOrNull()
        }

    override fun showDialog(
        component: IComponent<IComponentViewModel>,
        fragmentTag: FragmentTag,
        dialogStyle: DialogStyle
    ) {
        showDialog<Unit>(
            component = component,
            fragmentTag = fragmentTag,
            dialogStyle = dialogStyle
        )
    }

    override fun showDialog(
        dialog: DialogFragment,
        fragmentTag: FragmentTag
    ) {
        fragmentManager?.run {
            dialog
                .show(this, fragmentTag)
        }
    }

    override fun <ResultType> showDialog(
        fragment: BaseDialogFragment<out BaseFragmentViewModel>,
        dialogStyle: DialogStyle,
        callback: (ResultType?) -> Unit
    ) =
        context.showDialog(
            fragment = fragment,
            dialogSize = dialogStyle.dialogSize,
            callback = callback
        )

    override fun <ResultType> showDialog(
        component: IComponent<IComponentViewModel>,
        fragmentTag: FragmentTag,
        dialogStyle: DialogStyle,
        callback: (ResultType?) -> Unit
    ) {
        withActivity {
            val fragment =
                ComponentDialogFragmentFactory(
                    component = component,
                    dialogStyle = dialogStyle
                ).run {
                    supportFragmentManager.fragmentFactory = this
                    instantiate()
                }

            val tag = fragmentTag.ifBlank { component.getId() }

            context.showDialog(
                fragment = fragment,
                fragmentTag = tag,
                dialogSize = dialogStyle.dialogSize,
                callback = callback
            )
        }
    }

    override fun dismissDialog(viewModel: BaseFragmentViewModel) =
        dismissDialog(
            viewModel = viewModel,
            result = null
        )

    override fun <ResultType> dismissDialog(
        viewModel: BaseFragmentViewModel,
        result: ResultType?
    ) {
        dismissDialog(
            fragmentTag = viewModel.fragmentTag,
            result = result
        )
    }

    override fun dismissDialog(fragmentTag: String) =
        dismissDialog(
            fragmentTag = fragmentTag,
            result = null
        )

    override fun <ResultType> dismissDialog(
        fragmentTag: String,
        result: ResultType?
    ) =
        context.dismissDialog(
            fragmentTag = fragmentTag,
            result = result
        )

    override fun showToast(
        text: Int,
        duration: Int
    ) {
        Toast.makeText(context, text, duration).show()
    }

    override fun clearBackStack() {
        fragmentManager?.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
    }

    override fun hideKeyboard() {
        withActivity {
            currentFocus?.run {
                (getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
                    ?.hideSoftInputFromWindow(windowToken, 0)
            }
        }
    }

    override fun onBackPressed() {
        withActivity { onBackPressed() }
    }

    internal fun dismissDialogBySystem(
        fragmentTag: String
    ) =
        context.dismissDialog(
            fragmentTag = fragmentTag,
            result = null,
            dismissedBySystem = true
        )

    private fun <T> withActivity(block: AppCompatActivity.() -> T) = activity?.block()

    private fun <T> withContext(block: Context.() -> T) = context.block()

    companion object {

        private fun intent(action: String, block: Intent.() -> Unit): Intent =
            Intent(action).apply(block)

        private fun intentForEmail(
            recipient: String,
            subject: String
        ): Intent =
            intent(Intent.ACTION_SENDTO) {
                data = Uri.parse("mailto:")
                putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
                putExtra(Intent.EXTRA_SUBJECT, subject)
            }
    }
}