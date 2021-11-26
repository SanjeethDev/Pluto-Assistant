package com.example.plutoassistant.assistant

import android.Manifest
import android.animation.Animator
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.camera2.CameraManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.RingtoneManager.TYPE_RINGTONE
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import android.util.Log
import android.util.TypedValue
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.example.plutoassistant.R
import com.example.plutoassistant.data.AssistantDatabase
import com.example.plutoassistant.databinding.ActivityAssistantBinding
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max
import android.os.Vibrator
import android.view.*
import android.content.pm.ApplicationInfo

import android.content.pm.PackageInfo
import android.provider.ContactsContract
import androidx.core.app.ActivityCompat


class AssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAssistantBinding
    private lateinit var assistantViewModel: AssistantViewModel
    private lateinit var textToSpeech: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var keeper: String

    private var REQUESTCALL = 1
    private var SENDSMS = 2
    private var READSMS = 3
    private var SHAREFILE = 4
    private var SHARETEXTFILE = 5
    private var READCONTACTS = 6
    private var CAPTUREPHOTO = 7

    private var REQUEST_CODE_SELECT_DOC: Int = 100
    private var REQUEST_ENABLE_B = 1000
    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private lateinit var cameraManger: CameraManager
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var cameraID: String
    private lateinit var ringtone: Ringtone
    private var imageIndex: Int = 0
    private lateinit var imgUri: Uri

    private val logtts = "TTS"
    private val logsr = "SR"
    private val logkeeper = "keeper"

    @Suppress("DEPRECATION")
    private val imageirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/assistant/"


    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("LastOne", "Opened Activity")
        overridePendingTransition(R.anim.non_movable, R.anim.non_movable)
        binding = DataBindingUtil.setContentView(this, R.layout.activity_assistant)
        Log.d("LastOne", "Layout Works")
        installedApps()
        val application = requireNotNull(this).application
        val dataSource = AssistantDatabase.getInstance(application).assistantDao
        val viewModelFactory = AssistantViewModelFactory(dataSource, application)

        assistantViewModel =
            ViewModelProvider(this, viewModelFactory).get(AssistantViewModel::class.java)
        val adapter = AssistantAdapter()
        binding.recyclerview.adapter = adapter
        assistantViewModel.messages.observe(this, {
            it?.let {
                adapter.data = it
            }
        })
        binding.lifecycleOwner = this
        Log.d("LastOne", "Binding, lifecycleowner")
        if (savedInstanceState == null) {
            binding.assistantConstraintLayout.visibility = View.INVISIBLE
            val viewTreeObserver: ViewTreeObserver =
                binding.assistantConstraintLayout.viewTreeObserver
            Log.d("LastOne", "Null savedInstanceState")
            if (viewTreeObserver.isAlive) {
                Log.d("LastOne", "3Obs isAlive")
                viewTreeObserver.addOnGlobalLayoutListener(object : OnGlobalLayoutListener {
                    override fun onGlobalLayout() {
                        Log.d("LastOne", "onGlobalLayout")
                        circularRevealActivity()
                        Log.d("LastOne", "After circularReveal")
                        binding.assistantConstraintLayout.viewTreeObserver.removeOnGlobalLayoutListener(this)
                        Log.d("LastOne", "removeOGLL")
                    }
                })
            }
        }
        cameraManger = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            cameraID = cameraManger.cameraIdList[0]
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
        clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        ringtone = RingtoneManager.getRingtone(
            applicationContext,
            RingtoneManager.getDefaultUri(TYPE_RINGTONE)
        )
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result: Int = textToSpeech.setLanguage(Locale.ENGLISH)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(logtts, "Language not supported")
                } else {
                    Log.e(logtts, "Language supported")
                }
            } else {
                Log.e(logtts, "Initialization failed")
            }
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p0: Bundle?) {

            }

            override fun onBeginningOfSpeech() {
                Log.d("SR", "started")
            }

            override fun onRmsChanged(p0: Float) {

            }

            override fun onBufferReceived(p0: ByteArray?) {

            }

            override fun onEndOfSpeech() {
                Log.d("SR", "ended")
            }

            override fun onError(p0: Int) {

            }

            @RequiresApi(Build.VERSION_CODES.O)
            override fun onResults(bundle: Bundle?) {
                val data = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (data != null) {
                    keeper = data[0]
                    Log.d(logkeeper, keeper)
                    when {
                        keeper.contains("explain yourself") -> explain()
                        keeper.contains("thanks") -> speak("Your Welcome!")
                        keeper.contains("clear history") -> assistantViewModel.onClear()
                        keeper.contains("date") -> getDate()
                        keeper.contains("time") -> getTime()
                        keeper.contains("call") -> call()
                        keeper.contains("send message") -> sendSMS()
                        keeper.contains("read messages") -> readSMS()
                        keeper.contains("open") -> openApp(keeper)
                        keeper.contains("exit") -> exitapp()
//                        keeper.contains("what's")
//                                && keeper.contains("plus")
//                                || keeper.contains("+")
//                        -> calculate(keeper)

//                        keeper.contains("share") -> share()
//                        keeper.contains("bluetooth") -> bluetooth(keeper)
//                        keeper.contains("flashlight") -> flashlight(keeper)
//                        keeper.contains("clipboard") -> clipboard(keeper)
//                        keeper.contains("capture a") -> camera(keeper)
//                        keeper.contains("ringtone") -> ringtoneFuncs()
//                        keeper.contains("alarm") -> alarm()
                        keeper.contains("hello") || keeper.contains("hi") || keeper.contains("hey") -> speak(
                            "what do you want now?"
                        )
                        else -> speak("what?!")


                    }
                }
            }

            override fun onPartialResults(p0: Bundle?) {
                TODO("Not yet implemented")
            }

            override fun onEvent(p0: Int, p1: Bundle?) {
                TODO("Not yet implemented")
            }
        })

        binding.assistantActionButton.setOnTouchListener { view, motionEvent: MotionEvent ->

            when (motionEvent.action) {

                MotionEvent.ACTION_UP -> {
                    val vb = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    vb.cancel()
                    Log.d("LastOne", "stopListening0")
                    speechRecognizer.stopListening()
                    Log.d("LastOne", "stopListening1")
                }

                MotionEvent.ACTION_DOWN -> {
                    val vb = getSystemService(VIBRATOR_SERVICE) as Vibrator
                    val pattern = longArrayOf(0, 400)
                    installedApps()
                    Log.d("LastOne", "startListening0")
                    textToSpeech.stop()
                    Log.d("LastOne", "startListening1")
                    speechRecognizer.startListening((recognizerIntent))
                    vb.vibrate(pattern, 1)
                    Log.d("LastOne", "startListening2")
                }
            }
            false
        }
        checkIfSpeechRecognizerAvailable()
    }

    private fun exitapp() {

    }

    private fun explain() {
        speak("""I am an Android Assistant who is not that polite compared to my competitors. 
            To tell the truth, I lack much of the features. 
            Now stop bothering me!""".trimIndent())
    }

    private fun checkIfSpeechRecognizerAvailable() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.d(logsr, "yes")
        } else {
            Log.d(logsr, "no")
        }
    }

    fun speak(text: String) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, "")
        assistantViewModel.sendMessageToDatabase(keeper, text)
    }

    fun getDate() {
        val calender = Calendar.getInstance()
        val formattedDate = DateFormat.getDateInstance(DateFormat.FULL).format(calender.time)
        val splitDate = formattedDate.split(",").toTypedArray()
        val date = splitDate[1].trim {it <= ' '}
        speak("Its $date")
    }

    @SuppressLint("SimpleDateFormat")
    fun getTime() {
        val calendar = Calendar.getInstance()
        val format1 = SimpleDateFormat("hh:mm")
        val time: String = format1.format(calendar.time)
        speak("Its $time" )
    }

    private fun phoneCall() {
        val keeperSplit = keeper.replace(" ".toRegex(), "").split("o").toTypedArray()
        val number = keeperSplit[2]

        if (number.trim{it <= ' '}.length > 0) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
            {
                requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUESTCALL)
            } else {
                val dial = "tel: $number"
                speak("Calling $number")
                startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dial)))
            }
        } else {
            Toast.makeText(this, "Enter valid number", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("Recycle")
    private fun call() {
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(this, arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS), READCONTACTS)
        } else {
            val name = keeper.split("call").toTypedArray()[1].trim{
                it <= ' '
            }
            Log.d("chk", name)
            try {
                val cursor = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.TYPE
                ), "DISPLAY_NAME='$name'", null, null)
                cursor!!.moveToFirst()
                val number = cursor.getString(0)
                if(number.trim { it <= ' ' }.length > 0) {
                    if(ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUESTCALL)
                    } else {
                        val dial = "tel:$number"
                        startActivity(Intent(Intent.ACTION_CALL, Uri.parse(dial)))
                    }
                } else {
                    Toast.makeText(this, "Enter Phone Number", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                speak("Something went wrong...As expected of you.")
            }
        }
    }

    private fun sendSMS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SENDSMS)
        } else {
            val keeperReplaced = keeper.replace(" ".toRegex(),"")
            val number = keeperReplaced.split("o").toTypedArray()[1].split("t").toTypedArray()[0]
            val message = keeper.split("that").toTypedArray()[1]
            Log.d("chk", number+message)
            val mySmsManager = SmsManager.getDefault()
            mySmsManager.sendTextMessage(number.trim{it <= ' '}, null, message.trim{it <= ' '}, null, null)
            speak("Message Sent.")
        }
    }

    @SuppressLint("Recycle")
    @RequiresApi(Build.VERSION_CODES.O)
    private fun readSMS() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(this, arrayOf(Manifest.permission.READ_SMS), READSMS)
        } else {
            val cursor = contentResolver.query(Uri.parse("content://sms"), null, null, null)
            cursor!!.moveToFirst()
            speak("Recent Message. " + cursor.getString(12))
        }
    }

    fun installedApps() {
        val packList = packageManager.getInstalledPackages(0)
        for (i in packList.indices) {
            val packInfo = packList[i]
            if (packInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM == 0) {
                val appName = packInfo.applicationInfo.loadLabel(packageManager).toString()
                Log.e("Appli12" + Integer.toString(i), appName)
            } else {
                val appName = packInfo.applicationInfo.loadLabel(packageManager).toString()
                Log.e("Appli123" + Integer.toString(i), appName)
            }

        }
    }

    private fun openApp(str: String) {
        val result: String = str.replace("open ".toRegex(), "")
            .lowercase(Locale.getDefault())
        val packList = packageManager.getInstalledPackages(0)
        for (i in packList.indices) {
            val packInfo = packList[i]
            val appName = packInfo.applicationInfo.loadLabel(packageManager).toString().lowercase(Locale.getDefault())
            Log.e("AppNameWorks", packInfo.packageName)
            if (appName.contains(result)) {
                val vb = getSystemService(VIBRATOR_SERVICE) as Vibrator
                vb.cancel()
                val intent = packageManager.getLaunchIntentForPackage(packInfo.packageName.toString())
                intent?.let { startActivity(it) }
            }
        }


        speak("Opening $result")
//
    }


    private fun circularRevealActivity() {
        val cx: Int = binding.assistantConstraintLayout.right - getDips(44)
        val cy: Int = binding.assistantConstraintLayout.bottom - getDips(44)
        val finalRadius: Int = max(
            binding.assistantConstraintLayout.width,
            binding.assistantConstraintLayout.height,
        )
        val circularReveal = ViewAnimationUtils.createCircularReveal(
            binding.assistantConstraintLayout,
            cx,
            cy,
            0f,
            finalRadius.toFloat()
        )
        circularReveal.duration - 1250
        binding.assistantConstraintLayout.visibility = View.VISIBLE
        circularReveal.start()
    }

    private fun getDips(dps: Int): Int{
        val resources: Resources = resources
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dps.toFloat(),
            resources.displayMetrics
        ).toInt()

    }

    override fun onBackPressed() {
        val cx: Int = binding.assistantConstraintLayout.width - getDips(44)
        val cy: Int = binding.assistantConstraintLayout.height - getDips(44)

        val finalRadius: Int = max(
            binding.assistantConstraintLayout.width,
            binding.assistantConstraintLayout.height
        )

        val circularReveal = ViewAnimationUtils.createCircularReveal(binding.assistantConstraintLayout, cx, cy, finalRadius.toFloat(), 0f)
        circularReveal.addListener(object: Animator.AnimatorListener{
            override fun onAnimationStart(p0: Animator?) {
                TODO("Not yet implemented")
            }

            override fun onAnimationEnd(p0: Animator?) {
                binding.assistantConstraintLayout.visibility = View.INVISIBLE
                finish()
            }

            override fun onAnimationCancel(p0: Animator?) {
                TODO("Not yet implemented")
            }

            override fun onAnimationRepeat(p0: Animator?) {
                TODO("Not yet implemented")
            }

        })

        circularReveal.duration = 1250
        circularReveal.start()

    }

    override fun onDestroy() {
        super.onDestroy()
        textToSpeech.stop()
        textToSpeech.shutdown()
        speechRecognizer.cancel()
        speechRecognizer.destroy()
    }

}