package com.pdfatolyesi.app.workshop
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pdfatolyesi.app.PdfApplication
import com.pdfatolyesi.app.core.FileRules
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.UUID
import org.json.*
data class State(val busy:Boolean=false,val progress:Float=0f,val error:String?=null,val output:File?=null,val document:String?=null,val preview:String?=null,val fields:List<FormField> = emptyList(),val photos:List<Photo> = emptyList(),val saved:Boolean=false)
class Model(val app:PdfApplication):ViewModel(){
 val engine=Engine(app,app.engine);private val mutable=MutableStateFlow(State());val state=mutable.asStateFlow();private var job:Job?=null
 private val folder=File(app.filesDir,"photo-draft").apply{mkdirs()};private val draft=android.util.AtomicFile(File(folder,"draft.json"))
 init{action(false){val photos=runCatching{val a=JSONArray(draft.openRead().bufferedReader().use{it.readText()});(0 until a.length()).map{a.getJSONObject(it)}.map{Photo(File(folder,it.getString("file")).path,it.optInt("rotation"),it.optInt("filter"),it.optDouble("crop",0.0).toFloat())}.filter{File(it.file).exists()}}.getOrDefault(emptyList());mutable.update{it.copy(photos=photos)}}}
 fun action(reset:Boolean=true,block:suspend()->Unit){if(mutable.value.busy)return;mutable.update{it.copy(busy=true,error=null,saved=false,progress=0f,output=if(reset)null else it.output,document=if(reset)null else it.document,preview=if(reset)null else it.preview)};job=viewModelScope.launch(Dispatchers.IO){try{block()}catch(e:CancellationException){throw e}catch(e:Exception){mutable.update{it.copy(error=when(e){is com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException->"Parola gerekiyor veya parola yanlış.";is com.pdfatolyesi.app.pdf.PasswordUnavailable->"Şifreli PDF görüntüleme Android 15+ gerektirir. Bilinen sahip parolasıyla Parolayı kaldır aracını kullanabilirsin.";else->e.message?.take(220)?:"İşlem tamamlanamadı."})}}finally{mutable.update{it.copy(busy=false)}}}}
 fun output(name:String,ext:String)=File(app.cacheDir,"exports/${UUID.randomUUID()}/${FileRules.displayName(name).removeSuffix(".pdf").take(100)}.$ext").apply{parentFile!!.mkdirs()}
 suspend fun publish(file:File){try{val id=if(file.extension=="pdf")app.documents.import(Uri.fromFile(file),file.name){}else null;mutable.update{it.copy(output=file,document=id,preview=if(file.extension=="txt"&&file.length()<400000)file.readText()else null)}}catch(e:Throwable){file.delete();throw e}}
 fun run(o:Options,name:String){action{val out=output(name,when(o.kind){"split","images"->"zip";"text","ocr","info","search"->"txt";else->"pdf"});engine.run(o,out){p->mutable.update{it.copy(progress=p)}};publish(out)}}
 fun loadFields(file:File,password:String){action{mutable.update{it.copy(fields=engine.fields(file,password))}}}
 fun clearFields(){mutable.update{it.copy(fields=emptyList())}}
 fun field(i:Int,value:String){mutable.update{it.copy(fields=it.fields.mapIndexed{n,f->if(i==n)f.copy(value=value)else f})}}
 fun form(file:File,password:String,name:String,consent:Boolean){val fields=mutable.value.fields;action{val out=output(name,"pdf");engine.fill(file,password,fields,out,consent);publish(out)}}
 fun save(uri:Uri){val file=mutable.value.output?:return;action(false){try{val ctx=currentCoroutineContext();app.contentResolver.openOutputStream(uri,"wt")?.use{out->file.inputStream().use{input->FileRules.copyBounded(input,out,Long.MAX_VALUE){ctx.ensureActive()}}}?:throw IllegalArgumentException("Hedef açılamadı.");mutable.update{it.copy(saved=true)}}catch(e:Exception){runCatching{android.provider.DocumentsContract.deleteDocument(app.contentResolver,uri)};throw e}}}
 fun preview(text:String){mutable.update{it.copy(preview=text)}}
 fun applyText(){val text=mutable.value.preview?:return;val file=mutable.value.output?:return;action(false){file.writeText(text);mutable.update{it.copy(saved=true)}}}
 private fun persist(){val a=JSONArray();mutable.value.photos.forEach{p->a.put(JSONObject().put("file",File(p.file).name).put("rotation",p.rotation).put("filter",p.filter).put("crop",p.crop))};val stream=draft.startWrite();try{stream.write(a.toString().toByteArray());draft.finishWrite(stream)}catch(e:Throwable){draft.failWrite(stream);throw e}}
 fun add(uris:List<Uri>){action{require(mutable.value.photos.size+uris.size<=50){"En fazla 50 görsel ekleyebilirsin."};uris.forEachIndexed{i,uri->val file=File(folder,"${UUID.randomUUID()}.img");try{val ctx=currentCoroutineContext();app.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{out->FileRules.copyBounded(input,out,40L*1024*1024){ctx.ensureActive()}}}?:throw IllegalArgumentException("Görsel açılamadı.");val p=Photo(file.path);engine.photo(p,180).recycle();mutable.update{it.copy(photos=it.photos+p,progress=(i+1f)/uris.size)};persist()}catch(e:Throwable){mutable.update{it.copy(photos=it.photos.filterNot{p->p.file==file.path})};file.delete();throw e}}}}
 fun change(i:Int,p:Photo){action(false){mutable.update{it.copy(photos=it.photos.toMutableList().apply{this[i]=p})};persist()}}
 fun move(i:Int,delta:Int){if(i+delta !in mutable.value.photos.indices)return;action(false){mutable.update{it.copy(photos=it.photos.toMutableList().apply{add(i+delta,removeAt(i))})};persist()}}
 fun remove(i:Int){action(false){val f=File(mutable.value.photos[i].file);mutable.update{it.copy(photos=it.photos.toMutableList().apply{removeAt(i)})};persist();f.delete()}}
 fun photos(name:String,paper:String,landscape:Boolean,margin:Float,quality:Int){action{val out=output(name,"pdf");engine.photos(mutable.value.photos,paper,landscape,margin,quality,out){p->mutable.update{it.copy(progress=p)}};publish(out)}}
 fun photoOcr(i:Int){action{val b=engine.photo(mutable.value.photos[i]);try{val out=output("Fotograf-metni","txt");out.writeText(engine.recognize(b));publish(out)}finally{b.recycle()}}}
 fun cancel(){job?.cancel()};fun clear(){mutable.update{it.copy(error=null,saved=false)}}
}
