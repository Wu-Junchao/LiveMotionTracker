package com.specknet.pdiotapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.github.mikephil.charting.utils.ViewPortHandler
import com.specknet.pdiotapp.databinding.ActivityHistoryRecordBinding
import java.io.BufferedReader
import java.text.DecimalFormat
import kotlin.math.roundToInt


private lateinit var myDateList : MutableList<String>
private lateinit var myContentList : MutableList<String>
private var isTextShow : Boolean = false
lateinit var binding:ActivityHistoryRecordBinding
private var selectedIndex = 0
private fun parseContent(content:String) : IntArray{
    var store = IntArray(15)
    val allCount = content.split(".")
    for (i in 0..13){
        store[i] = allCount[i].toInt()
    }
    store[14]=allCount[14].toInt()
    return store
}

class HistoryRecord : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryRecordBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val userName = intent.getStringExtra("username")
        this.fileList().forEach {
            Log.d("wu",it.toString())
        }
        val allFileList=this.fileList().reversed()
        myDateList = mutableListOf<String>()
        myContentList = mutableListOf<String>()
        isTextShow=false
        // search all files for this account
        for (f in allFileList){
            if (f.slice(10..f.length-1)==userName){
                var content=this.openFileInput(f).bufferedReader().use(BufferedReader::readText)
                if (content=="0.0.0.0.0.0.0.0.0.0.0.0.0.0.0"){
                    // if no valid data then skip
                    continue
                }
                else{
                    // otherwise add to list
                    myDateList.add(f.slice(0..9))
                    myContentList.add(content)
                }
            }
        }
        if (myDateList.isEmpty()){ myDateList.add("Nothing") }
        initSpinner()

        binding.switchDisplayButton.setOnClickListener {
            // switch between pie chart view and text view
            if (isTextShow){
                binding.switchDisplayButton.text = "Switch to text View"
            }
            else{
                binding.switchDisplayButton.text ="Switch to Piegraph view"
            }
            isTextShow=!isTextShow
            binding.piechart.isVisible = !isTextShow
            if (!isTextShow){
                binding.mainInfo.setTextColor(Color.parseColor("#001C4E1F"))
            }
            else{
                binding.mainInfo.setTextColor(Color.parseColor("#1C4E1F"))
            }
        }
    }

    private fun initSpinner(){
        // initialize the spinner
        var starAdapter = ArrayAdapter<String>(this,R.layout.item_select,myDateList)
        starAdapter.setDropDownViewResource(R.layout.item_dropdown)
        var sp = binding.spinner1
        sp.prompt="choose a date"
        sp.adapter=starAdapter
        sp.setSelection(0)
        sp.onItemSelectedListener=MySelectedListener()
    }

    class MySelectedListener : AdapterView.OnItemSelectedListener{
        private fun argMaxNth(ls:List<Int>, n:Int): Int {
            // format the results and display on screen
            var str=""
            val listIndex= (0..ls.size-1).toList()
            var zipLs=(listIndex zip ls)
            zipLs=zipLs.sortedBy {it.second }.reversed()
            var temp= zipLs.map{it.first}
            return temp[n]
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
        override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
            // show content for the selected date
            selectedIndex=p2
            if (myContentList.isEmpty()){
                // if there is no date available, then show nothing
                binding.mainInfo.text = "No history record yet."
                binding.spinner1.isVisible=false
                binding.switchDisplayButton.isVisible=false
                binding.stepDisplay.isVisible=false
                binding.piechart.isVisible=false
            }
            else {

                val entries: MutableList<PieEntry> = ArrayList()
                val toDisplayContent = parseContent(myContentList[p2]).copyOf()
                binding.stepDisplay.text="Step number: "+toDisplayContent[14]
                var fillIntoMainInfo = ""
                val allcount = toDisplayContent.slice(0..13).sum()
                var others = 0
                // prepare the piechart
                for (i in 0..13){
                    val index = argMaxNth(toDisplayContent.slice(0..13),i)
                    val activityName = toClassName(index)
                    if (toDisplayContent[index].toFloat()/allcount>0.04){
                        // show an independent piece of pie if the portion of an activity exceeds 4% of total time
                        entries.add(PieEntry(toDisplayContent[index].toFloat(),activityName))
                    }
                    else{
                        // otherwise add to others
                        others+=toDisplayContent[index]
                    }
                    if (toDisplayContent[index]!=0){
                        // seconds or minutes
                        if (toDisplayContent[index]<60){
                            val activityValue = toDisplayContent[index]
                            fillIntoMainInfo = fillIntoMainInfo+"$activityName: $activityValue sec\n"
                        }
                        else {
                            val activityValue =((toDisplayContent[index]).toFloat() / 60 * 100.0).roundToInt() / 100.0
                            fillIntoMainInfo = fillIntoMainInfo + "$activityName: $activityValue min\n"
                        }
                    }
                    else{
                        // no record for an activity
                        val activityValue = 0
                        fillIntoMainInfo = fillIntoMainInfo+"$activityName: $activityValue\n"
                    }
                }
                if (others>0){
                    entries.add(PieEntry(others.toFloat(),"Others"))
                }
                val set = PieDataSet(entries, "")
                var color = ArrayList<Int>()
                for (i in ColorTemplate.VORDIPLOM_COLORS)
                    color.add(i)
                for (i in ColorTemplate.JOYFUL_COLORS)
                    color.add(i)
                for (i in ColorTemplate.COLORFUL_COLORS)
                    color.add(i)
                // setting the piechart
                set.setColors(color)
                set.valueTextSize = 18f
                set.valueTextColor = Color.parseColor("#1C4E1F")
                val dt = PieData(set)
                dt.setValueFormatter(PercentFormatter(binding.piechart))
                binding.piechart.data=dt
                binding.piechart.setDrawEntryLabels(false)
                binding.piechart.setUsePercentValues(true)
                binding.piechart.description.isEnabled=false
                binding.piechart.legend.form=Legend.LegendForm.SQUARE
                binding.piechart.legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
                binding.piechart.legend.horizontalAlignment = Legend.LegendHorizontalAlignment.LEFT
                binding.piechart.legend.orientation = Legend.LegendOrientation.VERTICAL
                binding.piechart.legend.textSize=15f
                binding.piechart.legend.textColor=Color.parseColor("#1C4E1F")
                binding.piechart.legend.isWordWrapEnabled=false
                binding.piechart.legend.setDrawInside(true)
                binding.piechart.dragDecelerationFrictionCoef= 0.95F
                binding.piechart.invalidate()
                binding.mainInfo.text=fillIntoMainInfo
                binding.piechart.isVisible = !isTextShow
                if (!isTextShow){
                    binding.mainInfo.setTextColor(Color.parseColor("#001C4E1F"))
                }
                else{
                    binding.mainInfo.setTextColor(Color.parseColor("#1C4E1F"))
                }
            }
        }

        override fun onNothingSelected(p0: AdapterView<*>?) {

        }


    }
}