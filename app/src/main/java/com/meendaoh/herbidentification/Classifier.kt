package com.meendaoh.herbidentification

import android.content.res.AssetManager
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.Comparator
import kotlin.collections.ArrayList

class Classifier(assetManager: AssetManager, modelPath:String, labelPath:String, inputSize:Int) {
    private var interpreter: Interpreter
    private var labelList: List<String>
    private val INPUT_SIZE = inputSize
    private val PIXEL_SIZE = 3
    private val IMAGE_MEAN = 0
    private val IMAGE_STD = 255.0f
    private val MAX_RESULTS = 3
    private val THRESHOLD = 0.4f

    data class Recognition(
        var id: String = "",
        var title: String = "",
        var confidence:Float = 0F
    ) {
        override fun toString(): String {
            return "Title = $title, Confidence = $confidence"
        }
    }
    init {
        val options = Interpreter.Options()
        options.setNumThreads(5)
        options.setUseNNAPI(true)
        interpreter = Interpreter(loadmodelFile(assetManager,modelPath),options)
        labelList = loadLabelList(assetManager,labelPath)
    }
    private fun loadmodelFile(assetManager: AssetManager, modelPath: String): MappedByteBuffer {
        val fileDescriptor = assetManager.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startoffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY,startoffset,declaredLength)
    }
    private fun loadLabelList(assetManager: AssetManager, labelPath: String):List<String>{
        return assetManager.open(labelPath).bufferedReader().useLines { it.toList() }
    }
    fun recognizeImage(bitmap: Bitmap):List<Recognition>{
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap,INPUT_SIZE,INPUT_SIZE,false)
        val byteBuffer = convertBitmapToByteBuffer(scaledBitmap)
        val result = Array(1){FloatArray(labelList.size)}
        interpreter.run(byteBuffer,result)
        return getSortedResult(result)
    }
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val byteBuffer = ByteBuffer.allocateDirect(4*INPUT_SIZE*INPUT_SIZE*PIXEL_SIZE)
        byteBuffer.order(ByteOrder.nativeOrder())
        val intValues = IntArray(INPUT_SIZE*INPUT_SIZE)
        bitmap.getPixels(intValues,0,bitmap.width,0,0,bitmap.width,bitmap.height)
        var pixel = 0
        for(i in 0 until INPUT_SIZE){
            for(j in 0 until INPUT_SIZE){
                val input = intValues[pixel++]
                byteBuffer.putFloat((((input.shr(16) and 0xFF)-IMAGE_MEAN)/IMAGE_STD))
                byteBuffer.putFloat((((input.shr(8) and 0xFF)-IMAGE_MEAN)/IMAGE_STD))
                byteBuffer.putFloat((((input and 0xFF)-IMAGE_MEAN)/IMAGE_STD))
            }
        }
        return byteBuffer
    }
    private fun getSortedResult(labelProbArray: Array<FloatArray>):List<Classifier.Recognition>{
        val pq = PriorityQueue(
            MAX_RESULTS,
            Comparator<Recognition>{
                    (_,_,confidence1),(_,_,confidence2)
                -> java.lang.Float.compare(confidence1,confidence2)*-1
            })
        for (i in labelList.indices){
            val confidence = labelProbArray[0][i];
            if (confidence > THRESHOLD) {
                pq.add(Classifier.Recognition(""+ i,
                    if(labelList.size > i) labelList[i] else "Unknown",confidence)
                )
            }
        }
        val recognitions = ArrayList<Classifier.Recognition>()
        val recognitionsSize = Math.min(pq.size,MAX_RESULTS)
        for (i in 0 until recognitionsSize){
            recognitions.add(pq.poll())
        }
        return recognitions
    }
}