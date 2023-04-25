package com.example.drawing_app

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.github.antonpopoff.colorwheel.ColorWheel
import com.github.antonpopoff.colorwheel.gradientseekbar.GradientSeekBar
import com.github.antonpopoff.colorwheel.gradientseekbar.setAlphaChangeListener
import com.github.antonpopoff.colorwheel.gradientseekbar.setBlackToColor
import com.github.antonpopoff.colorwheel.gradientseekbar.setTransparentToColor
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.util.Log
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.lang.Exception
import java.util.*

class MainActivity : AppCompatActivity(){

    private var drawingView: DrawingView? = null
    private var cwDialog : Dialog? = null
    private var colorWheel : ColorWheel? = null
    private var vGradient: GradientSeekBar? = null
    private var hGradient: GradientSeekBar? =  null
    private var currentChosen: Boolean? = null

    private var color: Int? = null
    private var colorPrevious: Int? = null
    private var colorCurrent: TextView? = null
    private var colorNew: TextView? = null
    private var colorOk: Button? = null
    private var offsetV: Float? = null
    private var offsetH: Float? = null

    private var brushDialog: Dialog? = null
    private var brushCircle: ImageView? = null
    private var sliderBrush: Slider? = null
    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private var paint: Paint? = null
    private var rad: Float? = null
    private var bgImage: ImageView? = null

    private val openGalleryLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
                result ->
            if(result.resultCode == RESULT_OK && result.data != null){
                val bgRemoverSnackbar = Snackbar.make(this,
                    bgImage!!,
                    "To remove the background image, hold the gallery button",
                    Snackbar.LENGTH_INDEFINITE)
                    .setAnchorView(findViewById(R.id.imageSelector))
                    .setBackgroundTint(Color.BLACK)
                    .setTextColor(
                        ContextCompat.getColor(this, R.color.snackbar_text_color))
                bgRemoverSnackbar.setAction("Dismiss"){
                    bgRemoverSnackbar.dismiss()
                }.show()
                bgImage?.setImageURI(result.data?.data)
            }
        }

    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value
                if(isGranted) {
                    val pickIntent = Intent(Intent.ACTION_PICK,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

                    openGalleryLauncher.launch(pickIntent)
                }else{
                    if(permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(this,
                            "Oops, you just denied the permission to access the gallery",
                            Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawView)
        bgImage = findViewById(R.id.bg_image)
        val brushSize: ImageButton = findViewById(R.id.brushSizeSelector)
        val colorSel: ImageButton = findViewById(R.id.colorSelector)
        val gallery: ImageButton = findViewById(R.id.imageSelector)
        val btnUndo: ImageButton = findViewById(R.id.undo)
        val btnRedo: ImageButton = findViewById(R.id.redo)
        val btnSave: ImageButton = findViewById(R.id.save)
        val btnShare: ImageButton = findViewById(R.id.share)

        setColorWheelDialogContent()
        setBrushSizeDialogContent()

        gallery.setOnClickListener {
            requestStoragePermission()
        }
        gallery.setOnLongClickListener {
            bgImage?.setImageURI(null)
            return@setOnLongClickListener true
        }
        brushSize.setOnClickListener {
            brushSizeDialog()
        }
        colorSel.setOnClickListener {
            colorWheelDialog()
        }
        btnUndo.setOnClickListener {
            drawingView?.onClickUndo()
        }
        btnUndo.setOnLongClickListener {
            drawingView?.clearAll()
            return@setOnLongClickListener true
        }
        btnRedo.setOnClickListener {
            drawingView?.onClickRedo()
        }
        btnSave.setOnClickListener {
            showFileNameDialog()
        }
        btnShare.setOnClickListener {
            if (isReadStorageAllowed()) {
                lifecycleScope.launch {
                    val flView: FrameLayout = findViewById(R.id.frame_container)
                    shareDrawing(getBitmapFromView(flView))
                }
            }
        }
    }
    private fun setColorWheelDialogContent() {
        cwDialog = Dialog(this)
        cwDialog!!.setContentView(R.layout.colorwheel_dialog)
        colorWheel = cwDialog!!.findViewById(R.id.colorwheel)
        vGradient = cwDialog!!.findViewById(R.id.gradientVbar)
        hGradient = cwDialog!!.findViewById(R.id.gradientHbar)
        colorCurrent = cwDialog!!.findViewById(R.id.Current)
        colorNew = cwDialog!!.findViewById(R.id.New)
        colorOk = cwDialog!!.findViewById(R.id.ok)
        vGradient!!.thumbColorCircleScale = 0.9F
        hGradient!!.thumbColorCircleScale = 0.9F
        colorPrevious = Color.BLACK
        color = Color.BLACK
        currentChosen = false
        colorCurrent!!.setBackgroundColor(Color.BLACK)
    }
    private fun setBrushSizeDialogContent() {
        brushDialog = Dialog(this)
        brushDialog!!.setContentView(R.layout.brushsize_dialog)
        brushDialog!!.setTitle("Brush Size Selector")
        brushCircle = brushDialog!!.findViewById(R.id.brushCircle)
        sliderBrush = brushDialog!!.findViewById(R.id.slider)
        bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap!!)
        paint = Paint()
        paint!!.color = Color.BLUE
        paint!!.strokeWidth = 30F
        rad = 1F
        canvas!!.drawCircle(200F, 100F, rad!!, paint!!)
        brushCircle!!.setImageBitmap(bitmap)
        sliderBrush!!.setBackgroundColor(Color.WHITE)
    }

    private fun isReadStorageAllowed(): Boolean {
        return (ContextCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
    }

    private fun getBitmapFromView(view:View) : Bitmap {
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null)
            bgDrawable.draw(canvas)
        else
            canvas.drawColor(Color.WHITE)
        view.draw(canvas)
        return returnedBitmap
    }

    private fun showFileNameDialog() {
        val saveDialog = Dialog(this)
        saveDialog.setContentView(R.layout.save_name_dialog)
        saveDialog.window!!.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT)
        saveDialog.show()
        val userInput: EditText = saveDialog.findViewById(R.id.et_FileName)
        val saveAction: Button = saveDialog.findViewById(R.id.saveAction)
        saveAction.setOnClickListener {
            when {
                userInput.text.isNullOrEmpty() -> {
                    Toast.makeText(this,
                        "Please Enter a valid File name", Toast.LENGTH_SHORT).show()
                }
                userInput.text.contains(".") -> {
                    Toast.makeText(this,
                        "File name should not contain '.'", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    saveDialog.dismiss()
                    saveDrawingWithNameInStorage(userInput.text.toString())
                }
            }
        }
    }

    private fun saveDrawingWithNameInStorage(fileName: String) {
        if (isReadStorageAllowed()){
            lifecycleScope.launch(Dispatchers.IO){
                // Saving drawing in gallery/photos
                val galleryUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val contentValues = ContentValues()
                contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.jpg")
                contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                contentValues.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
                val imageUri = contentResolver.insert(galleryUri,contentValues)

                try {
                    val flView: FrameLayout = findViewById(R.id.frame_container)
                    val drawingBitmap = getBitmapFromView(flView)
                    imageUri?.let { uri ->
                        val outputStream: OutputStream? = contentResolver.openOutputStream(uri)
                        outputStream?.let { outStream ->
                            drawingBitmap.compress(Bitmap.CompressFormat.JPEG,90,outStream)
                            outStream.flush()
                            outStream.close()
                        } ?: throw IOException("Failed to get Output Stream")
                    } ?: throw  IOException("Failed to create MediaStore record")

                    runOnUiThread{
                        Toast.makeText(this@MainActivity,
                            "Drawing saved successfully. Can be accessed via Gallery/Photos",
                            Toast.LENGTH_LONG).show()
                    }
                } catch (e: IOException) {
                    Log.e("IO Error",e.toString())
                    Toast.makeText(this@MainActivity,
                        "Could not save drawing", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("Error",e.toString())
                    Toast.makeText(this@MainActivity,
                        "Could not save drawing", Toast.LENGTH_SHORT).show()
                }
                // Below code is for saving the drawing in external cache
//                try {
//
//                    val bytes = ByteArrayOutputStream()
//                    drawingBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
//                    val file = File(externalCacheDir?.absoluteFile.toString()
//                                + File.separator + fileName + ".jpg")
//                    val fo = FileOutputStream(file)
//                    fo.write(bytes.toByteArray())
//                    fo.close()
//                    runOnUiThread {
//                        if (file.absolutePath.isEmpty()) Toast.makeText(this@MainActivity,
//                            "Drawing saved successfully: ${file.absolutePath}",
//                            Toast.LENGTH_SHORT).show()
//                        else Toast.makeText(this@MainActivity,
//                            "Something went wrong while saving the drawing.",
//                            Toast.LENGTH_LONG).show()
//                    }
//                } catch (e: Exception){
//                    Log.e("Error", e.toString())
//                }
            }
        }
    }

    private suspend fun shareDrawing(bitmap:Bitmap) {
        withContext(Dispatchers.IO) {
            val imagesFolder = File(cacheDir, "images")
            try {
                imagesFolder.mkdirs()
                val file = File(imagesFolder, "${UUID.randomUUID()}" +"_shared_image.png")
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream)
                stream.flush()
                stream.close()
                val uri = FileProvider.getUriForFile(this@MainActivity,
                    "com.example.drawing_app.fileprovider", file)
                runOnUiThread {
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.putExtra(Intent.EXTRA_STREAM, uri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    intent.type = "image/png"
                    startActivity(intent)
                }
            } catch (e: IOException) {
                Log.e("Error1: ", "Could not create image (png) file")
            } catch (e: FileNotFoundException) {
                Log.e("Error2: ", "Could not create image (png) file")
            } catch (e:Exception){
                Log.e("Error3: ", e.toString())
            }
        }
    }

    private fun requestStoragePermission(){
        if(ActivityCompat.shouldShowRequestPermissionRationale(
                this,Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("NO GALLERY ACCESS",
                "Need permission to access gallery for choosing " +
                        "a background image for drawing. " +
                        "It can be enabled under the Application Settings.")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE)
            )
        }
    }

    private fun showRationaleDialog(title: String, message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("GO TO SETTINGS") { dialog, _ ->
                dialog.dismiss()
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }

    private fun brushSizeDialog(){
        val smallBrush: ImageButton = brushDialog!!.findViewById(R.id.brushSmall)
        val mediumBrush: ImageButton = brushDialog!!.findViewById(R.id.brushMedium)
        val largeBrush: ImageButton = brushDialog!!.findViewById(R.id.brushLarge)
        val btnOk: Button = brushDialog!!.findViewById(R.id.sizeOK)

        brushDialog!!.show()
        smallBrush.setOnClickListener {
            rad = 11F
            changeBrushCircle(true)
        }
        mediumBrush.setOnClickListener {
            rad = 20F
            changeBrushCircle(true)
        }
        largeBrush.setOnClickListener {
            rad = 35F
            changeBrushCircle(true)
        }
        sliderBrush!!.addOnChangeListener { _, value, _ ->
            rad = value
            paint!!.color = color!!
            changeBrushCircle(false)
        }
        btnOk.setOnClickListener {
            drawingView!!.setBrushSize(rad!!)
            brushDialog!!.dismiss()
        }
    }

    private fun changeBrushCircle(updateSliderValue: Boolean) {
        if(updateSliderValue) sliderBrush!!.value = rad!!
        canvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas!!.drawCircle(200F, 100F, rad!!, paint!!)
    }

    private fun colorWheelDialog(){
        cwDialog!!.show()

        colorWheel!!.colorChangeListener = { rgb: Int ->
            run {
                color = rgb
                colorNew!!.setBackgroundColor(color!!)
                hGradient!!.setBlackToColor(color!!)
                vGradient!!.setTransparentToColor(color!!,true)
                hGradient!!.offset = 1F
                currentChosen = false
            }
        }
        vGradient!!.setAlphaChangeListener{ position: Float, rgb: Int, alpha: Int ->
            run {
                color = Color.argb(alpha,Color.red(rgb),Color.green(rgb),Color.blue(rgb))
                colorNew!!.setBackgroundColor(color!!)
                offsetV = position
                currentChosen = false
            }
        }
        hGradient!!.colorChangeListener = { position: Float, argb: Int ->
            run {
                color = argb
                colorNew!!.setBackgroundColor(color!!)
                offsetH = position
                currentChosen = false
            }
        }
        colorCurrent!!.setOnClickListener {
            currentChosen = currentChosen!!.not()
            if (currentChosen!!) {
                drawingView!!.setBrushColor(colorPrevious!!)
                colorNew!!.setBackgroundColor(colorPrevious!!)
            } else {
                drawingView!!.setBrushColor(color!!)
                colorNew!!.setBackgroundColor(color!!)
            }
        }

        colorOk!!.setOnClickListener {
            if (currentChosen!!) {
                colorCurrent!!.setBackgroundColor(colorPrevious!!)
                cwDialog!!.dismiss()
                currentChosen = false
            }
            else {
                drawingView!!.setBrushColor(color!!)
                colorCurrent!!.setBackgroundColor(color!!)
                colorPrevious = color!!
                cwDialog!!.dismiss()
            }
        }
    }

}