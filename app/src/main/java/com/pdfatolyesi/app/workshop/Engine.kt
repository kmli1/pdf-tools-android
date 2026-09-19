package com.pdfatolyesi.app.workshop
import android.content.Context
import android.graphics.*
import androidx.exifinterface.media.ExifInterface
import com.pdfatolyesi.app.pdf.PdfEngine
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.pdmodel.encryption.*
import com.tom_roush.pdfbox.pdmodel.graphics.image.*
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.form.*
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.text.PDFTextStripper
import android.graphics.Matrix as AndroidMatrix
import com.tom_roush.pdfbox.util.Matrix as PdfMatrix
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.zip.*
import kotlin.math.*
data class Options(val kind:String,val files:List<File>,val range:String="",val text:String="",val second:String="",val password:String="",val newPassword:String="",val angle:Int=90,val quality:Int=80,val consent:Boolean=false)
data class Photo(val file:String,val rotation:Int=0,val filter:Int=0,val crop:Float=0f)
data class FormField(val name:String,val value:String,val checkbox:Boolean)
class Engine(val context:Context,val renderer:PdfEngine){
 fun load(file:File,password:String="")=PDDocument.load(file,password,MemoryUsageSetting.setupTempFileOnly())
 fun font(d:PDDocument)=context.assets.open("fonts/DejaVuSans.ttf").use{PDType0Font.load(d,it)}
 fun safe(t:String,f:PDType0Font):String=buildString{t.codePoints().forEach{cp->val s=String(Character.toChars(cp));append(if(s=="\n"||runCatching{f.getStringWidth(s)}.isSuccess)s else "?")}}
 fun check(d:PDDocument,consent:Boolean){require(d.currentAccessPermission.canModify()){ "Belge değişikliğe izin vermiyor." };require(d.signatureDictionaries.isEmpty()||consent){"İmzalı belge değişikliği için onay gerekiyor."}}
 fun ranges(raw:String,n:Int,repeat:Boolean=false):List<Int>{require(n>0);if(raw.isBlank())return (0 until n).toList();require(raw.length<10000);val list=raw.split(',').flatMap{v->val p=v.trim().split('-');require(p.size in 1..2);val a=p[0].trim().toInt();val b=if(p.size==2)p[1].trim().toInt()else a;require(a in 1..n&&b in a..n){"Sayfalar 1–$n arasında olmalı."};(a..b).map{it-1}};require(list.size<=10000);return if(repeat)list else list.distinct()}
 fun copy(d:PDDocument,t:PDDocument,i:Int){val p=d.getPage(i);t.importPage(p).apply{resources=p.resources;mediaBox=p.mediaBox;cropBox=p.cropBox;rotation=p.rotation}}
 fun dimensions(p:PDPage)=if(p.rotation%180==0)p.cropBox.width to p.cropBox.height else p.cropBox.height to p.cropBox.width
 fun matrix(p:PDPage):PdfMatrix{
  val b=p.cropBox
  val x=b.lowerLeftX
  val y=b.lowerLeftY

  return when((p.rotation%360+360)%360){
   90->PdfMatrix(0f,1f,-1f,0f,x+b.width,y)
   180->PdfMatrix(-1f,0f,0f,-1f,x+b.width,y+b.height)
   270->PdfMatrix(0f,-1f,1f,0f,x,y+b.height)
   else->PdfMatrix(1f,0f,0f,1f,x,y)
  }
 }
  suspend fun run(o:Options,out:File,progress:(Float)->Unit)=withContext(Dispatchers.IO){try{
  when(o.kind){
   "blank"->PDDocument().use{d->val n=o.text.toInt();require(n in 1..100);repeat(n){d.addPage(PDPage(PDRectangle.A4))};d.save(out)}
   "textpdf"->textPdf(o.text,out)
   "merge"->PDDocument().use{t->require(o.files.size>=2);o.files.forEachIndexed{i,f->ensureActive();load(f,o.password).use{d->check(d,o.consent);PDFMergerUtility().appendDocument(t,d)};progress((i+1f)/o.files.size)};t.save(out)}
   "images","ocr"->rendered(o,out,progress)
   else->load(o.files.first(),o.password).use{d->val pages=ranges(o.range,d.numberOfPages,o.kind=="reorder")
    if(o.kind in listOf("text","search"))require(d.currentAccessPermission.canExtractContent()){ "Metin çıkarma izni yok." }else if(o.kind!="info")check(d,o.consent)
    when(o.kind){
     "extract","reorder","delete","duplicate","reverse"->{val indices=when(o.kind){"delete"->(0 until d.numberOfPages).filter{it !in pages};"duplicate"->(0 until d.numberOfPages).flatMap{if(it in pages)listOf(it,it)else listOf(it)};"reverse"->(0 until d.numberOfPages).reversed().toList();else->pages};require(indices.isNotEmpty()){ "Tüm sayfalar silinemez." };PDDocument().use{t->indices.forEachIndexed{i,n->ensureActive();copy(d,t,n);progress((i+1f)/indices.size)};t.save(out)}}
     "rotate"->{pages.forEach{d.getPage(it).rotation=(d.getPage(it).rotation+o.angle)%360};d.save(out)}
     "split"->ZipOutputStream(out.outputStream()).use{z->pages.forEachIndexed{i,n->ensureActive();val temp=File.createTempFile("split",".pdf",context.cacheDir);try{PDDocument().use{t->copy(d,t,n);t.save(temp)};z.putNextEntry(ZipEntry("sayfa-${n+1}.pdf"));temp.inputStream().use{it.copyTo(z)};z.closeEntry()}finally{temp.delete()};progress((i+1f)/pages.size)}}
     "protect"->{require(o.newPassword.length>=6){"Parola en az 6 karakter olmalı."};d.protect(StandardProtectionPolicy(o.newPassword,o.newPassword,AccessPermission()).apply{encryptionKeyLength=256;isPreferAES=true});d.save(out)}
     "unlock"->{require(d.currentAccessPermission.isOwnerPermission){"Belge sahibi parolası gerekiyor."};d.isAllSecurityToBeRemoved=true;d.save(out)}
     "metadata"->{d.documentInformation.title=o.text;d.documentInformation.author=o.second;d.save(out)}
     "info"->out.writeText("Sayfa: ${d.numberOfPages}\nBoyut: ${o.files.first().length()} bayt\nBaşlık: ${d.documentInformation.title.orEmpty()}\nYazar: ${d.documentInformation.author.orEmpty()}\nŞifreli: ${d.isEncrypted}\nDijital imza sayısı: ${d.signatureDictionaries.size}")
     "text","search"->{val stripper=PDFTextStripper();out.bufferedWriter().use{w->pages.forEachIndexed{i,n->ensureActive();stripper.startPage=n+1;stripper.endPage=n+1;val t=stripper.getText(d);if(o.kind=="text")w.write(t+"\n")else if(t.contains(o.text,true))w.write("Sayfa ${n+1}\n"+t.lineSequence().filter{it.contains(o.text,true)}.joinToString("\n")+"\n\n");progress((i+1f)/pages.size)}}}
     "watermark","number","header"->{val font=font(d);pages.forEachIndexed{i,n->ensureActive();val p=d.getPage(n);val(w,h)=dimensions(p);val text=safe(if(o.kind=="number")"${o.text} ${n+1} / ${d.numberOfPages}"else o.text,font).replace('\n',' ');val size=if(o.kind=="watermark")32f else 12f;PDPageContentStream(d,p,PDPageContentStream.AppendMode.APPEND,true,true).use{s->s.transform(matrix(p));s.setNonStrokingColor(0.1f,0.4f,0.35f);if(o.kind=="watermark")s.setGraphicsStateParameters(PDExtendedGraphicsState().apply{nonStrokingAlphaConstant=0.25f});s.beginText();s.setFont(font,size);s.newLineAtOffset(max(12f,(w-font.getStringWidth(text)/1000*size)/2),when(o.kind){"header"->h-30;"number"->24f;else->h/2});s.showText(text);s.endText()};progress((i+1f)/pages.size)};d.save(out)}
     "compress"->{val seen=mutableSetOf<com.tom_roush.pdfbox.cos.COSDictionary>();for(i in 0 until d.numberOfPages){ensureActive();val r=d.getPage(i).resources?:continue;if(!seen.add(r.cosObject))continue;for(name in r.xObjectNames.toList()){val image=r.getXObject(name);if(image !is PDImageXObject||image.width.toLong()*image.height>12000000||image.softMask!=null||image.mask!=null||image.isStencil)continue;val b=runCatching{image.image}.getOrNull()?:continue;try{val k=min(1f,(if(o.quality<60)1000f else 1800f)/max(b.width,b.height));val small=if(k<1)Bitmap.createScaledBitmap(b,max(1,(b.width*k).toInt()),max(1,(b.height*k).toInt()),true)else b;try{r.put(name,JPEGFactory.createFromImage(d,small,o.quality/100f))}finally{if(small!==b)small.recycle()}}finally{b.recycle()}};progress((i+1f)/d.numberOfPages)};d.save(out)}
     else->throw IllegalArgumentException("Bilinmeyen araç")
    }
   }
  };ensureActive();progress(1f)
 }catch(e:Throwable){out.delete();throw e}}
 suspend fun textPdf(text:String,out:File){require(text.isNotBlank());PDDocument().use{d->val f=font(d);val lines=mutableListOf<String>();safe(text.replace('\t',' '),f).split('\n').forEach{p->var line="";p.codePoints().forEach{cp->val ch=String(Character.toChars(cp));if(f.getStringWidth(line+ch)/1000*12>515&&line.isNotEmpty()){lines+=line;line=""};line+=ch};lines+=line};lines.chunked(42).forEach{chunk->currentCoroutineContext().ensureActive();val p=PDPage(PDRectangle.A4);d.addPage(p);PDPageContentStream(d,p).use{s->s.beginText();s.setFont(f,12f);s.setLeading(18f);s.newLineAtOffset(40f,800f);chunk.forEach{s.showText(it);s.newLine()};s.endText()}};d.save(out)}}
 suspend fun recognize(b:Bitmap):String{val r=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);try{return r.process(InputImage.fromBitmap(b,0)).await().text}finally{r.close()}}
 suspend fun rendered(o:Options,out:File,progress:(Float)->Unit){load(o.files.first(),o.password).use{require(it.currentAccessPermission.canExtractContent())};val s=renderer.createSession();try{s.open(o.files.first(),o.password.takeIf{it.isNotBlank()});val pages=ranges(o.range,s.pageCount);if(o.kind=="ocr")out.bufferedWriter().use{w->pages.forEachIndexed{i,p->currentCoroutineContext().ensureActive();w.write(recognize(s.render(p,1800))+"\n\n");progress((i+1f)/pages.size)}}else ZipOutputStream(out.outputStream()).use{z->pages.forEachIndexed{i,p->currentCoroutineContext().ensureActive();val png=o.text=="PNG";z.putNextEntry(ZipEntry("sayfa-${p+1}.${if(png)"png"else"jpg"}"));s.render(p,1800).compress(if(png)Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,o.quality,z);z.closeEntry();progress((i+1f)/pages.size)}}}finally{s.close()}}
 fun photo(p: Photo, edge: Int = 1800): Bitmap {

  // Önce görsel boyutlarını oku
  val bounds = BitmapFactory.Options().apply {
   inJustDecodeBounds = true
  }

  BitmapFactory.decodeFile(p.file, bounds)

  require(bounds.outWidth > 0 && bounds.outHeight > 0) {
   "Görsel okunamadı."
  }

  // Büyük görselleri RAM dostu şekilde küçülterek yükle
  var sample = 1
  while (max(bounds.outWidth, bounds.outHeight) / sample > edge) {
   sample *= 2
  }

  var b = BitmapFactory.decodeFile(
   p.file,
   BitmapFactory.Options().apply {
    inSampleSize = sample
   }
  ) ?: throw IllegalArgumentException("Görsel açılamadı.")

  // Burada MUTLAKA Android Matrix kullanılmalı
  val m = AndroidMatrix().apply {

   when (
    ExifInterface(p.file)
     .getAttributeInt(
      ExifInterface.TAG_ORIENTATION,
      ExifInterface.ORIENTATION_NORMAL
     )
   ) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
     setScale(-1f, 1f)
    }

    ExifInterface.ORIENTATION_ROTATE_180 -> {
     setRotate(180f)
    }

    ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
     setScale(1f, -1f)
    }

    ExifInterface.ORIENTATION_TRANSPOSE -> {
     setRotate(90f)
     postScale(-1f, 1f)
    }

    ExifInterface.ORIENTATION_ROTATE_90 -> {
     setRotate(90f)
    }

    ExifInterface.ORIENTATION_TRANSVERSE -> {
     setRotate(-90f)
     postScale(-1f, 1f)
    }

    ExifInterface.ORIENTATION_ROTATE_270 -> {
     setRotate(-90f)
    }
   }

   postRotate(p.rotation.toFloat())
  }

  // EXIF veya kullanıcı döndürmesi varsa uygula
  if (!m.isIdentity) {
   val rotated = Bitmap.createBitmap(
    b,
    0,
    0,
    b.width,
    b.height,
    m,
    true
   )

   if (rotated !== b) {
    b.recycle()
   }

   b = rotated
  }

  // Simetrik kırpma
  val crop = p.crop.coerceIn(0f, 0.35f)

  val x = (b.width * crop).toInt()
  val y = (b.height * crop).toInt()

  val newWidth = b.width - 2 * x
  val newHeight = b.height - 2 * y

  require(newWidth > 0 && newHeight > 0) {
   "Kırpma değeri görsel için geçersiz."
  }

  val output = Bitmap.createBitmap(
   newWidth,
   newHeight,
   Bitmap.Config.ARGB_8888
  )

  val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  // Gri tonlama
  if (p.filter > 0) {
   paint.colorFilter = ColorMatrixColorFilter(
    ColorMatrix().apply {
     setSaturation(0f)
    }
   )
  }

  Canvas(output).apply {
   drawColor(Color.WHITE)
   drawBitmap(
    b,
    -x.toFloat(),
    -y.toFloat(),
    paint
   )
  }

  b.recycle()

  // Siyah-beyaz filtresi
  if (p.filter == 2) {
   val pixels = IntArray(
    output.width * output.height
   )

   output.getPixels(
    pixels,
    0,
    output.width,
    0,
    0,
    output.width,
    output.height
   )

   for (i in pixels.indices) {
    pixels[i] =
     if (Color.red(pixels[i]) > 150) {
      Color.WHITE
     } else {
      Color.BLACK
     }
   }

   output.setPixels(
    pixels,
    0,
    output.width,
    0,
    0,
    output.width,
    output.height
   )
  }

  return output
 }
 suspend fun photos(list:List<Photo>,paper:String,landscape:Boolean,margin:Float,quality:Int,out:File,progress:(Float)->Unit){require(list.isNotEmpty());try{PDDocument(MemoryUsageSetting.setupTempFileOnly()).use{d->list.forEachIndexed{i,p->currentCoroutineContext().ensureActive();val b=photo(p);try{val base=when(paper){"Letter"->PDRectangle.LETTER;"Otomatik"->PDRectangle(b.width*0.5f,b.height*0.5f);else->PDRectangle.A4};val size=if(landscape&&base.width<base.height)PDRectangle(base.height,base.width)else base;val page=PDPage(size);d.addPage(page);val pad=min(margin,min(size.width,size.height)/4);val scale=min((size.width-2*pad)/b.width,(size.height-2*pad)/b.height);PDPageContentStream(d,page).use{s->s.drawImage(JPEGFactory.createFromImage(d,b,quality/100f),(size.width-b.width*scale)/2,(size.height-b.height*scale)/2,b.width*scale,b.height*scale)}}finally{b.recycle()};progress((i+1f)/list.size)};d.save(out)}}catch(e:Throwable){out.delete();throw e}}
 fun fields(file:File,password:String):List<FormField> = load(file,password).use{d->val f=d.documentCatalog.acroForm?:throw IllegalArgumentException("Doldurulabilir form yok.");require(!f.hasXFA()){ "XFA desteklenmiyor." };f.fieldTree.filter{!it.isReadOnly&&(it is PDTextField||it is PDCheckBox)}.map{FormField(it.fullyQualifiedName,if(it is PDCheckBox)it.isChecked.toString()else it.valueAsString,it is PDCheckBox)}.toList()}
 fun fill(file:File,password:String,values:List<FormField>,out:File,consent:Boolean){try{load(file,password).use{d->check(d,consent);require(d.currentAccessPermission.canFillInForm());val f=d.documentCatalog.acroForm;require(f!=null&&!f.hasXFA());val res=f.defaultResources?:PDResources().also{f.defaultResources=it};val font=font(d);val appearance="/${res.add(font).name} 12 Tf 0 g";f.defaultAppearance=appearance;values.forEach{v->when(val field=f.getField(v.name)){is PDTextField->{require(!field.isReadOnly);field.defaultAppearance=appearance;field.value=safe(v.value,font)};is PDCheckBox->{require(!field.isReadOnly);if(v.value=="true")field.check()else field.unCheck()}}};f.needAppearances=false;d.save(out)}}catch(e:Throwable){out.delete();throw e}}
}
