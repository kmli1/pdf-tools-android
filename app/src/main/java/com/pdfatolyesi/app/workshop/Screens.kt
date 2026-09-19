@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.pdfatolyesi.app.workshop
import android.content.Intent
import android.content.ClipData
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pdfatolyesi.app.data.Document
import kotlinx.coroutines.*
import java.io.File
import java.util.UUID

data class Tool(val id:String,val title:String,val group:String)
val catalog=listOf(
 Tool("photos","Fotoğraftan PDF","Oluştur"),Tool("camera","Kameradan PDF","Oluştur"),Tool("textpdf","Metinden PDF","Oluştur"),Tool("blank","Boş PDF","Oluştur"),
 Tool("merge","PDF birleştir","Sayfalar"),Tool("extract","Sayfaları çıkar","Sayfalar"),Tool("split","PDF böl → ZIP","Sayfalar"),Tool("reorder","Sayfaları sırala","Sayfalar"),Tool("rotate","Sayfaları döndür","Sayfalar"),Tool("delete","Sayfa sil","Sayfalar"),Tool("duplicate","Sayfa çoğalt","Sayfalar"),Tool("reverse","Sırayı ters çevir","Sayfalar"),
 Tool("edit","Çizim ve metin ekle","Düzenle"),Tool("watermark","Filigran ekle","Düzenle"),Tool("number","Sayfa numarası","Düzenle"),Tool("header","Üst bilgi","Düzenle"),
 Tool("images","PDF → JPG / PNG","Dönüştür"),Tool("text","PDF → metin","Dönüştür"),Tool("ocr","OCR ile metin çıkar","Dönüştür"),Tool("search","PDF içinde ara","Dönüştür"),Tool("compress","PDF sıkıştır","Dönüştür"),
 Tool("protect","Parola ekle","Belge"),Tool("unlock","Parolayı kaldır","Belge"),Tool("form","Form doldur","Belge"),Tool("metadata","Başlık ve yazar","Belge"),Tool("info","Belge bilgileri","Belge"))
@Composable fun Catalog(open:(String)->Unit){var query by rememberSaveable{mutableStateOf("")};val locale=java.util.Locale.forLanguageTag("tr");val entries=catalog.filter{it.title.lowercase(locale).contains(query.lowercase(locale))};LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 item{Text("Araçlar",style=MaterialTheme.typography.headlineLarge);Text("PDF Atölyesi + · 26 araç")}
 item{OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Araç ara")})}
 for((group,list)in entries.groupBy{it.group}){item{Text(group,style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.primary)};items(list,key={it.id}){tool->Card(onClick={open(tool.id)},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp)){Text(tool.title,Modifier.padding(20.dp),style=MaterialTheme.typography.titleMedium)}}}
}}
@Composable fun Choices(labels:List<String>,selected:Int,change:(Int)->Unit){Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){labels.forEachIndexed{i,t->FilterChip(i==selected,{change(i)},label={Text(t)})}}}
@Composable fun ToolScreen(kind:String,model:Model,docs:List<Document>,back:()->Unit,view:(String)->Unit,edit:(String)->Unit,importPdf:()->Unit){
 val state by model.state.collectAsStateWithLifecycle();var ids by rememberSaveable{mutableStateOf(emptyList<String>())};var range by rememberSaveable{mutableStateOf("")};var text by rememberSaveable{mutableStateOf(if(kind=="blank")"1"else if(kind=="images")"JPG"else "")};var second by rememberSaveable{mutableStateOf("")};var name by rememberSaveable{mutableStateOf("PDF-Atolyesi")};var password by remember{mutableStateOf("")};var newPassword by remember{mutableStateOf("")};var angle by rememberSaveable{mutableIntStateOf(0)};var quality by rememberSaveable{mutableFloatStateOf(80f)};var consent by rememberSaveable{mutableStateOf(false)}
 LaunchedEffect(ids,password){model.clearFields()}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
 item{TextButton(onClick=back){Text("Geri")};Text(catalog.first{it.id==kind}.title,style=MaterialTheme.typography.headlineMedium);Text("Kaynak belgen korunur; sonuç yeni dosyadır.",style=MaterialTheme.typography.bodySmall)}
 if(kind !in listOf("blank","textpdf")){
 item{TextButton(onClick=importPdf,enabled=!state.busy){Text("PDF ekle")};Text("Belge seç")}
 items(docs,key={it.id}){d->Row(Modifier.fillMaxWidth().clickable(enabled=!state.busy){ids=if(kind=="merge"){if(d.id in ids)ids-d.id else ids+d.id}else listOf(d.id)},verticalAlignment=Alignment.CenterVertically){Checkbox(d.id in ids,null);Text(d.name,Modifier.weight(1f),maxLines=2)}}
 if(kind=="merge")items(ids,key={"order-$it"}){id->val i=ids.indexOf(id);Row(verticalAlignment=Alignment.CenterVertically){Text("${i+1}. ${docs.find{it.id==id}?.name}",Modifier.weight(1f));TextButton(enabled=i>0&&!state.busy,onClick={ids=ids.toMutableList().apply{add(i-1,removeAt(i))}}){Text("↑")};TextButton(enabled=i<ids.lastIndex&&!state.busy,onClick={ids=ids.toMutableList().apply{add(i+1,removeAt(i))}}){Text("↓")}}}
 item{OutlinedTextField(password,{password=it},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("Mevcut parola (varsa)")},visualTransformation=PasswordVisualTransformation(),singleLine=true)}
 }
 if(kind in listOf("extract","split","reorder","rotate","delete","duplicate","watermark","number","header","images","text","ocr","search"))item{OutlinedTextField(range,{range=it},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("Sayfalar: 1,3-5")},supportingText={Text("Boş: tüm sayfalar. Sıralama: 3,1,2")})}
 if(kind in listOf("textpdf","blank","watermark","number","header","metadata","search"))item{OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text(when(kind){"blank"->"Sayfa sayısı (1–100)";"metadata"->"Başlık";"search"->"Aranan metin";else->"Metin"})},minLines=if(kind=="textpdf")5 else 1)}
 if(kind=="metadata")item{OutlinedTextField(second,{second=it},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("Yazar")})}
 if(kind=="rotate")item{Choices(listOf("90°","180°","270°"),angle){angle=it}}
 if(kind=="images")item{Choices(listOf("JPG","PNG"),if(text=="PNG")1 else 0){text=if(it==1)"PNG"else"JPG"}}
 if(kind in listOf("images","compress"))item{Text("Kalite: %${quality.toInt()}");Slider(quality,{quality=it},valueRange=35f..95f,enabled=!state.busy);if(kind=="compress")Text("Görseller yeniden sıkıştırılır. Kalite azalır; bazı PDF’ler küçülmeyebilir.")}
 if(kind=="protect")item{OutlinedTextField(newPassword,{newPassword=it},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("Yeni parola (en az 6 karakter)")},visualTransformation=PasswordVisualTransformation())}
 if(kind=="form"){
 item{Button(enabled=ids.size==1&&!state.busy,onClick={model.loadFields(model.app.documents.file(ids.first()),password)}){Text("Form alanlarını getir")};Text("AcroForm metin ve onay kutuları desteklenir. XFA ve imza alanları desteklenmez.")}
 items(state.fields.size){i->val f=state.fields[i];if(f.checkbox)Row{Checkbox(f.value=="true",{model.field(i,it.toString())},enabled=!state.busy);Text(f.name)}else OutlinedTextField(f.value,{model.field(i,it)},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text(f.name)})}
 }
 if(kind !in listOf("text","search","info","ocr","images","blank","textpdf","edit"))item{Row{Checkbox(consent,{consent=it},enabled=!state.busy);Text("Dijital imzalı belgedeki değişikliğin imzayı geçersiz kılabileceğini kabul ediyorum.",style=MaterialTheme.typography.bodySmall)}}
 item{OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),enabled=!state.busy,label={Text("Çıktı adı")})}
 item{val ready=when(kind){"blank"->text.toIntOrNull() in 1..100;"textpdf"->text.isNotBlank();"merge"->ids.size>=2;"form"->ids.size==1&&state.fields.isNotEmpty();"protect"->ids.size==1&&newPassword.length>=6;else->ids.size==1};Button(enabled=ready&&!state.busy,onClick={when(kind){"edit"->edit(ids.first());"form"->model.form(model.app.documents.file(ids.first()),password,name,consent);else->model.run(Options(kind,ids.map{model.app.documents.file(it)},range,text,second,password,newPassword,(angle+1)*90,quality.toInt(),consent),name)}},modifier=Modifier.fillMaxWidth()){Text(if(kind=="edit")"Düzenleyiciyi aç"else"Oluştur")};if(kind=="ocr")Text("OCR sonucu TXT’dir. Türkçe karakterleri gözden geçir; aranabilir PDF katmanı oluşturulmaz.")}
 item{Result(model,view)}
 };Message(model)
}
@Composable fun Result(model:Model,view:(String)->Unit){val state by model.state.collectAsStateWithLifecycle();val context=LocalContext.current;val clipboard=LocalClipboardManager.current;val saver=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")){it?.let(model::save)};var error by remember{mutableStateOf(false)}
 Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
 if(state.busy){LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth());TextButton(onClick=model::cancel){Text("İptal")}}
 state.output?.let{file->Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(16.dp)){Text("Sonuç hazır",style=MaterialTheme.typography.titleMedium);Text(file.name+" · "+android.text.format.Formatter.formatFileSize(context,file.length()));state.document?.let{id->Button(onClick={view(id)}){Text("PDF’yi aç")}};Row{TextButton(enabled=!state.busy,onClick={saver.launch(file.name)}){Text("Farklı kaydet")};TextButton(onClick={try{val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file,file.name);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type=when(file.extension){"pdf"->"application/pdf";"zip"->"application/zip";else->"text/plain"};putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newUri(context.contentResolver,file.name,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Paylaş"))}catch(_:Exception){error=true}}){Text("Paylaş")}}}}}
 state.preview?.let{value->OutlinedTextField(value,model::preview,Modifier.fillMaxWidth().heightIn(min=140.dp,max=350.dp),label={Text("Metin sonucu")});Row{TextButton(onClick={clipboard.setText(AnnotatedString(value))}){Text("Kopyala")};TextButton(enabled=!state.busy,onClick=model::applyText){Text("Metni kaydet")}}}
 };if(error)AlertDialog(onDismissRequest={error=false},text={Text("Paylaşım açılamadı.")},confirmButton={TextButton(onClick={error=false}){Text("Tamam")}})
}
@Composable fun Message(model:Model){val s by model.state.collectAsStateWithLifecycle();if(s.error!=null||s.saved)AlertDialog(onDismissRequest=model::clear,text={Text(s.error?:"Kaydedildi.")},confirmButton={TextButton(onClick=model::clear){Text("Tamam")}})}
@Composable fun Photos(model:Model,back:()->Unit,view:(String)->Unit){val state by model.state.collectAsStateWithLifecycle();var cameraPath by rememberSaveable{mutableStateOf("")};var cameraError by remember{mutableStateOf(false)};var name by rememberSaveable{mutableStateOf("Fotograflar.pdf")};var paper by rememberSaveable{mutableIntStateOf(0)};var landscape by rememberSaveable{mutableStateOf(false)};var margin by rememberSaveable{mutableFloatStateOf(24f)};var quality by rememberSaveable{mutableFloatStateOf(85f)}
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(50)){if(it.isNotEmpty())model.add(it)}
 val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()){ok->if(cameraPath.isNotEmpty()){val f=File(cameraPath);if(ok)model.add(listOf(Uri.fromFile(f)))else f.delete()}}
 LazyColumn(contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
 item{TextButton(onClick=back){Text("Geri")};Text("Fotoğraf ve tarama",style=MaterialTheme.typography.headlineMedium);Text("${state.photos.size}/50 sayfa · Taslak otomatik saklanır")}
 item{Row{Button(enabled=!state.busy&&state.photos.size<50,onClick={picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}){Text("Görsel ekle")};TextButton(enabled=!state.busy&&state.photos.size<50,onClick={val file=File(model.app.cacheDir,"exports/camera-${UUID.randomUUID()}.jpg").apply{parentFile!!.mkdirs()};cameraPath=file.path;try{camera.launch(FileProvider.getUriForFile(model.app,"${model.app.packageName}.files",file))}catch(_:Exception){file.delete();cameraError=true}}){Text("Kamera")}}}
 itemsIndexed(state.photos,key={_,p->p.file}){i,p->PhotoCard(model,i,p,state.photos.size,state.busy)}
 item{Choices(listOf("A4","Letter","Otomatik"),paper){paper=it};Row{Switch(landscape,{landscape=it});Text("Yatay")};Text("Kenar boşluğu: ${margin.toInt()} pt");Slider(margin,{margin=it},valueRange=0f..60f);Text("Kalite: %${quality.toInt()}");Slider(quality,{quality=it},valueRange=40f..95f);OutlinedTextField(name,{name=it},Modifier.fillMaxWidth(),label={Text("PDF adı")});Button(enabled=state.photos.isNotEmpty()&&!state.busy,onClick={model.photos(name,listOf("A4","Letter","Otomatik")[paper],landscape,margin,quality.toInt())},modifier=Modifier.fillMaxWidth()){Text("PDF oluştur")}}
 item{Result(model,view)}
 };Message(model);if(cameraError)AlertDialog(onDismissRequest={cameraError=false},text={Text("Kamera uygulaması bulunamadı; galeriden görsel seçebilirsin.")},confirmButton={TextButton(onClick={cameraError=false}){Text("Tamam")}})
}
@Composable fun PhotoCard(model:Model,i:Int,p:Photo,count:Int,busy:Boolean){var crop by remember(p){mutableFloatStateOf(p.crop)};val bitmap by produceState<Bitmap?>(null,p){value=withContext(Dispatchers.IO){runCatching{model.engine.photo(p,600)}.getOrNull()}};Card{Column(Modifier.padding(12.dp)){
 Text("Sayfa ${i+1}");bitmap?.let{Image(it.asImageBitmap(),"Sayfa ${i+1}",Modifier.fillMaxWidth().height(170.dp))}
 Row(Modifier.horizontalScroll(rememberScrollState())){TextButton(enabled=i>0&&!busy,onClick={model.move(i,-1)}){Text("↑")};TextButton(enabled=i<count-1&&!busy,onClick={model.move(i,1)}){Text("↓")};TextButton(enabled=!busy,onClick={model.change(i,p.copy(rotation=(p.rotation+90)%360))}){Text("Döndür")};TextButton(enabled=!busy,onClick={model.remove(i)}){Text("Sil")}}
 Choices(listOf("Renkli","Gri","Siyah-beyaz"),p.filter){if(!busy)model.change(i,p.copy(filter=it))};Text("Kenarlardan kırp: %${(crop*100).toInt()}");Slider(crop,{crop=it},enabled=!busy,valueRange=0f..0.35f,onValueChangeFinished={model.change(i,p.copy(crop=crop))});TextButton(enabled=!busy,onClick={model.photoOcr(i)}){Text("Yazıyı tanı (OCR)")}
}}}
