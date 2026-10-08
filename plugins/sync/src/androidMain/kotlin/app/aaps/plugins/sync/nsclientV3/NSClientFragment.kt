package app.aaps.plugins.sync.nsclientV3

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.text.toSpanned
import androidx.core.view.MenuCompat
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.nsclient.NSClientLog
import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginFragment
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.sync.R
import app.aaps.plugins.sync.nsclientV3.keys.NsclientBooleanKey
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.zacsweers.metro.Inject

private fun NSClientLog.toPreparedHtml(dateUtil: DateUtil): StringBuilder {
    val sb = StringBuilder()
    sb.append(dateUtil.timeStringWithSeconds(date))
    sb.append(" <b>").append(action).append("</b> ")
    sb.append(logText ?: "")
    if (json != null) sb.append(" {...}")
    sb.append("<br>")
    return sb
}

class NSClientFragment : Fragment(), MenuProvider, PluginFragment {

    @Inject lateinit var preferences: Preferences
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var nsClientRepository: NSClientRepository
    @Inject lateinit var dateUtil: DateUtil

    companion object {

        const val ID_MENU_CLEAR_LOG = 507
        const val ID_MENU_RESTART = 508
        const val ID_MENU_SEND_NOW = 509
        const val ID_MENU_FULL_SYNC = 510
    }

    override var plugin: PluginBase? = null
    private val nsClientPlugin
        get() = plugin as? NsClient
            ?: activePlugin.activeSyncs.filterIsInstance<NsClient>().firstOrNull()

    // This property is only valid between onCreateView and onDestroyView.
    private var views: NsClientFragmentViews? = null
    private lateinit var logAdapter: RecyclerViewAdapter
    private var handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    // https://stackoverflow.com/questions/31759171/recyclerview-and-java-lang-indexoutofboundsexception-inconsistency-detected-in
    class FixedLinearLayoutManager(context: Context?, @RecyclerView.Orientation orientation: Int = RecyclerView.VERTICAL, reverseLayout: Boolean = false) :
        LinearLayoutManager(context, orientation, reverseLayout) {

        override fun supportsPredictiveItemAnimations(): Boolean = false
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        NsClientFragmentViews.from(inflater.inflate(R.layout.ns_client_fragment, container, false)).also {
            views = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val views = views ?: return

        views.paused.isChecked = preferences.get(NsclientBooleanKey.NsPaused)
        views.paused.setOnCheckedChangeListener { _, isChecked ->
            uel.log(action = if (isChecked) Action.NS_PAUSED else Action.NS_RESUME, source = Sources.NSClient)
            nsClientPlugin?.pause(isChecked)
        }

        logAdapter = RecyclerViewAdapter(nsClientRepository.logList.value)
        views.recyclerview.layoutManager = FixedLinearLayoutManager(context)
        views.recyclerview.adapter = logAdapter
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(Menu.FIRST, ID_MENU_CLEAR_LOG, 0, rh.gs(R.string.clear_log)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_RESTART, 0, rh.gs(R.string.restart)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_SEND_NOW, 0, rh.gs(R.string.deliver_now)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(Menu.FIRST, ID_MENU_FULL_SYNC, 0, rh.gs(R.string.full_sync)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        MenuCompat.setGroupDividerEnabled(menu, true)
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            ID_MENU_CLEAR_LOG -> {
                nsClientRepository.clearLog()
                views?.recyclerview?.swapAdapter(RecyclerViewAdapter(emptyList()), true)
                true
            }

            ID_MENU_RESTART   -> {
                (nsClientPlugin as? NSClientV3Plugin)?.restartFromGui()
                true
            }

            ID_MENU_SEND_NOW  -> {
                handler.post { nsClientPlugin?.resend("GUI") }
                true
            }

            ID_MENU_FULL_SYNC -> {
                OKDialog.showConfirmation(
                    requireActivity(),
                    rh.gs(R.string.ns_client),
                    rh.gs(R.string.full_sync_comment),
                    Runnable {
                        OKDialog.showConfirmation(
                            requireActivity(),
                            rh.gs(R.string.ns_client),
                            rh.gs(app.aaps.core.ui.R.string.cleanup_db_confirm_sync),
                            Runnable {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            persistenceLayer.cleanupDatabase(dateUtil.now() - T.days(93).msecs(), deleteTrackedChanges = true, runVacuum = true)
                                        }
                                        if (result.isNotEmpty()) {
                                            OKDialog.show(
                                                requireActivity(),
                                                rh.gs(app.aaps.core.ui.R.string.result),
                                                HtmlHelper.fromHtml("<b>" + rh.gs(app.aaps.core.ui.R.string.cleared_entries) + "</b><br>" + result)
                                                    .toSpanned(),
                                                true,
                                                null
                                            )
                                        }
                                        aapsLogger.info(LTag.CORE, "Cleaned up databases with result: $result")
                                        withContext(Dispatchers.IO) {
                                            nsClientPlugin?.resetToFullSync()
                                        }
                                        nsClientPlugin?.resend("FULL_SYNC")
                                    } catch (e: Exception) {
                                        aapsLogger.error("Error cleaning up databases", e)
                                    }
                                }
                                uel.log(action = Action.CLEANUP_DATABASES, source = Sources.NSClient)
                            },
                            Runnable {
                                viewLifecycleOwner.lifecycleScope.launch {
                                    withContext(Dispatchers.IO) {
                                        nsClientPlugin?.resetToFullSync()
                                    }
                                    nsClientPlugin?.resend("FULL_SYNC")
                                }
                            }
                        )
                    },
                    Runnable { }
                )
                true
            }

            else              -> false
        }

    @Synchronized
    override fun onDestroyView() {
        super.onDestroyView()
        views?.recyclerview?.adapter = null
        views = null
    }

    @Synchronized
    override fun onResume() {
        super.onResume()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch {
                    nsClientRepository.logList.collect { list ->
                        views?.recyclerview?.swapAdapter(RecyclerViewAdapter(list), true)
                    }
                }
                launch {
                    nsClientRepository.queueSize.collect { size ->
                        views?.queue?.text =
                            if (size >= 0) size.toString() else rh.gs(app.aaps.core.ui.R.string.value_unavailable_short)
                    }
                }
                launch {
                    nsClientRepository.statusUpdate
                        .debounce(3000L)
                        .collect { status ->
                            views?.status?.text = status
                        }
                }
                launch {
                    nsClientRepository.urlUpdate.collect { url ->
                        views?.url?.text = url
                    }
                }
            }
        }
        views?.paused?.isChecked = preferences.get(NsclientBooleanKey.NsPaused)
    }

    @Synchronized
    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    private inner class RecyclerViewAdapter(private var logList: List<NSClientLog>) : RecyclerView.Adapter<RecyclerViewAdapter.NsClientLogViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): NsClientLogViewHolder =
            NsClientLogViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.ns_client_log_item, viewGroup, false))

        override fun onBindViewHolder(holder: NsClientLogViewHolder, position: Int) {
            holder.views.logText.text = HtmlHelper.fromHtml(logList[position].toPreparedHtml(dateUtil).toString())
        }

        override fun getItemCount() = logList.size

        inner class NsClientLogViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val views = NsClientLogItemViews.from(view)
        }
    }
}

/**
 * The views of `ns_client_fragment.xml`, found by hand.
 *
 * A Kotlin Multiplatform module never gets generated view binding classes, so there is no
 * `NsClientFragmentBinding` to use. The names are the ones the generated class used.
 */
class NsClientFragmentViews(
    val root: ScrollView,
    val paused: SwitchMaterial,
    val url: TextView,
    val status: TextView,
    val queue: TextView,
    val recyclerview: RecyclerView,
) {
    companion object {
        fun from(root: View): NsClientFragmentViews = NsClientFragmentViews(
            root = root as ScrollView,
            paused = root.findViewById(R.id.paused),
            url = root.findViewById(R.id.url),
            status = root.findViewById(R.id.status),
            queue = root.findViewById(R.id.queue),
            recyclerview = root.findViewById(R.id.recyclerview),
        )
    }
}

/** The views of `ns_client_log_item.xml`, found by hand for the same reason as [NsClientFragmentViews]. */
class NsClientLogItemViews(
    val root: View,
    val logText: TextView,
) {
    companion object {
        fun from(root: View): NsClientLogItemViews = NsClientLogItemViews(
            root = root,
            logText = root.findViewById(R.id.log_text),
        )
    }
}
