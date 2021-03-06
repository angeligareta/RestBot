package com.example.restbot

import ai.api.AIConfiguration
import ai.api.AIListener
import ai.api.AIServiceContextBuilder
import ai.api.android.AIDataService
import ai.api.android.AIService
import ai.api.model.AIError
import ai.api.model.AIRequest
import ai.api.model.AIResponse
import ai.api.model.Result
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import com.example.restbot.asynctasks.AIRequestTask
import com.example.restbot.handlers.*
import com.google.gson.JsonElement
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity(), AIListener {

    private val TAG = "MainActivity"
    private val WELCOME_MESSAGE = "Comienze con 'hola'"
    private val REQUEST = 200

    private lateinit var messageAdapter: MessageAdapter

    private lateinit var mMessagesView: ListView
    private lateinit var mInputText: EditText
    private lateinit var mListenButton: ImageButton
    private lateinit var mSendButton: ImageButton

    private var voiceMode:  Boolean = false

    /**
     * Initialization of the UI components, speaker and messageAdapter.
     * Besides, it checks the permissions and configure the assistant.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mListenButton = findViewById(R.id.bt_listen)
        mSendButton = findViewById(R.id.bt_send)
        mInputText = findViewById(R.id.input_text)
        mMessagesView = findViewById(R.id.messages_view)

        // Initialize the speaker that will speak the query results
        SpeakerHandler.setContext(this)

        // Assign the message view adapter to an instance of the MessageAdapter class
        messageAdapter = MessageAdapter(this)
        mMessagesView.adapter = messageAdapter

        // Execute handlers
        KeyHandler.setContext(this)
        RemoteDatabaseHandler
        LocalDatabaseHandler // This executes init in object

        checkPermissions()
        configureAssistant()
    }

    /**
     * Check if the user has the correct permissions. If not, ask for them.
     */
    private fun checkPermissions() {
        // Versions higher than Lollipop requires permission to be accepted
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            askForPermission()
        }
    }

    /**
     * Method that ask for audio permission if the user has not accepted it
     * or if is using a version higher than Lollipop.
     */
    private fun askForPermission() {
        if (ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST)
        }
    }

    /**
     * Method that configures the assistant according to
     * https://github.com/dialogflow/dialogflow-android-client#running_sample
     */
    private fun configureAssistant() {
        Log.d("MainActivity", "Configuring...")

        // Create an instance of AIConfiguration, specifying the access token, locale, and recognition engine.
        val config = ai.api.android.AIConfiguration(KeyHandler.ACCESS_TOKEN,
                AIConfiguration.SupportedLanguages.Spanish,
                ai.api.android.AIConfiguration.RecognitionEngine.System)

        /** Configuration of DialogFlow by using voice requests. */
        // Use the AIConfiguration object to get a reference to the AIService, which will make the query requests.
        val aiService = AIService.getService(applicationContext, config)

        // Set the AIListener instance for the AIService instance.
        aiService.setListener(this)

        // Set the button as listener
        mListenButton.setOnClickListener {
            SpeakerHandler.stop()
            aiService.startListening()
        }

        /** Configuration of DialogFlow by using text requests. */
        val aiDataService = AIDataService(this, config)
        val customAIServiceContext = AIServiceContextBuilder.buildFromSessionId(REQUEST.toString())
        val aiRequest = AIRequest()

        // Assign to the send button the action of that every time it is clicked, it takes the text from
        // the input edit text and make a new a query asynchronously
        mSendButton.setOnClickListener {
            val query = mInputText.text.toString()
            mInputText.text.clear()
            if (voiceMode) {
                voiceMode = false
            }

            sendMessage(query, false)

            aiRequest.setQuery(query)
            AIRequestTask(this, aiDataService, customAIServiceContext).execute(aiRequest)
        }

        // Send the welcome_message to start
        mInputText.hint = WELCOME_MESSAGE
        //aiRequest.setQuery(WELCOME_MESSAGE)
        // AIRequestTask(this, aiDataService, customAIServiceContext).execute(aiRequest)
    }

    /**
     * Method that is invoked when the assistant hears something
     */
    override fun onResult(response: AIResponse?) {
        val result = response?.result

        // Assign query and query response to the cardviews in the layout.
        val query = result?.resolvedQuery
        val queryResponse = result?.fulfillment?.speech

        if (query != null && queryResponse != null) {
            if (!voiceMode) {
                voiceMode = true
            }

            sendMessage(query, false)
            sendMessage(queryResponse, true)
        }

        handleIntent(result)
    }

    /**
     * Method that is called when an aiRequest has an aiResponse.
     * It adds the message to the list view as incoming.
     */
    fun requestCallback(aiResponse: AIResponse?) {
        val queryResponse = aiResponse?.result?.fulfillment?.speech

        if (queryResponse != null) {
            sendMessage(queryResponse, true)
            handleIntent(aiResponse?.result)
        }
    }

    /**
     * Handle intent with IntentHandler object.
     */
    private fun handleIntent(result: Result?) {
        // Get the intent name and parameters and let IntentHandler handle it.
        val intentName = result?.metadata?.intentName
        val intentParameters = if (result != null) result.parameters else HashMap<String, JsonElement>()
        IntentHandler.handleIntent(this, intentName, intentParameters)
    }

    /**
     * Method that add a new message to the message adapter and hence to the message list view.
     */
    fun sendMessage(text: String, incomingMessage: Boolean, numberOfMessagesBefore: Int = 0) {
        val message = Message(text, incomingMessage)

        if (input_text.hint == WELCOME_MESSAGE) {
            mInputText.hint = resources.getString(R.string.input_message_hint)
        }

        if (voiceMode && incomingMessage) {
            SpeakerHandler.play(text)
        }

        // Add message and scroll the ListView to the last added element
        runOnUiThread {
            messageAdapter.addMessage(message)
            mMessagesView.setSelection(mMessagesView.count - 1 - numberOfMessagesBefore)
        }
    }

    override fun onStop() {
        super.onStop()
        SpeakerHandler.stop()
    }

    /**
     * Method not implemented of interface AIListener
     */
    override fun onError(error: AIError?) {
        Log.e(TAG, error.toString())
    }

    /**
     * Method not implemented of interface AIListener
     */
    override fun onListeningStarted() {}

    /**
     * Method not implemented of interface AIListener
     */
    override fun onAudioLevel(level: Float) {}

    /**
     * Method not implemented of interface AIListener
     */
    override fun onListeningCanceled() {}

    /**
     * Method not implemented of interface AIListener
     */
    override fun onListeningFinished() {}

}
