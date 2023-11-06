package org.hyperledger.ariesproject

import android.app.Application
import android.content.res.Configuration
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.hyperledger.ariesframework.agent.Agent
import org.hyperledger.ariesframework.agent.AgentConfig
import org.hyperledger.ariesframework.agent.MediatorPickupStrategy
import org.hyperledger.ariesframework.credentials.models.AutoAcceptCredential
import org.hyperledger.ariesframework.proofs.models.AutoAcceptProof
import org.hyperledger.ariesframework.wallet.Wallet
import org.hyperledger.ariesproject.api.JSONServer
import retrofit2.Retrofit
import retrofit2.awaitResponse
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File

const val PREFERENCE_NAME = "aries-framework-kotlin-sample"
const val genesisPath = "test-genesis.txn"

class WalletApp : Application() {
    lateinit var agent: Agent
    var walletOpened: Boolean = false

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("http://es6.kr/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(OkHttpClient())
        .build()
    val jsonServer: JSONServer = retrofit.create(JSONServer::class.java)

    private fun copyResourceFile(resource: String) {
        val inputStream = applicationContext.assets.open(resource)
        val file = File(applicationContext.filesDir.absolutePath, resource)
        if (!file.exists()) {
            file.outputStream().use { inputStream.copyTo(it) }
        }
    }

    private suspend fun openWallet(invitationUrl: String) {
        val pref = applicationContext.getSharedPreferences(PREFERENCE_NAME, 0)
        var key = pref.getString("walletKey", null)

        if (key == null) {
            key = Wallet.generateKey()
            pref.edit().putString("walletKey", key).apply()
            copyResourceFile(genesisPath)
        }

        // val invitationUrl = URL("http://10.0.2.2:3001/invitation").readText() // This uses local AFJ mediator and needs MediatorPickupStrategy.PickUpV1
        val config = AgentConfig(
            walletKey = key,
            genesisPath = File(applicationContext.filesDir.absolutePath, genesisPath).absolutePath,
            mediatorConnectionsInvite = invitationUrl,
            mediatorPickupStrategy = MediatorPickupStrategy.Implicit,
            label = "SampleApp",
            autoAcceptCredential = AutoAcceptCredential.Always,
            autoAcceptProof = AutoAcceptProof.Always,
        )
        agent = Agent(applicationContext, config)
        agent.initialize()

        walletOpened = true
        Log.d("demo", "Agent initialized")

        val url = jsonServer.url("INVITATION_URL").awaitResponse().body()!!.value
        val (_, connection) = agent.oob.receiveInvitationFromUrl(url)
        Log.d("demo", "Connected to ${connection?.theirLabel ?: "unknown agent"}")
    }

    override fun onCreate() {
        super.onCreate()

        GlobalScope.launch(Dispatchers.IO) {
            val res = jsonServer.url("MEDIATOR_URL").awaitResponse()
            openWallet(res.body()!!.value)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onLowMemory() {
        super.onLowMemory()
    }
}
