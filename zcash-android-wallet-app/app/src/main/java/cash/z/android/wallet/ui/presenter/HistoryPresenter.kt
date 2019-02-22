package cash.z.android.wallet.ui.presenter

import android.util.Log
import cash.z.android.wallet.ui.fragment.HistoryFragment
import cash.z.android.wallet.ui.presenter.Presenter.PresenterView
import cash.z.wallet.sdk.dao.WalletTransaction
import cash.z.wallet.sdk.data.Synchronizer
import cash.z.wallet.sdk.data.twig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class HistoryPresenter @Inject constructor(
    private val view: HistoryFragment,
    private var synchronizer: Synchronizer
) : Presenter, CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext get() = Dispatchers.Main + job

    interface HistoryView : PresenterView {
        fun setTransactions(transactions: List<WalletTransaction>)
    }

    override suspend fun start() {
        Log.e("@TWIG", "historyPresenter starting!")
        launchTransactionBinder(synchronizer.allTransactions())
    }

    override fun stop() {
        Log.e("@TWIG", "historyPresenter stopping!")
        job.cancel()
    }

    private fun CoroutineScope.launchTransactionBinder(channel: ReceiveChannel<List<WalletTransaction>>) = launch {
        Log.e("@TWIG", "transaction binder starting!")
        for (walletTransactionList in channel) {
            Log.e("@TWIG", "received ${walletTransactionList.size} transactions for presenting")
            bind(walletTransactionList)
        }
        Log.e("@TWIG", "transaction binder exiting!")
    }


    //
    // View Callbacks on Main Thread
    //

    private fun bind(transactions: List<WalletTransaction>) {
        twig("binding ${transactions.size} walletTransactions")
        view.setTransactions(transactions.sortedByDescending { it.timeInSeconds })
    }

}

