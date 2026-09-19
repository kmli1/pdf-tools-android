package com.pdfatolyesi.app.workshop
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.pdfatolyesi.app.PdfApplication
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.*
import java.io.File

data class Mark(val kind:String,val points:List<Float>,val color:Int,val text:String="",val size:Float=3f)
data class EditState(val busy:Boolean=false,val page:Int=0,val count:Int=0,val bitmap:Bitmap?=null,val width:Float=595f,val height:Float=842f,val marks:Map<Int,List<Mark>> = emptyMap(),val error:String?=null,val result:String?=null,val undo:Boolean=false,val redo:Boolean=false)
class EditModel(val app:PdfApplication,val id:String):ViewModel(){
 private val data=MutableStateFlow(EditState());val state=data.asStateFlow();private val session=app.engine.createSession();private val engine=Engine(app,app.engine);private var password=""
 private val undo=ArrayDeque<Map<Int,List<Mark>>>();private val redo=ArrayDeque<Map<Int,List<Mark>>>()
 private val draft=android.util.AtomicFile(File(app.filesDir,"editor-drafts/$id.json").apply{parentFile!!.mkdirs()})
 init{open("")}
 private fun action(block:suspend()->Unit){if(data.value.busy)return;data.update{it.copy(busy=true,error=null)};viewModelScope.launch(Dispatchers.IO){try{block()}catch(e:CancellationException){throw e}catch(e:Exception){data.update{it.copy(error=e.message?:"İşlem tamamlanamadı.")}}finally{data.update{it.copy(busy=false)}}}}
 fun open(value:String){action{password=value;session.open(app.documents.file(id),value.takeIf{it.isNotBlank()});val marks=runCatching{val root=JSONObject(draft.openRead().bufferedReader().use{it.readText()});root.keys().asSequence().associate{k->val a=root.getJSONArray(k);k.toInt() to (0 until a.length()).map{i->val v=a.getJSONObject(i);val p=v.getJSONArray("points");Mark(v.getString("kind"),(0 until p.length()).map{p.getDouble(it).toFloat()},v.getInt("color"),v.getString("text"),v.getDouble("size").toFloat())}}}.getOrDefault(emptyMap());data.update{it.copy(count=session.pageCount,marks=marks)};render(0)}}
 private suspend fun render(p:Int){val(w,h)=engine.load(app.documents.file(id),password).use{engine.dimensions(it.getPage(p))};val b=session.render(p,1400);data.update{it.copy(page=p,width=w,height=h,bitmap=b)}}
 fun page(p:Int){if(p in 0 until data.value.count)action{render(p)}}
 private fun persist(){val marks=data.value.marks;action{val root=JSONObject();marks.forEach{(page,list)->root.put(page.toString(),JSONArray().apply{list.forEach{put(JSONObject().put("kind",it.kind).put("points",JSONArray(it.points)).put("color",it.color).put("text",it.text).put("size",it.size))}})};val out=draft.startWrite();try{out.write(root.toString().toByteArray());draft.finishWrite(out)}catch(e:Throwable){draft.failWrite(out);throw e}}}
 fun add(m:Mark){if(data.value.busy)return;undo.addLast(data.value.marks);if(undo.size>50)undo.removeFirst();redo.clear();data.update{it.copy(marks=it.marks+(it.page to (it.marks[it.page].orEmpty()+m)),undo=true,redo=false,result=null)};persist()}
 fun undo(){if(data.value.busy||undo.isEmpty())return;redo.addLast(data.value.marks);data.update{it.copy(marks=undo.removeLast(),undo=undo.isNotEmpty(),redo=true)};persist()}
 fun redo(){if(data.value.busy||redo.isEmpty())return;undo.addLast(data.value.marks);data.update{it.copy(marks=redo.removeLast(),undo=true,redo=redo.isNotEmpty())};persist()}
 fun export(name:String,consent:Boolean){action{val out=File(app.cacheDir,"exports/${java.util.UUID.randomUUID()}.pdf").apply{parentFile!!.mkdirs()};try{engine.load(app.documents.file(id),password).use{d->engine.check(d,consent);val font=engine.font(d);for((index,marks)in data.value.marks){currentCoroutineContext().ensureActive();if(index !in 0 until d.numberOfPages)continue;val p=d.getPage(index);val(w,h)=engine.dimensions(p);PDPageContentStream(d,p,PDPageContentStream.AppendMode.APPEND,true,true).use{s->s.transform(engine.matrix(p));marks.forEach{m->s.saveGraphicsState();val r=android.graphics.Color.red(m.color)/255f;val g=android.graphics.Color.green(m.color)/255f;val b=android.graphics.Color.blue(m.color)/255f;s.setStrokingColor(r,g,b);s.setNonStrokingColor(r,g,b);s.setLineWidth(m.size);val x=m.points[0]*w;val y=(1-m.points[1])*h
 when(m.kind){"TEXT"->{s.beginText();s.setFont(font,m.size);s.setLeading(m.size*1.25f);s.newLineAtOffset(x,y-m.size);engine.safe(m.text,font).split('\n').forEach{s.showText(it);s.newLine()};s.endText()};"PEN"->{s.moveTo(x,y);for(i in 2 until m.points.size step 2)s.lineTo(m.points[i]*w,(1-m.points[i+1])*h);s.stroke()};"LINE"->{s.moveTo(x,y);s.lineTo(m.points[2]*w,(1-m.points[3])*h);s.stroke()};else->{val x2=m.points[2]*w;val y2=(1-m.points[3])*h;s.addRect(minOf(x,x2),minOf(y,y2),kotlin.math.abs(x2-x),kotlin.math.abs(y2-y));if(m.kind=="HIGHLIGHT"){s.setGraphicsStateParameters(PDExtendedGraphicsState().apply{nonStrokingAlphaConstant=0.25f});s.fill()}else s.stroke()}};s.restoreGraphicsState()}}};d.save(out)};val result=app.documents.import(android.net.Uri.fromFile(out),name){};data.update{it.copy(result=result)}}finally{out.delete()}}}
 fun clear(){data.update{it.copy(error=null)}}
 override fun onCleared(){super.onCleared();CoroutineScope(Dispatchers.IO).launch{session.close()};password=""}
}
@Composable fun Editor(model:EditModel,back:()->Unit,view:(String)->Unit){val s by model.state.collectAsStateWithLifecycle();var password by remember{mutableStateOf("")};var text by rememberSaveable{mutableStateOf("")};var mode by rememberSaveable{mutableIntStateOf(0)};var color by rememberSaveable{mutableIntStateOf(0xff006b5e.toInt())};var brush by rememberSaveable{mutableFloatStateOf(3f)};var fontSize by rememberSaveable{mutableFloatStateOf(18f)};var name by rememberSaveable{mutableStateOf("Duzenlenen.pdf")};var consent by rememberSaveable{mutableStateOf(false)};var drawing by remember{mutableStateOf(emptyList<Float>())};val types=listOf("PEN","TEXT","HIGHLIGHT","RECT","LINE");val typeface=remember{android.graphics.Typeface.createFromAsset(model.app.assets,"fonts/DejaVuSans.ttf")}
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
 TextButton(onClick=back){Text("Geri")};Text("Çizim ve metin",style=MaterialTheme.typography.headlineMedium);Text("Seçtiğin aracı sayfada sürükle. Mevcut PDF metni değiştirilmez. Çizilen imza elektronik imza değildir.",style=MaterialTheme.typography.bodySmall)
 if(s.count==0){OutlinedTextField(password,{password=it},label={Text("PDF parolası")},visualTransformation=androidx.compose.ui.text.input.PasswordVisualTransformation());Button(enabled=!s.busy,onClick={model.open(password);password=""}){Text("Aç")}}
 if(s.count>0){Row{TextButton(enabled=s.page>0&&!s.busy,onClick={model.page(s.page-1)}){Text("Önceki")};Text("${s.page+1}/${s.count}");TextButton(enabled=s.page<s.count-1&&!s.busy,onClick={model.page(s.page+1)}){Text("Sonraki")}}
 Choices(listOf("Kalem / imza","Metin","Vurgula","Dikdörtgen","Çizgi"),mode){mode=it}
 if(mode==1){OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),label={Text("Eklenecek metin")});Slider(fontSize,{fontSize=it},valueRange=8f..48f)}else Slider(brush,{brush=it},valueRange=1f..12f)
 Row(Modifier.horizontalScroll(rememberScrollState())){listOf(0xff006b5e,0xff000000,0xffb3261e,0xff1565c0,0xffe7ae00).forEach{c->FilterChip(color==c.toInt(),{color=c.toInt()},label={Box(Modifier.size(24.dp).background(Color(c)))})}}
 s.bitmap?.let{b->Box(Modifier.fillMaxWidth().aspectRatio(s.width/s.height).background(Color.White)){Image(b.asImageBitmap(),"Sayfa ${s.page+1}",Modifier.matchParentSize());Canvas(Modifier.matchParentSize().pointerInput(mode,s.page,s.busy,color,text,brush,fontSize){if(!s.busy)detectDragGestures(onDragStart={p->drawing=listOf((p.x/size.width).coerceIn(0f,1f),(p.y/size.height).coerceIn(0f,1f))},onDragCancel={drawing=emptyList()},onDragEnd={if(drawing.size>=4&&(mode!=1||text.isNotBlank()))model.add(Mark(types[mode],if(mode==0)drawing else drawing.take(2)+drawing.takeLast(2),color,text,if(mode==1)fontSize else brush));drawing=emptyList()}){c,_->c.consume();drawing=drawing+listOf((c.position.x/size.width).coerceIn(0f,1f),(c.position.y/size.height).coerceIn(0f,1f))}}){
 fun drawMark(m:Mark){val x=m.points[0]*size.width;val y=m.points[1]*size.height;val c=Color(m.color);val line=m.size*size.width/s.width
 when(m.kind){"TEXT"->m.text.split('\n').forEachIndexed{i,t->drawContext.canvas.nativeCanvas.drawText(t,x,y+line+i*line*1.25f,android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply{color=m.color;textSize=line;this.typeface=typeface})};"PEN"->{val path=Path();m.points.chunked(2).forEachIndexed{i,p->if(i==0)path.moveTo(p[0]*size.width,p[1]*size.height)else path.lineTo(p[0]*size.width,p[1]*size.height)};drawPath(path,c,style=Stroke(line))};else->{val x2=m.points[2]*size.width;val y2=m.points[3]*size.height;if(m.kind=="LINE")drawLine(c,Offset(x,y),Offset(x2,y2),line)else drawRect(c.copy(alpha=if(m.kind=="HIGHLIGHT")0.25f else 1f),Offset(minOf(x,x2),minOf(y,y2)),Size(kotlin.math.abs(x2-x),kotlin.math.abs(y2-y)),style=if(m.kind=="HIGHLIGHT")androidx.compose.ui.graphics.drawscope.Fill else Stroke(line))}}
 };s.marks[s.page].orEmpty().forEach{drawMark(it)};if(drawing.size>=4)drawMark(Mark(types[mode],if(mode==0)drawing else drawing.take(2)+drawing.takeLast(2),color,text,if(mode==1)fontSize else brush))
 }}}
 Row{TextButton(enabled=s.undo&&!s.busy,onClick=model::undo){Text("Geri al")};TextButton(enabled=s.redo&&!s.busy,onClick=model::redo){Text("Yinele")}}
 OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("Dosya adı")});Row{Checkbox(consent,{consent=it});Text("Dijital imzalı belge değişikliğinin imzayı geçersiz kılabileceğini kabul ediyorum.",style=MaterialTheme.typography.bodySmall)};Button(enabled=!s.busy,onClick={model.export(name,consent)},modifier=Modifier.fillMaxWidth()){Text("Yeni PDF oluştur")}}
 if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth());s.result?.let{id->Button(onClick={view(id)}){Text("Sonucu aç")}}
 };s.error?.let{message->AlertDialog(onDismissRequest=model::clear,text={Text(message)},confirmButton={TextButton(onClick=model::clear){Text("Tamam")}})}
}
