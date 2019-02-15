package cash.z.android.wallet.ui.fragment

import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.transition.Transition
import androidx.transition.TransitionInflater
import cash.z.android.wallet.R
import cash.z.android.wallet.databinding.FragmentHomeBinding
import cash.z.android.wallet.extention.*
import cash.z.android.wallet.sample.SampleProperties
import cash.z.android.wallet.sample.SampleProperties.DEV_MODE
import cash.z.android.wallet.ui.adapter.TransactionAdapter
import cash.z.android.wallet.ui.presenter.HomePresenter
import cash.z.android.wallet.ui.util.AlternatingRowColorDecoration
import cash.z.android.wallet.ui.util.LottieLooper
import cash.z.android.wallet.ui.util.TopAlignedSpan
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.ActiveSendTransaction
import cash.z.wallet.sdk.data.ActiveTransaction
import cash.z.wallet.sdk.data.TransactionState
import cash.z.wallet.sdk.ext.toZec
import com.google.android.material.snackbar.Snackbar
import com.leinardi.android.speeddial.SpeedDialActionItem
import dagger.Module
import dagger.android.ContributesAndroidInjector
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.random.nextLong


/**
 * Fragment representing the home screen of the app. This is the screen most often seen by the user when launching the
 * application.
 */
class HomeFragment : BaseFragment(), SwipeRefreshLayout.OnRefreshListener, HomePresenter.HomeView {

    private lateinit var homePresenter: HomePresenter
    private lateinit var binding: FragmentHomeBinding
    private lateinit var zcashLogoAnimation: LottieLooper
    private var snackbar: Snackbar? = null
    private var viewsInitialized = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewsInitialized = false
//        setupSharedElementTransitions()
        return DataBindingUtil.inflate<FragmentHomeBinding>(
            inflater, R.layout.fragment_home, container, false
        ).let {
            binding = it
            it.root
        }
    }

    private fun setupSharedElementTransitions() {
        TransitionInflater.from(mainActivity).inflateTransition(R.transition.transition_zec_sent).apply {
            duration = 3000L
            addListener(HomeTransitionListener())
            this@HomeFragment.sharedElementEnterTransition = this
            this@HomeFragment.sharedElementReturnTransition = this
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initTemp()
        init()
//        launch {
//            Log.e("TWIG", "deciding whether to show first run")
//            val extraDelay = measureTimeMillis {
//                setFirstRunShown(mainActivity.synchronizer.isFirstRun() || mainActivity.synchronizer.isOutOfSync())
//            }
//            Log.e("TWIG", "done deciding whether to show first run in $extraDelay ms. Was that worth it? Or should we toggle a boolean in the application class?")
//        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        with(mainActivity) {
            setSupportActionBar(binding.includeHeader.homeToolbar)
            setupNavigation()
            supportActionBar?.setTitle(R.string.destination_title_home)
        }
        initFab()

        homePresenter = HomePresenter(this, mainActivity.synchronizer)

        binding.includeContent.recyclerTransactions.apply {
            layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
            adapter = TransactionAdapter()
            addItemDecoration(AlternatingRowColorDecoration())
        }
    }

    override fun onResume() {
        super.onResume()
        launch {
            homePresenter.start()
        }
    }

    override fun onPause() {
        super.onPause()
        homePresenter.stop()
        binding.lottieZcashBadge.cancelAnimation()
    }


    //
    // View API
    //

    fun setContentViewShown(isShown: Boolean) {
        with(binding.includeContent) {
            groupEmptyViewItems.visibility = if (isShown) View.GONE else View.VISIBLE
            groupContentViewItems.visibility = if (isShown) View.VISIBLE else View.GONE
        }
        toggleViews(!isShown)
    }

    override fun onRefresh() {
        setRefreshAnimationPlaying(true).also { Log.e("TWIG-a", "refresh true from onRefresh") }

        with(binding.includeContent.refreshLayout) {
            isRefreshing = false
            val fauxRefresh = Random.nextLong(750L..3000L)
            postDelayed({
                setRefreshAnimationPlaying(false).also { Log.e("TWIG-a", "refresh false from onRefresh") }
            }, fauxRefresh)
        }
    }


    fun setRefreshAnimationPlaying(isPlaying: Boolean) {
        Log.e("TWIG-a", "set refresh to: $isPlaying for $zcashLogoAnimation")
        if (isPlaying) {
            zcashLogoAnimation.start()
        } else {
            zcashLogoAnimation.stop()
            view?.postDelayed({
                zcashLogoAnimation.stop()
            }, 500L)
        }
    }






    //TODO: pull some of this logic into the presenter, particularly the part that deals with ZEC <-> USD price conversion
    override fun updateBalance(old: Long, new: Long) {
        val zecValue = new/1e8
        setZecValue(zecValue)
        setUsdValue(SampleProperties.USD_PER_ZEC * zecValue)

        onContentRefreshComplete(zecValue)
    }

    override fun setTransactions(transactions: List<WalletTransaction>) {
        with (binding.includeContent.recyclerTransactions) {
            (adapter as TransactionAdapter).submitList(transactions)
            postDelayed({
                smoothScrollToPosition(0)
            }, 100L)
            if (binding.includeFirstRun.visibility == View.VISIBLE) setFirstRunShown(false)
        }
    }

    override fun showProgress(progress: Int) {
        if(progress >= 100) {
            view?.postDelayed({
                onInitialLoadComplete()
            }, 3000L)
        } else {
            setRefreshAnimationPlaying(true).also { Log.e("TWIG-a", "refresh true from showProgress") }
            binding.includeContent.textEmptyWalletMessage.setText(R.string.home_empty_wallet_updating)
        }
//        snackbar.showOk(view!!, "progress: $progress")
        // TODO: improve this with Lottie animation. but for now just use the empty view for downloading...
//        var hasEmptyViews = group_empty_view_items.visibility == View.VISIBLE
//        if(!viewsInitialized) toggleViews(true)
//
//        val message = if(progress >=  100) "Download complete! Processing blocks..." else "Downloading remaining blocks ($progress%)"
////        text_wallet_message.text = message
//
//        if (snackbar == null && progress <= 50) {
//            snackbar = Snackbar.make(view!!, "$message", Snackbar.LENGTH_INDEFINITE)
//                .setAction("OK") {
//                    snackbar?.dismiss()
//                }
//            snackbar?.show()
//        } else {
//            snackbar?.setText(message)
//            if(snackbar?.isShownOrQueued != true) snackbar?.show()
//        }
    }

    private fun onInitialLoadComplete() {
        val isEmpty = (binding.includeContent.recyclerTransactions?.adapter?.itemCount ?: 0).let { it == 0 }
        Log.e("TWIG-t", "onInitialLoadComplete and isEmpty == $isEmpty")
        setContentViewShown(!isEmpty)
        if (isEmpty) {
            binding.includeContent.textEmptyWalletMessage.setText(R.string.home_empty_wallet)
        }
        setRefreshAnimationPlaying(false).also { Log.e("TWIG-a", "refresh false from onInitialLoadComplete") }
    }


    override fun setActiveTransactions(activeTransactionMap: Map<ActiveTransaction, TransactionState>) {
        if (activeTransactionMap.isEmpty()) {
            setActiveTransactionsShown(false)
            return
        }

        val transactions = activeTransactionMap.entries.toTypedArray()
        // primary is the last one that was inserted
        val primaryEntry = transactions[transactions.size - 1]
        updatePrimaryTransaction(primaryEntry.key, primaryEntry.value)
        // TODO: update remaining transactions
    }

    override fun onCancelledTooLate() {
        snackbar = snackbar.showOk(view!!, "Oops! It was too late to cancel!")
    }

    private fun updatePrimaryTransaction(transaction: ActiveTransaction, transactionState: TransactionState) {
        setActiveTransactionsShown(true)
        Log.e("TWIG", "setting transaction state to ${transactionState::class.simpleName}")
        var title = "Active Transaction"
        var subtitle = "Processing..."
        when (transactionState) {
            TransactionState.Creating -> {
                binding.includeContent.headerActiveTransaction.visibility = View.VISIBLE
                title = "Preparing ${transaction.value.toZec(3)} ZEC"
                subtitle = "to ${(transaction as ActiveSendTransaction).toAddress}"
                setTransactionActive(transaction, true)
            }
            TransactionState.SendingToNetwork -> {
                title = "Sending Transaction"
                subtitle = "to ${(transaction as ActiveSendTransaction).toAddress}"
                binding.includeContent.textActiveTransactionValue.text = "${transaction.value/1000L}"
                binding.includeContent.textActiveTransactionValue.visibility = View.VISIBLE
                binding.includeContent.buttonActiveTransactionCancel.visibility = View.GONE
            }
            is TransactionState.Failure -> {
                binding.includeContent.lottieActiveTransaction.setAnimation(R.raw.lottie_send_failure)
                binding.includeContent.lottieActiveTransaction.playAnimation()
                title = "Failed"
                subtitle = when(transactionState.failedStep) {
                    TransactionState.Creating -> "Failed to create transaction"
                    TransactionState.SendingToNetwork -> "Failed to submit transaction to the network"
                    else -> "Unrecoginzed error"
                }
                binding.includeContent.buttonActiveTransactionCancel.visibility = View.GONE
                binding.includeContent.textActiveTransactionValue.visibility = View.GONE
                setTransactionActive(transaction, false)
                setActiveTransactionsShown(false, 10000L)
            }
            is TransactionState.AwaitingConfirmations -> {
                if (transactionState.confirmationCount < 1) {
                    binding.includeContent.lottieActiveTransaction.setAnimation(R.raw.lottie_send_success)
                    binding.includeContent.lottieActiveTransaction.playAnimation()
                    title = "ZEC Sent"
                    subtitle = "Today at 2:11pm"
                    binding.includeContent.textActiveTransactionValue.text = transaction.value.toZec(3).toString()
                    binding.includeContent.textActiveTransactionValue.visibility = View.VISIBLE
                    binding.includeContent.buttonActiveTransactionCancel.visibility = View.GONE
                } else {
                    title = "Confirmation Received"
                    subtitle = "Today at 2:12pm"
                    // take it out of the list in a bit and skip counting confirmation animation for now (i.e. one is enough)
                    setActiveTransactionsShown(false, 3000L)
                }
            }
            is TransactionState.Cancelled -> {
                title = binding.includeContent.textActiveTransactionTitle.text.toString()
                subtitle = binding.includeContent.textActiveTransactionSubtitle.text.toString()
                setTransactionActive(transaction, false)
            }
        }
        binding.includeContent.textActiveTransactionTitle.text = title
        binding.includeContent.textActiveTransactionSubtitle.text = subtitle
    }


    //
    // Private View API
    //

    private fun setActiveTransactionsShown(isShown: Boolean, delay: Long = 0L) {
        Log.e("TWIG-a", "setActiveTransactionsShown: $isShown")
        binding.includeContent.headerActiveTransaction.postDelayed({
            binding.includeContent.groupActiveTransactionItems.visibility = if (isShown) View.VISIBLE else View.GONE
        }, delay)
    }

    private fun setFirstRunShown(isShown: Boolean) {
        binding.includeFirstRun.visibility = if (isShown) View.VISIBLE else View.GONE
        mainActivity.setDrawerLocked(isShown)
        binding.sdFab.visibility = if (!isShown) View.VISIBLE else View.GONE
        binding.lottieZcashBadge.visibility = if(!isShown) View.VISIBLE else View.GONE
    }

    /**
     * General initialization called during onViewCreated. Mostly responsible for applying the default empty state of
     * the view, before any data or information is known.
     */
    private fun init() {
        zcashLogoAnimation = LottieLooper(binding.lottieZcashBadge, 20..47, 69)
        binding.includeContent.buttonActiveTransactionCancel.setOnClickListener {
            val transaction = it.tag as? ActiveSendTransaction
            if (transaction != null) {
                homePresenter.onCancelActiveTransaction(transaction)
            } else {
                Toaster.short("Error: unable to find transaction to cancel!")
            }
        }

        binding.includeContent.refreshLayout.setProgressViewEndTarget(false, (38f * resources.displayMetrics.density).toInt())

        with(binding.includeContent.refreshLayout) {
            setOnRefreshListener(this@HomeFragment)
            setColorSchemeColors(R.color.zcashBlack.toAppColor())
            setProgressBackgroundColorSchemeColor(R.color.zcashYellow.toAppColor())
        }

        // hide content
        setActiveTransactionsShown(false)
        setContentViewShown(false)
        binding.includeContent.textEmptyWalletMessage.setText(R.string.home_empty_wallet_updating)
        setRefreshAnimationPlaying(true).also { Log.e("TWIG-a", "refresh true from init") }
    }

    // initialize the stuff that is temporary and needs to go ASAP
    private fun initTemp() {

        with(binding.includeHeader) {
            headerFullViews = arrayOf(textBalanceUsd, textBalanceIncludesInfo, textBalanceZec, imageZecSymbolBalanceShadow, imageZecSymbolBalance)
            headerEmptyViews = arrayOf(textBalanceZecInfo, textBalanceZecEmpty, imageZecSymbolBalanceShadowEmpty, imageZecSymbolBalanceEmpty)
            headerFullViews.forEach { containerHomeHeader.removeView(it) }
            headerEmptyViews.forEach { containerHomeHeader.removeView(it) }
            binding.includeHeader.containerHomeHeader.visibility = View.INVISIBLE
        }

        // toggling determines visibility. hide it all.
        binding.includeContent.groupEmptyViewItems.visibility = View.GONE
        binding.includeContent.groupContentViewItems.visibility = View.GONE
    }

    /**
     * Initialize the Fab button and all its action items. Should be called during onActivityCreated.
     */
    private fun initFab() {
        val speedDial = binding.sdFab
        val nav = mainActivity.navController

        HomeFab.values().forEach {
            speedDial.addActionItem(it.createItem())
        }

        speedDial.setOnActionSelectedListener { item ->
            if (item.id == R.id.fab_request) {
                Toaster.short("off!")
                setActiveTransactionsShown(false)
//                setRefreshAnimationPlaying(false)
            } else if (item.id == R.id.fab_receive) {
                Toaster.short("on!")
                setActiveTransactionsShown(true)
//                setRefreshAnimationPlaying(true)
            } else {
                HomeFab.fromId(item.id)?.destination?.apply { nav.navigate(this) }
            }
            false
        }
    }

    /**
     * Helper for creating fablets--those little buttons that pop up when the fab is tapped.
     */
    private val createItem: HomeFab.() -> SpeedDialActionItem = {
        SpeedDialActionItem.Builder(id, icon)
            .setFabBackgroundColor(bgColor.toAppColor())
            .setFabImageTintColor(R.color.zcashWhite.toAppColor())
            .setLabel(label.toAppString())
            .setLabelClickable(true)
            .create()
    }

    private fun setUsdValue(value: Double) {
        val valueString = String.format("$ %,.2f",value)
        val hairSpace = "\u200A"
//        val adjustedValue = "$$hairSpace$valueString"
        val textSpan = SpannableString(valueString)
        textSpan.setSpan(TopAlignedSpan(), 0, 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        textSpan.setSpan(TopAlignedSpan(), valueString.length - 3, valueString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.includeHeader.textBalanceUsd.text = textSpan
    }

    private fun setZecValue(value: Double) {
        binding.includeHeader.textBalanceZec.text = if(value == 0.0) "0" else String.format("%.3f",value)


//        // bugfix: there is a bug in motionlayout that causes text to flicker as it is resized because the last character doesn't fit. Padding both sides with a thin space works around this bug.
//        val hairSpace = "\u200A"
//        val adjustedValue = "$hairSpace$valueString$hairSpace"
//        text_balance_zec.text = adjustedValue
    }

    /**
     * Called whenever the content has been refreshed on the screen. When it is time to show and hide things.
     * If the balance goes to zero, the wallet is now empty so show the empty view.
     * If the balance changes from zero, the wallet is no longer empty so hide the empty view.
     * But don't do either of these things if the situation has not changed.
     */
    private fun onContentRefreshComplete(value: Double) {
        val isEmpty = value <= 0.0
        // wasEmpty isn't enough info. it must be considered along with whether these views were ever initialized
        val wasEmpty = binding.includeContent.groupEmptyViewItems.visibility == View.VISIBLE
        // situation has changed when we weren't initialized but now we have a balance or emptiness has changed
        val situationHasChanged = !viewsInitialized || (isEmpty != wasEmpty)

        Log.e("TWIG-t", "updateEmptyViews called with value: $value  initialized: $viewsInitialized  isEmpty: $isEmpty  wasEmpty: $wasEmpty")
        if (situationHasChanged) {
            Log.e("TWIG-t", "The situation has changed! toggling views!")
            setContentViewShown(!isEmpty)
            if (!isEmpty) setFirstRunShown(false)
        }

        setRefreshAnimationPlaying(false).also { Log.e("TWIG-a", "refresh false from onContentRefreshComplete") }
        binding.includeHeader.containerHomeHeader.visibility = View.VISIBLE
    }

    private fun onActiveTransactionTransitionStart() {
        binding.includeContent.buttonActiveTransactionCancel.visibility = View.INVISIBLE
    }

    private fun onActiveTransactionTransitionEnd() {
        // TODO: investigate if this fix is still required after getting transition animation working again
        // fixes a bug where the translation gets lost, during animation. As a nice side effect, visually, it makes the view appear to settle in to position
        binding.includeContent.headerActiveTransaction.translationZ = 10.0f
        binding.includeContent.buttonActiveTransactionCancel.apply {
            postDelayed({text  = "cancel"}, 50L)
            visibility = View.VISIBLE
        }
    }

    private fun setTransactionActive(transaction: ActiveTransaction, isActive: Boolean) {
        // TODO: get view for transaction, mostly likely keep a sparse array of these or something
        if (isActive) {
            binding.includeContent.buttonActiveTransactionCancel.setText(R.string.cancel)
            binding.includeContent.buttonActiveTransactionCancel.isEnabled = true
            binding.includeContent.buttonActiveTransactionCancel.tag = transaction
            binding.includeContent.headerActiveTransaction.animate().apply {
                translationZ(10f)
                duration = 200L
                interpolator = DecelerateInterpolator()
            }
        } else {
            binding.includeContent.buttonActiveTransactionCancel.setText(R.string.cancelled)
            binding.includeContent.buttonActiveTransactionCancel.isEnabled = false
            binding.includeContent.buttonActiveTransactionCancel.tag = null
            binding.includeContent.headerActiveTransaction.animate().apply {
                translationZ(2f)
                duration = 300L
                interpolator = AccelerateInterpolator()
            }
            binding.includeContent.lottieActiveTransaction.cancelAnimation()
        }
    }


    /**
     * Defines the basic properties of each FAB button for use while initializing the FAB
     */
    enum class HomeFab(
        @IdRes val id:Int,
        @DrawableRes val icon:Int,
        @ColorRes val bgColor:Int,
        @StringRes val label:Int,
        @IdRes val destination:Int
    ) {
        /* ordered by when they need to be added to the speed dial (i.e. reverse display order) */
        REQUEST(
            R.id.fab_request,
            R.drawable.ic_receipt_24dp,
            R.color.icon_request,
            R.string.destination_menu_label_request,
            R.id.nav_request_fragment
        ),
        RECEIVE(
            R.id.fab_receive,
            R.drawable.ic_qrcode_24dp,
            R.color.icon_receive,
            R.string.destination_menu_label_receive,
            R.id.nav_receive_fragment
        ),
        SEND(
            R.id.fab_send,
            R.drawable.ic_menu_send,
            R.color.icon_send,
            R.string.destination_menu_label_send,
            R.id.nav_send_fragment
        );

        companion object {
            fun fromId(id: Int): HomeFab? = values().firstOrNull { it.id == id }
        }
    }
//
//
//
//// ---------------------------------------------------------------------------------------------------------------------
//// TODO: Delete these test functions
//// ---------------------------------------------------------------------------------------------------------------------
//
    var empty = false
    val delay = 50L
    lateinit var headerEmptyViews: Array<View>
    lateinit var headerFullViews: Array<View>

    fun shrink(): Double {
        return binding.includeHeader.textBalanceZec.text.toString().trim().toDouble() - Random.nextDouble(5.0)
    }
    fun grow(): Double {
        return binding.includeHeader.textBalanceZec.text.toString().trim().toDouble() + Random.nextDouble(5.0)
    }
    fun reduceValue() {
        shrink().let {
            if(it < 0) { setZecValue(0.0); toggleViews(empty); forceRedraw() }
            else view?.postDelayed({
                setZecValue(it)
                setUsdValue(it*75.0)
                reduceValue()
            }, delay)
        }
    }
    fun increaseValue(target: Double) {
        grow().let {
            if(it > target) { setZecValue(target); setUsdValue(target*75.0); toggleViews(empty) }
            else view?.postDelayed({
                setZecValue(it)
                setUsdValue(it*75.0)
                increaseValue(target)
                if (headerFullViews[0].parent == null || headerEmptyViews[0].parent != null) toggleViews(false)
                forceRedraw()
            }, delay)
        }
    }
    fun forceRedraw() {
        view?.postDelayed({
            binding.includeHeader.containerHomeHeader.progress = binding.includeHeader.containerHomeHeader.progress - 0.1f
        }, delay * 2)
    }
    internal fun toggle(isEmpty: Boolean) {
        toggleValues(isEmpty)
    }

    internal fun toggleViews(isEmpty: Boolean) {
        Log.e("TWIG-t", "toggling views to isEmpty == $isEmpty")
        var action: () -> Unit
        if (isEmpty) {
            action = {
                binding.includeContent.groupEmptyViewItems.visibility = View.VISIBLE
                binding.includeContent.groupContentViewItems.visibility = View.GONE
                headerFullViews.forEach { binding.includeHeader.containerHomeHeader.removeView(it) }
                headerEmptyViews.forEach {
                    tryIgnore {
                        binding.includeHeader.containerHomeHeader.addView(it)
                    }
                }
            }
        } else {
            action = {
                binding.includeContent.groupEmptyViewItems.visibility = View.GONE
                binding.includeContent.groupContentViewItems.visibility = View.VISIBLE
                headerEmptyViews.forEach { binding.includeHeader.containerHomeHeader.removeView(it) }
                headerFullViews.forEach {
                    tryIgnore {
                        binding.includeHeader.containerHomeHeader.addView(it)
                    }
                }
            }
        }
        view?.postDelayed({
            binding.includeHeader.containerHomeHeader.visibility = View.VISIBLE
            action()
            viewsInitialized = true
        }, delay)
        // TODO: the motion layout does not begin in the  right state for some reason. Debug this later.
        view?.postDelayed(::forceRedraw, delay * 2)
    }

    // TODO: get rid of all of this and consider two different fragments for the header, instead
    internal fun toggleViews2(isEmpty: Boolean) {
        var action: () -> Unit
        if (isEmpty) {
            action = {
//                group_empty_view_items.visibility = View.VISIBLE
//                group_full_view_items.visibility = View.GONE
                headerFullViews.forEach { binding.includeHeader.containerHomeHeader.removeView(it) }
                headerEmptyViews.forEach {
                    tryIgnore {
                        binding.includeHeader.containerHomeHeader.addView(it)
                    }
                }
            }
        } else {
            action = {
//                group_empty_view_items.visibility = View.GONE
//                group_full_view_items.visibility = View.VISIBLE
                headerEmptyViews.forEach { binding.includeHeader.containerHomeHeader.removeView(it) }
                headerFullViews.forEach {
                    tryIgnore {
                        binding.includeHeader.containerHomeHeader.addView(it)
                    }
                }
            }
        }
        view?.postDelayed({
            action()
            viewsInitialized = true
        }, delay)
        // TODO: the motion layout does not begin in the  right state for some reason. Debug this later.
        view?.postDelayed(::forceRedraw, delay * 2)
    }

    internal fun toggleValues(isEmpty: Boolean) {
        empty = isEmpty
        if(empty) {
            reduceValue()
        } else {
            increaseValue(Random.nextDouble(20.0, 100.0))
        }
    }


    inner class HomeTransitionListener : Transition.TransitionListener {
        override fun onTransitionStart(transition: Transition) {
        }

        override fun onTransitionEnd(transition: Transition) {
        }

        override fun onTransitionResume(transition: Transition) {}
        override fun onTransitionPause(transition: Transition) {}
        override fun onTransitionCancel(transition: Transition) {}
    }
}

@Module
abstract class HomeFragmentModule {
    @ContributesAndroidInjector
    abstract fun contributeHomeFragment(): HomeFragment
}


