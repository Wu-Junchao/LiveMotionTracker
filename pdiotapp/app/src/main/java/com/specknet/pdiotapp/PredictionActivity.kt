package com.specknet.pdiotapp


import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.AssetManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.FloatMath
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.specknet.pdiotapp.utils.Constants
import com.specknet.pdiotapp.utils.RESpeckLiveData
import com.specknet.pdiotapp.utils.ThingyLiveData
import org.tensorflow.lite.Interpreter
import org.w3c.dom.Text
import java.io.BufferedReader
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.time.LocalDate
import kotlin.math.log

class PredictionActivity : AppCompatActivity() {

    lateinit var respeckReceiver: BroadcastReceiver
    lateinit var thingyReceiver: BroadcastReceiver
    lateinit var respeckLooper: Looper
    lateinit var thingyLooper: Looper
    var RespkLive:RESpeckLiveData? = null
    var ThingLive:ThingyLiveData?= null
    var respeckOn = false
    var thingyOn = false
    val respeckFilterTest = IntentFilter(Constants.ACTION_RESPECK_LIVE_BROADCAST)
    val thingyFilterTest = IntentFilter(Constants.ACTION_THINGY_BROADCAST)

    lateinit var todayDate: String
    var stepCount = 0
    var stepCountInCurrLive=0
    var motionCount = IntArray(14)
    var doRecord = false
    var fileName = ""

    private lateinit var interpreter: Interpreter
    private lateinit var interpreterDual: Interpreter
    private val mModelPath = "demo1_25_single.tflite"
    private val mModelPathDual = "demo1_25_dual.tflite"

    var imuArray = emptyArray<FloatArray>();
    var thingyArray = emptyArray<FloatArray>();
    var imuCount = 0;
    var onlinePredict = 0;  // 0 to false, 1 to respeck only, 2 to enable both.

    var imuStepCountingArray = emptyArray<FloatArray>();
    var thingyStepCountingArray = emptyArray<FloatArray>();
    var imuStepCountingDataCount = 0;
    var onlineCountSteps = 0;
    var meanArray = emptyArray<Float>();
    var globalMean = 0.0f;

    private lateinit var resultText : TextView
    private lateinit var liveButton : Button
    private lateinit var checkButton : Button
    private lateinit var liveBothSensorButton : Button
    private lateinit var switchGuide : TextView
    private lateinit var StepCountText : TextView


    private fun argMaxNth(ls:FloatArray, n:Int): Int {
        // get the index of Nth largest value in array
        val listIndex= (0..ls.size-1).toList()
        var zipLs=(listIndex zip ls.toList())
        zipLs=zipLs.sortedBy {it.second }.reversed()
        var temp= zipLs.map{it.first}
        return temp[n]
    }

    private fun showResult(result:FloatArray){
        // format the results and display on screen
        var str=""
        for (i in 0..2){
            val tempindex=argMaxNth(result,i)
            if (i==0){
                motionCount[tempindex]+=1
            }
            var className = toClassName(tempindex)
            Log.d("wu",className)
            str=str.plus((i+1).toString() + ": " + className+",\nProbability: "+String.format("%.2f", result[tempindex]*100)+"%.\n\n")
        }
        Log.d("wu",str)
        resultText.text = str
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_predicting)

        resultText = findViewById(R.id.txtResult)
        resultText.text = "Press Live button to start..."
        checkButton = findViewById(R.id.btnPredict)
        liveButton = findViewById(R.id.livebutton)
        liveBothSensorButton = findViewById(R.id.stop_live)
        switchGuide=findViewById(R.id.switchguide)
        StepCountText = findViewById(R.id.stepCount)

        val username = intent.getStringExtra("username").toString()
        // record only if the user logged in
        if (username != "guest"){
            doRecord =true
        }
        todayDate = LocalDate.now().toString()
        fileName = todayDate + username
        StepCountText.text = "Step count in this live period: ${stepCountInCurrLive.toString()}"
        if (doRecord){
            // initialize the file, read content if it is not empty, write to variables
            this.openFileOutput(fileName, Context.MODE_APPEND).use{
                it.write("".toByteArray())
            }
            val allContent = this.openFileInput(fileName).bufferedReader().use(BufferedReader::readText)
            if (allContent.isNotEmpty()){
                val allCount = allContent.split(".")
                Log.d("wu",allContent)
                for (i in 0..13){
                    motionCount[i] = allCount[i].toInt()
                }
                stepCount=allCount[14].toInt()
            }
        }



        Log.d("wu",fileName)

        checkButton.setOnClickListener {
            // only for testing, not used
            Log.d("test Predict input: ","button")
            var result = doInference("imu input")
            runOnUiThread {
                showResult(result)
            }
        }

        liveButton.setOnClickListener {
            // change the values of variables for different mode
            if (onlinePredict!=0){
                StepCountText.isVisible=false
                stepCount+=stepCountInCurrLive
                stepCountInCurrLive=0
                onlineCountSteps=0
                onlinePredict=0
                liveButton.text="live"
                resultText.text="Press Live button to start..."
            }
            else {
                if (!respeckOn) {
                    Toast.makeText(this, "Respeck is not on! Check connection.", Toast.LENGTH_SHORT)
                        .show()
                    return@setOnClickListener
                } else if (thingyOn) {
                    // use two sensors
                    StepCountText.isVisible=true
                    stepCountInCurrLive=0
                    onlineCountSteps = 1
                    onlinePredict = 2
                    liveButton.text = "stop live"
                    resultText.text="Starting..."
                    switchGuide.text = "Currently using Respeck & Thingy"
                }
                else{
                    //use only respeck
                    StepCountText.isVisible=true
                    stepCountInCurrLive=0
                    onlineCountSteps = 1
                    onlinePredict = 1
                    liveButton.text = "stop live"
                    resultText.text="Starting..."
                    switchGuide.text = "Currently using Respeck only"
                }
            }
        }

        liveBothSensorButton.setOnClickListener {
            // this button is not used.
            if (onlinePredict==1 && thingyOn){
                onlinePredict=2
                switchGuide.text = "Currently using Respeck & Thingy"
            }
            else if (onlinePredict==2){
                onlinePredict=1
                switchGuide.text = "Currently using Respeck only"
            }
            else if (onlinePredict==0){
                Toast.makeText(this, "Start live first.", Toast.LENGTH_SHORT).show()
            }
            else{
                Toast.makeText(this, "Thingy is not on! Check connection.", Toast.LENGTH_SHORT).show()
            }

        }

        setupRespect()
        setupThing()
        initInterpreter()
    }

    @SuppressLint("SuspiciousIndentation")
    private fun collectStepCountingDataBag() {
        // perform step count during live recognition
        if (RespkLive!=null && onlineCountSteps == 1 && respeckOn){

            if (imuStepCountingDataCount < 50){
                imuStepCountingArray += floatArrayOf(
                    RespkLive!!.accelX, RespkLive!!.accelY, RespkLive!!.accelZ)
                imuStepCountingDataCount += 1
            } else {
                Log.d("Prediction activity", "predict")
                val result = countStepsInBag(imuStepCountingArray)
                stepCountInCurrLive += result // add to step count for this live period
                runOnUiThread {
                    StepCountText.text = "Step count in this live period: ${stepCountInCurrLive.toString()}"
                }

                imuStepCountingDataCount = 1
                imuStepCountingArray = arrayOf(floatArrayOf(
                    RespkLive!!.accelX, RespkLive!!.accelY, RespkLive!!.accelZ))
            }
        }
    }

    private fun countStepsInBag(imuData: Array<FloatArray>): Int {
        var stepCount = 0
        var magnitudeArray = emptyArray<Float>();
        for (data in imuData){
            magnitudeArray += kotlin.math.sqrt(data[0] * data[0] + data[1] * data[1] + data[2] * data[2])
        }
        var mean = magnitudeArray.sum() / magnitudeArray.size
        meanArray += mean
        globalMean = meanArray.sum() / meanArray.size
        var standardDeviation = 0.0
        var squaredDifferenceSum = 0.0
        for (magnitude in magnitudeArray){
            squaredDifferenceSum += (magnitude - globalMean) * (magnitude - globalMean)
        }
        standardDeviation = kotlin.math.sqrt(squaredDifferenceSum / (magnitudeArray.size - 1))
        for (magnitude in magnitudeArray){
            if(kotlin.math.abs(magnitude - globalMean) > standardDeviation * 2.5){
                stepCount++
            }
        }
        return stepCount
    }

    @SuppressLint("SuspiciousIndentation")
    private fun useSensors4Predicting() {
        if (RespkLive!=null && onlinePredict ==1 && respeckOn){
            // use respeck only model
            val output = RespkLive!!.phoneTimestamp.toString() + "," +
                    RespkLive!!.accelX + "," + RespkLive!!.accelY + "," + RespkLive!!.accelZ + "," +
                    RespkLive!!.gyro.x + "," + RespkLive!!.gyro.y + "," + RespkLive!!.gyro.z + "\n"

                if (imuCount < 25){
                    // store the data in one second
                    Log.d("Prediction activity", "add data to array = " + output)
                    imuArray += floatArrayOf(
                        RespkLive!!.accelX, RespkLive!!.accelY, RespkLive!!.accelZ,
                        RespkLive!!.gyro.x , RespkLive!!.gyro.y , RespkLive!!.gyro.z )
                    imuCount += 1
                } else {
                    // do the inference if collects 1s data
                    Log.d("Prediction activity", "predict")
                    val result = doInference(imuArray)
                    runOnUiThread {
                        showResult(result)
                    }
                    imuCount = 1
                    imuArray = arrayOf(floatArrayOf(
                        RespkLive!!.accelX, RespkLive!!.accelY, RespkLive!!.accelZ,
                        RespkLive!!.gyro.x , RespkLive!!.gyro.y , RespkLive!!.gyro.z ))
                }

            Log.d("Prediction activity", "useSensors4Predicting: appended to respeckoutputdata = " + output)

        }
        else if (RespkLive!=null && ThingLive!=null && onlinePredict==2 &&respeckOn && thingyOn){
            // use two sensors model
//            Toast.makeText(this, "run algorithm with two sensors", Toast.LENGTH_SHORT).show()
            val output = RespkLive!!.phoneTimestamp.toString() + "," +
                    RespkLive!!.accelX + "," + RespkLive!!.accelY + "," + RespkLive!!.accelZ + "," +
                    RespkLive!!.gyro.x + "," + RespkLive!!.gyro.y + "," + RespkLive!!.gyro.z + "\n"

            if (imuCount < 25){
                // same as above
                // store the data in one second
                Log.d("Prediction activity", "add data to array = " + output)
                imuArray += floatArrayOf(
                    RespkLive!!.accelX, RespkLive!!.accelY, RespkLive!!.accelZ,
                    RespkLive!!.gyro.x , RespkLive!!.gyro.y , RespkLive!!.gyro.z )
                imuCount += 1
                thingyArray += floatArrayOf(
                    ThingLive!!.accelX, ThingLive!!.accelY, ThingLive!!.accelZ,
                    ThingLive!!.gyro.x , ThingLive!!.gyro.y , ThingLive!!.gyro.z )
            } else {
                // do the inference if collects 1s data
                Log.d("Prediction activity", "predict")
                val result = doInferenceDual(imuArray,thingyArray)
                runOnUiThread {
                    showResult(result)
                }
                imuCount = 1
                imuArray = arrayOf(floatArrayOf(
                    RespkLive!!.accelX, RespkLive!!.accelY, RespkLive!!.accelZ,
                    RespkLive!!.gyro.x , RespkLive!!.gyro.y , RespkLive!!.gyro.z ))
                thingyArray = arrayOf(floatArrayOf(
                    ThingLive!!.accelX, ThingLive!!.accelY, ThingLive!!.accelZ,
                    ThingLive!!.gyro.x , ThingLive!!.gyro.y , ThingLive!!.gyro.z ))
               }
            }


    }

    private fun setupRespect() {
        Log.d("Prediction activity", "setting up respeck receiver")
        // register respeck receiver
        respeckReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action == Constants.ACTION_RESPECK_LIVE_BROADCAST) {
                    RespkLive = intent.getSerializableExtra(Constants.RESPECK_LIVE_DATA) as RESpeckLiveData
                    // collect data for process when receives data
                    useSensors4Predicting()
                    collectStepCountingDataBag()
                    respeckOn = true
                }else{
                    RespkLive = null
                    respeckOn=false
                }
            }
        }
        // important to set this on a background thread otherwise it will block the UI
        val respeckHandlerThread = HandlerThread("bgProcThreadRespeck")
        respeckHandlerThread.start()
        respeckLooper = respeckHandlerThread.looper
        val respeckHandler = Handler(respeckLooper)
        this.registerReceiver(respeckReceiver, respeckFilterTest, null, respeckHandler)

    }

    private  fun setupThing(){
        thingyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.action
                if (action == Constants.ACTION_THINGY_BROADCAST) {
                    ThingLive = intent.getSerializableExtra(Constants.THINGY_LIVE_DATA) as ThingyLiveData
                    thingyOn = true
                    runOnUiThread {
                        switchGuide.text="Currently using Respeck & Thingy"
                    }
                }
                else{
                    ThingLive=null
                    thingyOn=false
                    runOnUiThread {
                        switchGuide.text="Currently using Respeck only"
                    }
                }
            }
        }
        // important to set this on a background thread otherwise it will block the UI
        val thingyHandlerThread = HandlerThread("bgProcThreadThingy")
        thingyHandlerThread.start()
        thingyLooper = thingyHandlerThread.looper
        val thingyHandler = Handler(thingyLooper)
        this.registerReceiver(thingyReceiver, thingyFilterTest, null, thingyHandler)
    }
    private fun initInterpreter(){
        // initialize the models
        interpreter = Interpreter(loadModelFile(assets, mModelPath))
        interpreterDual = Interpreter(loadModelFile(assets, mModelPathDual))
    }
    private fun doInferenceDual(respeckData: Array<FloatArray>,thingyData: Array<FloatArray>): FloatArray {
        // two sensors model, do the inference
        val inputVal0 = arrayOf(respeckData)
        val inputVal1 = arrayOf(thingyData)
        val inputs = arrayOf(inputVal0, inputVal1)
        val output = Array(1) { FloatArray(14) }
        val outputs: MutableMap<Int, Any> = HashMap()
        outputs[0] = output
        interpreterDual.runForMultipleInputsOutputs(inputs, outputs)
        Log.d("Predict: ", (output[0].argmax()).toString())
//        Log.d(output[0])
        return output[0]
    }
    //testing
    private fun doInferenceDual(imuData: String): FloatArray {
        //only for test
        val inputVal0 = arrayOf(arrayOf(FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6)))
        val inputVal1 = arrayOf(arrayOf(FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6)))
//        inputVal[0] = inputString.toFloat()
        val inputs = arrayOf(inputVal0, inputVal1)
        Log.d("Predict input: ", (inputs.toString()))
        val output = Array(1) { FloatArray(14) }
        val outputs: MutableMap<Int, Any> = HashMap()
        outputs[0] = output

        interpreterDual.runForMultipleInputsOutputs(inputs, outputs)
        Log.d("Predict: ", (output[0].argmax()).toString())
        Log.d("wu",output[0].sortedArray().joinToString(" "))
        return output[0]
    }

    private fun doInference(imuData: Array<FloatArray>): FloatArray {
        // respeck only model, do the inference
        val inputVal = arrayOf(imuData)
        val output = Array(1) { FloatArray(14) }
        interpreter.run(inputVal, output)
        Log.d("Predict: ", (output[0].argmax()).toString())
//        Log.d(output[0])
        return output[0]
    }

    // for testing only
    private fun doInference(imuData: String): FloatArray {
        val inputVal = arrayOf(arrayOf(FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),
            FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6),FloatArray(6)))
//        inputVal[0] = inputString.toFloat()
        val output = Array(1) { FloatArray(14) }
        interpreter.run(inputVal, output)
        Log.d("Predict: ", (output[0].argmax()).toString())
        Log.d("wu",output[0].sortedArray().joinToString(" "))
        return output[0]
    }
    private fun toClassName(index: Int): String {
        // get corresponding activity name
        if (index == 12) {
            return  "Climbing stairs"
        } else if(index == 5){
            return  "Sitting bent backward"
        }else if (index== 8) {
            return  "Lying down on stomach"
        }else if (index == 7){
            return  "Lying down left"
        }else if (index == 0){
            return  "Sitting"
        }else if (index == 1){
            return "Walking at normal speed"
        }else if (index == 9){
            return  "Movement"
        }else if (index == 6){
            return  "Lying down right"
        }else if (index == 2) {
            return "Lying down on back"
        }else if (index == 3){
            return  "Desk work"
        }else if (index == 10) {
            return  "Standing"
        }else if (index == 4){
            return "Sitting forward"
        }else if (index == 11){
            return "Running"
        }else if (index == 13){
            return  "Descending stairs"
        }
        return  index.toString()
    }

    private fun loadModelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
    override fun onDestroy() {
        unregisterReceiver(respeckReceiver)
        unregisterReceiver(thingyReceiver)
        respeckLooper.quit()
        interpreter.close()
        interpreterDual.close()
        // write data to the file when exit this activity
        if (doRecord){
            stepCount+=stepCountInCurrLive
            var str=""
            for (i in motionCount){
                str= "$str$i."
            }
            str=str+stepCount.toString()
//            str="18.77.91.129.26.154.189.0.514.232.172.114.64.89.2918"
            this.openFileOutput(fileName, Context.MODE_PRIVATE).use{
                it.write(str.toByteArray())
                Log.d("wu",str)
            }
        }
        super.onDestroy()

    }
}

private fun FloatArray.argmax(): Int? {
    return withIndex().maxByOrNull { it.value }?.index
}
